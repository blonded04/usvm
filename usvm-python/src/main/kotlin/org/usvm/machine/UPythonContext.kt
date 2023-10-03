package org.usvm.machine

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import org.usvm.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.solver.USolverBase

class UPythonContext(
    typeSystem: PythonTypeSystem,
    private val components: PythonComponents = PythonComponents(typeSystem)
): UContext<KIntSort>(components) {
    private var nextAddress: UConcreteHeapAddress = INITIAL_STATIC_ADDRESS / 2
    fun provideRawConcreteHeapRef(): UConcreteHeapRef {
        require(nextAddress > INITIAL_STATIC_ADDRESS) {
            "Should not return a static ref"
        }

        return mkConcreteHeapRef(nextAddress--)
    }

    val floatRoundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven)

    fun intToFloat(intValue: UExpr<KIntSort>): UExpr<KRealSort> {
        return mkIntToReal(intValue)
        //return mkRealToFpExpr(fp64Sort, floatRoundingMode, realValue)
    }


    private var solver: USolverBase<PythonType> = components.mkSolver(this)

    @Suppress("UNCHECKED_CAST")
    override fun <Type> solver(): USolverBase<Type> =
        solver as USolverBase<Type>

    fun restartSolver() {
        solver.close()
        solver = components.mkSolver(this)
    }

    fun closeSolver() {
        solver.close()
    }
}