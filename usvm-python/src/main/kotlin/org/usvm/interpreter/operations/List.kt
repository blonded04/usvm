package org.usvm.interpreter.operations

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructInt
import org.usvm.language.types.PythonType
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList
import org.usvm.language.types.pythonTuple
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateListKt(context: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val addresses = elements.map { it!!.address }.asSequence()
    with (context.ctx) {
        val listAddress = context.curState!!.memory.malloc(pythonList, addressSort, addresses)
        val result = UninterpretedSymbolicPythonObject(listAddress)
        myAssert(context, context.curState!!.pathConstraints.typeConstraints.evalIsSubtype(listAddress, pythonList))
        return result
    }
}

fun handlerListGetSizeKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    @Suppress("unchecked_cast")
    val listSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, pythonList)) as UExpr<KIntSort>
    return constructInt(context, listSize)
}

private fun resolveIndex(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UArrayIndexLValue<PythonType>? {
    if (context.curState == null)
        return null
    with (context.ctx) {
        index.addSupertype(context, pythonInt)
        list.addSupertype(context, pythonList)

        @Suppress("unchecked_cast")
        val listSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, pythonList)) as UExpr<KIntSort>
        val indexValue = index.getIntContent(context)

        val indexCond = mkAnd(indexValue lt listSize, mkArithUnaryMinus(listSize) le indexValue)
        myFork(context, indexCond)

        if (context.curState!!.pyModel.eval(indexCond).isFalse)
            return null

        val positiveIndex = mkAnd(indexValue lt listSize, mkIntNum(0) le indexValue)
        myFork(context, positiveIndex)

        return if (context.curState!!.pyModel.eval(positiveIndex).isTrue) {
            UArrayIndexLValue(addressSort, list.address, indexValue, pythonList)
        } else {
            val negativeIndex = mkAnd(indexValue lt mkIntNum(0), mkArithUnaryMinus(listSize) le indexValue)
            require(context.curState!!.pyModel.eval(negativeIndex).isTrue)
            UArrayIndexLValue(addressSort, list.address, mkArithAdd(indexValue, listSize), pythonList)
        }
    }
}

fun handlerListGetItemKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject?, index: UninterpretedSymbolicPythonObject?): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    list ?: return null
    index ?: return null
    val lvalue = resolveIndex(context, list, index) ?: return null

    @Suppress("unchecked_cast")
    val elemAddr = context.curState!!.memory.read(lvalue) as UHeapRef
    return UninterpretedSymbolicPythonObject(elemAddr)
}


fun handlerListSetItemKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject, value: UninterpretedSymbolicPythonObject) {
    if (context.curState == null)
        return
    val lvalue = resolveIndex(context, list, index) ?: return
    context.curState!!.memory.write(lvalue, value.address)
}


fun handlerListExtendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject?, tuple: UninterpretedSymbolicPythonObject?): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    list ?: return null
    tuple ?: return null
    with (context.ctx) {
        list.addSupertype(context, pythonList)
        tuple.addSupertype(context, pythonTuple)
        @Suppress("unchecked_cast")
        val currentSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, pythonList)) as UExpr<KIntSort>
        @Suppress("unchecked_cast")
        val tupleSize = context.curState!!.memory.read(UArrayLengthLValue(tuple.address, pythonTuple)) as UExpr<KIntSort>
        // TODO: type: list or tuple?
        context.curState!!.memory.memcpy(tuple.address, list.address, pythonList, addressSort, mkIntNum(0), currentSize, tupleSize)
        val newSize = mkArithAdd(currentSize, tupleSize)
        context.curState!!.memory.write(UArrayLengthLValue(list.address, pythonList), newSize)
        return list
    }
}

fun handlerListAppendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject?, elem: UninterpretedSymbolicPythonObject?): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    list ?: return null
    elem ?: return null
    with (context.ctx) {
        list.addSupertype(context, pythonList)
        @Suppress("unchecked_cast")
        val currentSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, pythonList)) as UExpr<KIntSort>
        context.curState!!.memory.write(UArrayIndexLValue(addressSort, list.address, currentSize, pythonList), elem.address)
        context.curState!!.memory.write(UArrayLengthLValue(list.address, pythonList), mkArithAdd(currentSize, mkIntNum(1)))
        return list
    }
}