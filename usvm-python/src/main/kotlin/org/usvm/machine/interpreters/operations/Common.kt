package org.usvm.machine.interpreters.operations

import org.usvm.api.allocateArrayInitialized
import org.usvm.api.writeArrayLength
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.language.types.ArrayType
import org.usvm.language.types.ConcretePythonType
import org.usvm.machine.interpreters.PythonObject
import org.usvm.language.types.ConcreteTypeNegation
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.MethodDescription

fun handlerIsinstanceKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    val type = typeSystem.concreteTypeOnAddress(typeRef) ?: return null
    if (type == typeSystem.pythonObjectType)
        return constructBool(ctx, ctx.ctx.trueExpr)

    val interpreted = interpretSymbolicPythonObject(obj, ctx.modelHolder)
    val concreteType = interpreted.getConcreteType(ctx)
    return if (concreteType == null) {
        if (type == typeSystem.pythonInt) {  //  this is a common case, TODO: better solution
            val cond =
                obj.evalIs(ctx, ConcreteTypeNegation(typeSystem.pythonInt)) and obj.evalIs(ctx, ConcreteTypeNegation(typeSystem.pythonBool))
            myFork(ctx, cond)
        } else {
            myFork(ctx, obj.evalIs(ctx, type))
        }
        require(interpreted.getConcreteType(ctx) == null)
        constructBool(ctx, falseExpr)
    } else {
        if (type == typeSystem.pythonInt) {  //  this is a common case
            myAssert(ctx, obj.evalIs(ctx, typeSystem.pythonBool).not())  // to avoid underapproximation
            constructBool(ctx, obj.evalIs(ctx, typeSystem.pythonInt))
        } else {
            constructBool(ctx, obj.evalIs(ctx, type))
        }
    }
}

fun fixateTypeKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject) {
    ctx.curState ?: return
    val interpreted = interpretSymbolicPythonObject(obj, ctx.modelHolder)
    val type = interpreted.getConcreteType(ctx) ?: return
    obj.addSupertype(ctx, type)
}

fun handlerAndKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    left.addSupertype(ctx, typeSystem.pythonBool)
    right.addSupertype(ctx, typeSystem.pythonBool)
    val leftValue = left.getBoolContent(ctx)
    val rightValue = right.getBoolContent(ctx)
    return constructBool(ctx, mkAnd(leftValue, rightValue))
}

fun lostSymbolicValueKt(ctx: ConcolicRunContext, description: String) {
    ctx.statistics.addLostSymbolicValue(MethodDescription(description))
}

fun createIterable(
    ctx: ConcolicRunContext,
    elements: List<UninterpretedSymbolicPythonObject>,
    type: ConcretePythonType
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val addresses = elements.map { it.address }.asSequence()
    val typeSystem = ctx.typeSystem
    val size = elements.size
    with (ctx.ctx) {
        val iterableAddress = ctx.curState!!.memory.allocateArrayInitialized(ArrayType, addressSort, addresses)
        ctx.curState!!.memory.writeArrayLength(iterableAddress, mkIntNum(size), ArrayType)
        ctx.curState!!.memory.types.allocate(iterableAddress.address, type)
        val result = UninterpretedSymbolicPythonObject(iterableAddress, typeSystem)
        result.addSupertypeSoft(ctx, type)
        return result
    }
}

fun handlerIsOpKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    val leftType = left.getTypeIfDefined(ctx)
    val rightType = right.getTypeIfDefined(ctx)
    if (leftType == null || rightType == null) {
        myFork(ctx, mkHeapRefEq(left.address, right.address))
    }
    if (leftType != rightType)
        return
    when (leftType) {
        ctx.typeSystem.pythonBool ->
            myFork(ctx, left.getBoolContent(ctx) xor right.getBoolContent(ctx))
        ctx.typeSystem.pythonInt ->
            myFork(ctx, left.getIntContent(ctx) eq right.getIntContent(ctx))
        else ->
            myFork(ctx, mkHeapRefEq(left.address, right.address))
    }
}

fun handlerNoneCheckKt(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    myFork(ctx, on.evalIs(ctx, ctx.typeSystem.pythonNoneType))
}

fun handlerStandardTpGetattroKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject
): SymbolForCPython? {
    if (ctx.curState == null)
        return null
    val concreteStr = ctx.curState!!.preAllocatedObjects.concreteString(name) ?: return null
    val type = obj.getTypeIfDefined(ctx) as? ConcretePythonType ?: return null
    val concreteDescriptor = ConcretePythonInterpreter.typeLookup(type.asObject, concreteStr) ?: return null
    val memberDescriptor = ConcretePythonInterpreter.getSymbolicDescriptor(concreteDescriptor) ?: return null
    return memberDescriptor.getMember(ctx, obj)
}

fun symbolicTpCallKt(
    on: SymbolForCPython,
    args: Long,
    kwargs: Long
): Long {
    return ConcretePythonInterpreter.callSymbolicMethod(on, args, kwargs).address
}