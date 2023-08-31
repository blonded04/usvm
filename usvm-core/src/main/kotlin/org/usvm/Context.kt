package org.usvm

import io.ksmt.KAst
import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.DefaultValueSampler
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import io.ksmt.utils.uncheckedCast
import org.usvm.collection.array.UAllocatedArray
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArray
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.array.length.UInputArrayLengths
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.field.UInputFields
import org.usvm.collection.map.length.UInputMapLengthCollection
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMap
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMap
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeys
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMap
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeys
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.memory.splitUHeapRef
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem
import org.usvm.util.Region

@Suppress("LeakingThis")
open class UContext(
    components: UComponents<*>,
    operationMode: OperationMode = OperationMode.CONCURRENT,
    astManagementMode: AstManagementMode = AstManagementMode.GC,
    simplificationMode: SimplificationMode = SimplificationMode.SIMPLIFY,
) : KContext(operationMode, astManagementMode, simplificationMode) {

    private val solver by lazy { components.mkSolver(this) }
    private val typeSystem by lazy { components.mkTypeSystem(this) }

    private var currentStateId = 0u

    /**
     * Generates new deterministic id of state in this context.
     */
    fun getNextStateId(): StateId {
        return currentStateId++
    }

    @Suppress("UNCHECKED_CAST")
    fun <Type, Context : UContext> solver(): USolverBase<Type, Context> =
        this.solver as USolverBase<Type, Context>

    @Suppress("UNCHECKED_CAST")
    fun <Type> typeSystem(): UTypeSystem<Type> =
        this.typeSystem as UTypeSystem<Type>

    val addressSort: UAddressSort = mkUninterpretedSort("Address")
    val sizeSort: USizeSort = bv32Sort

    fun mkSizeExpr(size: Int): USizeExpr = mkBv(size)

    val nullRef: UNullRef = UNullRef(this)

    fun mkNullRef(): USymbolicHeapRef {
        return nullRef
    }

    /**
     * Disassembles [lhs] and [rhs], simplifies concrete refs, if it has any, and rewrites it in a DNF, except that the
     * last clause contains a guard and an ite for symbolic addresses. The guard ensures that all concrete refs are
     * bubbled up.
     *
     * For example,
     *
     * ```
     * ite1 =
     * (ite cond1 %0 (ite cond2 %1 0x0))
     *
     * ite2 =
     * (ite cond3 %2 (ite cond4 0x0 0x1))
     *
     * mkHeapRefEq(ite1, ite2) =
     * ((!cond1 && !cond2 && !cond3 && cond4) || ((cond1 || cond2) && cond3 && ite(cond1, %0, %1) == %2))
     * ```
     *
     * @return the new equal rewritten expression without [UConcreteHeapRef]s
     */
    override fun <T : KSort> mkEq(lhs: KExpr<T>, rhs: KExpr<T>, order: Boolean): KExpr<KBoolSort> =
        if (lhs.sort == addressSort) {
            mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort))
        } else {
            super.mkEq(lhs, rhs, order)
        }

    fun mkHeapRefEq(lhs: UHeapRef, rhs: UHeapRef): UBoolExpr =
        when {
            // fast checks
            lhs is USymbolicHeapRef && rhs is USymbolicHeapRef -> super.mkEq(lhs, rhs, order = true)
            lhs is UConcreteHeapRef && rhs is UConcreteHeapRef -> mkBool(lhs == rhs)
            // unfolding
            else -> {
                val (concreteRefsLhs, symbolicRefLhs) = splitUHeapRef(lhs, ignoreNullRefs = false)
                val (concreteRefsRhs, symbolicRefRhs) = splitUHeapRef(rhs, ignoreNullRefs = false)

                val concreteRefLhsToGuard = concreteRefsLhs.associate { it.expr.address to it.guard }

                val conjuncts = mutableListOf<UBoolExpr>(falseExpr)

                concreteRefsRhs.forEach { (concreteRefRhs, guardRhs) ->
                    val guardLhs = concreteRefLhsToGuard.getOrDefault(concreteRefRhs.address, falseExpr)
                    // mkAnd instead of mkAnd with flat=false here is OK
                    val conjunct = mkAnd(guardLhs, guardRhs)
                    conjuncts += conjunct
                }

                if (symbolicRefLhs != null && symbolicRefRhs != null) {
                    val refsEq = super.mkEq(symbolicRefLhs.expr, symbolicRefRhs.expr, order = true)
                    // mkAnd instead of mkAnd with flat=false here is OK
                    val conjunct = mkAnd(symbolicRefLhs.guard, symbolicRefRhs.guard, refsEq)
                    conjuncts += conjunct
                }

                // it's safe to use mkOr here
                mkOr(conjuncts)
            }
        }

    private val uConcreteHeapRefCache = mkAstInterner<UConcreteHeapRef>()
    fun mkConcreteHeapRef(address: UConcreteHeapAddress): UConcreteHeapRef =
        uConcreteHeapRefCache.createIfContextActive {
            UConcreteHeapRef(this, address)
        }

    private val registerReadingCache = mkAstInterner<URegisterReading<out USort>>()
    fun <Sort : USort> mkRegisterReading(idx: Int, sort: Sort): URegisterReading<Sort> =
        registerReadingCache.createIfContextActive { URegisterReading(this, idx, sort) }.cast()

    private val inputFieldReadingCache = mkAstInterner<UInputFieldReading<*, out USort>>()

    fun <Field, Sort : USort> mkInputFieldReading(
        region: UInputFields<Field, Sort>,
        address: UHeapRef,
    ): UInputFieldReading<Field, Sort> = inputFieldReadingCache.createIfContextActive {
        UInputFieldReading(this, region, address)
    }.cast()

    private val allocatedArrayReadingCache = mkAstInterner<UAllocatedArrayReading<*, out USort>>()

    fun <ArrayType, Sort : USort> mkAllocatedArrayReading(
        region: UAllocatedArray<ArrayType, Sort>,
        index: USizeExpr,
    ): UAllocatedArrayReading<ArrayType, Sort> = allocatedArrayReadingCache.createIfContextActive {
        UAllocatedArrayReading(this, region, index)
    }.cast()

    private val inputArrayReadingCache = mkAstInterner<UInputArrayReading<*, out USort>>()

    fun <ArrayType, Sort : USort> mkInputArrayReading(
        region: UInputArray<ArrayType, Sort>,
        address: UHeapRef,
        index: USizeExpr,
    ): UInputArrayReading<ArrayType, Sort> = inputArrayReadingCache.createIfContextActive {
        UInputArrayReading(this, region, address, index)
    }.cast()

    private val inputArrayLengthReadingCache = mkAstInterner<UInputArrayLengthReading<*>>()

    fun <ArrayType> mkInputArrayLengthReading(
        region: UInputArrayLengths<ArrayType>,
        address: UHeapRef,
    ): UInputArrayLengthReading<ArrayType> = inputArrayLengthReadingCache.createIfContextActive {
        UInputArrayLengthReading(this, region, address)
    }.cast()

    private val allocatedSymbolicMapReadingCache = mkAstInterner<UAllocatedMapReading<*, *, *, *>>()

    fun <MapType, KeySort : USort, Sort : USort, Reg : Region<Reg>> mkAllocatedMapReading(
        region: UAllocatedMap<MapType, KeySort, Sort, Reg>,
        key: UExpr<KeySort>
    ): UAllocatedMapReading<MapType, KeySort, Sort, Reg> =
        allocatedSymbolicMapReadingCache.createIfContextActive {
            UAllocatedMapReading(this, region, key)
        }.cast()

    private val inputSymbolicMapReadingCache = mkAstInterner<UInputMapReading<*, *, *, *>>()

    fun <MapType, KeySort : USort, Reg : Region<Reg>, Sort : USort> mkInputMapReading(
        region: UInputMap<MapType, KeySort, Sort, Reg>,
        address: UHeapRef,
        key: UExpr<KeySort>
    ): UInputMapReading<MapType, KeySort, Sort, Reg> =
        inputSymbolicMapReadingCache.createIfContextActive {
            UInputMapReading(this, region, address, key)
        }.cast()

    private val allocatedSymbolicRefMapWithInputKeysReadingCache =
        mkAstInterner<UAllocatedRefMapWithInputKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkAllocatedRefMapWithInputKeysReading(
        region: UAllocatedRefMapWithInputKeys<MapType, Sort>,
        keyRef: UHeapRef
    ): UAllocatedRefMapWithInputKeysReading<MapType, Sort> =
        allocatedSymbolicRefMapWithInputKeysReadingCache.createIfContextActive {
            UAllocatedRefMapWithInputKeysReading(this, region, keyRef)
        }.cast()

    private val inputSymbolicRefMapWithAllocatedKeysReadingCache =
        mkAstInterner<UInputRefMapWithAllocatedKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkInputRefMapWithAllocatedKeysReading(
        region: UInputRefMapWithAllocatedKeys<MapType, Sort>,
        mapRef: UHeapRef
    ): UInputRefMapWithAllocatedKeysReading<MapType, Sort> =
        inputSymbolicRefMapWithAllocatedKeysReadingCache.createIfContextActive {
            UInputRefMapWithAllocatedKeysReading(this, region, mapRef)
        }.cast()

    private val inputSymbolicRefMapWithInputKeysReadingCache =
        mkAstInterner<UInputRefMapWithInputKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkInputRefMapWithInputKeysReading(
        region: UInputRefMap<MapType, Sort>,
        mapRef: UHeapRef,
        keyRef: UHeapRef
    ): UInputRefMapWithInputKeysReading<MapType, Sort> =
        inputSymbolicRefMapWithInputKeysReadingCache.createIfContextActive {
            UInputRefMapWithInputKeysReading(this, region, mapRef, keyRef)
        }.cast()

    private val inputSymbolicMapLengthReadingCache = mkAstInterner<UInputMapLengthReading<*>>()

    fun <MapType> mkInputMapLengthReading(
        region: UInputMapLengthCollection<MapType>,
        address: UHeapRef
    ): UInputMapLengthReading<MapType> =
        inputSymbolicMapLengthReadingCache.createIfContextActive {
            UInputMapLengthReading<MapType>(this, region, address)
        }.cast()

    private val indexedMethodReturnValueCache = mkAstInterner<UIndexedMethodReturnValue<Any, out USort>>()

    fun <Method, Sort : USort> mkIndexedMethodReturnValue(
        method: Method,
        callIndex: Int,
        sort: Sort,
    ): UIndexedMethodReturnValue<Method, Sort> = indexedMethodReturnValueCache.createIfContextActive {
        UIndexedMethodReturnValue(this, method.cast(), callIndex, sort)
    }.cast()

    private val isSubtypeExprCache = mkAstInterner<UIsSubtypeExpr<Any>>()
    fun <Type> mkIsSubtypeExpr(
        ref: UHeapRef, type: Type,
    ): UIsSubtypeExpr<Type> = isSubtypeExprCache.createIfContextActive {
        UIsSubtypeExpr(this, ref, type.cast())
    }.cast()

    private val isSupertypeExprCache = mkAstInterner<UIsSupertypeExpr<Any>>()
    fun <Type> mkIsSupertypeExpr(
        ref: UHeapRef, type: Type,
    ): UIsSupertypeExpr<Type> = isSupertypeExprCache.createIfContextActive {
        UIsSupertypeExpr(this, ref, type.cast())
    }.cast()

    fun mkConcreteHeapRefDecl(address: UConcreteHeapAddress): UConcreteHeapRefDecl =
        UConcreteHeapRefDecl(this, address)

    override fun boolSortDefaultValue(): KExpr<KBoolSort> = falseExpr

    override fun <S : KBvSort> bvSortDefaultValue(sort: S): KExpr<S> = mkBv(0, sort)

    // Type hack to be able to intern the initial location for inheritors.
    private val initialLocation = RootNode<Nothing, Nothing>()

    fun <State : UState<*, *, Statement, *, State>, Statement> mkInitialLocation()
            : PathsTrieNode<State, Statement> = initialLocation.uncheckedCast()

    fun mkUValueSampler(): KSortVisitor<KExpr<*>> {
        return UValueSampler(this)
    }

    val uValueSampler: KSortVisitor<KExpr<*>> by lazy { mkUValueSampler() }

    class UValueSampler(val uctx: UContext) : DefaultValueSampler(uctx) {
        override fun visit(sort: KUninterpretedSort): KExpr<*> =
            if (sort == uctx.addressSort) {
                uctx.nullRef
            } else {
                super.visit(sort)
            }
    }

    inline fun <T : KSort> mkIte(
        condition: KExpr<KBoolSort>,
        trueBranch: () -> KExpr<T>,
        falseBranch: () -> KExpr<T>
    ): KExpr<T> =
        when (condition) {
            is UTrue -> trueBranch()
            is UFalse -> falseBranch()
            else -> mkIte(condition, trueBranch(), falseBranch())
        }
}


fun <T : KSort> T.sampleUValue(): KExpr<T> =
    accept(uctx.uValueSampler).asExpr(this)

val KAst.uctx
    get() = ctx as UContext
