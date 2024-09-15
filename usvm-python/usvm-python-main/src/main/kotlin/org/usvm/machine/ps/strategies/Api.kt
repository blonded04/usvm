package org.usvm.machine.ps.strategies

import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.types.ConcretePythonType

interface PyPathSelectorActionStrategy {
    fun chooseAction(graph: DelayedForkGraph): PyPathSelectorAction?
}

interface DelayedForkStrategy {
    fun chooseTypeRating(state: DelayedForkState): TypeRating
}

interface DelayedForkGraphCreation {
    fun createEmptyDelayedForkState(): DelayedForkState
    fun createOneVertexGraph(root: DelayedForkGraphRootVertex): DelayedForkGraph
}

open class DelayedForkState {
    val usedTypes: MutableSet<ConcretePythonType> = mutableSetOf()
    val successfulTypes: MutableSet<ConcretePythonType> = mutableSetOf()
    var isDead: Boolean = false
    private val typeRatings = mutableListOf<TypeRating>()
    open fun addTypeRating(typeRating: TypeRating) {
        typeRatings.add(typeRating)
    }
    val size: Int
        get() = typeRatings.size
    fun getAt(idx: Int): TypeRating {
        require(idx < size)
        return typeRatings[idx]
    }
}

abstract class DelayedForkGraph(
    val root: DelayedForkGraphRootVertex,
) {
    private val vertices: MutableMap<DelayedFork, DelayedForkGraphInnerVertex> = mutableMapOf()
    open fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex) {
        require(vertices[df] == null) {
            "Cannot add delayed fork twice"
        }
        vertices[df] = vertex
    }
    abstract fun addExecutedStateWithConcreteTypes(state: PyState)
    abstract fun addStateToVertex(vertex: DelayedForkGraphVertex, state: PyState)
    open fun updateVertex(vertex: DelayedForkGraphInnerVertex) = run {}
    fun getVertexByDelayedFork(df: DelayedFork): DelayedForkGraphInnerVertex? =
        vertices[df]
}

sealed class DelayedForkGraphVertex

class DelayedForkGraphRootVertex : DelayedForkGraphVertex()

class DelayedForkGraphInnerVertex(
    val delayedForkState: DelayedForkState,
    val delayedFork: DelayedFork,
    val parent: DelayedForkGraphVertex,
) : DelayedForkGraphVertex()

sealed class PyPathSelectorAction
class Peek(
    val pathSelector: UPathSelector<PyState>,
) : PyPathSelectorAction()
class MakeDelayedFork(
    val vertex: DelayedForkGraphInnerVertex,
) : PyPathSelectorAction()

class TypeRating(
    val types: MutableList<ConcretePythonType>,
    val numberOfHints: Int,
    var numberOfUsed: Int = 0,
)
