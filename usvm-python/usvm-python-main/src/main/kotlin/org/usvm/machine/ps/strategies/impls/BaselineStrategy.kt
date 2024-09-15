package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

// For now, these values were chosen in an arbitrary way.
// TODO: find best possible values
const val PROB_0 = 1.0
const val PROB_1 = 0.6
const val PROB_2 = 0.875
const val PROB_3 = 0.8
const val INF = 100.0

val baselineProbabilities = listOf(PROB_0, PROB_1, PROB_2, PROB_3, 1.0)
private val probNegations = baselineProbabilities
    .drop(1)
    .runningFold(1.0) { acc, p -> acc * (1 - p) }
val baselineWeights = // listOf(100.0, 0.6, 0.35, 0.04, 0.01)
    listOf(INF) + (baselineProbabilities.drop(1) zip probNegations.dropLast(1)).map { (a, b) -> a * b }

fun makeBaselinePriorityActionStrategy(
    random: Random,
): RandomizedPriorityActionStrategy =
    RandomizedPriorityActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork
        ),
        baselineProbabilities
    )

fun makeBaselineWeightedActionStrategy(
    random: Random,
): WeightedActionStrategy =
    WeightedActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork
        ),
        baselineWeights
    )

sealed class BaselineAction : Action {
    protected fun chooseAvailableVertex(
        available: List<DelayedForkGraphInnerVertex>,
        random: Random,
    ): DelayedForkGraphInnerVertex {
        require(available.isNotEmpty())
        val idx = random.nextInt(0, available.size)
        return available[idx]
    }
}

object PeekExecutedStateWithConcreteType : BaselineAction() {
    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        !(graph as BaselineDelayedForkGraph).pathSelectorForExecutedStatesWithConcreteTypes.isEmpty()

    override fun makeAction(
        graph: DelayedForkGraph,
        random: Random,
    ): PyPathSelectorAction =
        Peek((graph as BaselineDelayedForkGraph).pathSelectorForExecutedStatesWithConcreteTypes)
}

object PeekFromRoot : BaselineAction() {
    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        !(graph as BaselineDelayedForkGraph).pathSelectorWithoutDelayedForks.isEmpty()

    override fun makeAction(graph: DelayedForkGraph, random: Random): PyPathSelectorAction =
        Peek((graph as BaselineDelayedForkGraph).pathSelectorWithoutDelayedForks)

    override fun toString(): String = "PeekFromRoot"
}

object ServeNewDelayedFork : BaselineAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        (graph as BaselineDelayedForkGraph).aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: DelayedForkGraph, random: Random): PyPathSelectorAction {
        val available = (graph as BaselineDelayedForkGraph).aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeNewDelayedFork"
}

object PeekFromStateWithDelayedFork : BaselineAction() {
    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        !(graph as BaselineDelayedForkGraph).pathSelectorWithDelayedForks.isEmpty()

    override fun makeAction(graph: DelayedForkGraph, random: Random): PyPathSelectorAction {
        return Peek((graph as BaselineDelayedForkGraph).pathSelectorWithDelayedForks)
    }

    override fun toString(): String = "PeekFromStateWithDelayedFork"
}

object ServeOldDelayedFork : BaselineAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        (graph as BaselineDelayedForkGraph).aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: DelayedForkGraph, random: Random): PyPathSelectorAction {
        val available = (graph as BaselineDelayedForkGraph).aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeOldDelayedFork"
}

class BaselineDelayedForkStrategy : DelayedForkStrategy {
    private var lastIdx = -1
    override fun chooseTypeRating(state: DelayedForkState): TypeRating {
        require(state.size > 0) {
            "Cannot choose type rating from empty set"
        }
        lastIdx = (lastIdx + 1) % state.size
        val idx = lastIdx
        return state.getAt(idx)
    }
}

class BaselineDFGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>,
) : DelayedForkGraphCreation {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex): BaselineDelayedForkGraph =
        BaselineDelayedForkGraph(basePathSelectorCreation, root)
}

open class BaselineDelayedForkGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex,
) : DelayedForkGraph(root) {

    internal val pathSelectorWithoutDelayedForks = basePathSelectorCreation()
    internal val pathSelectorWithDelayedForks = basePathSelectorCreation()
    internal val pathSelectorForExecutedStatesWithConcreteTypes = basePathSelectorCreation()
    internal val aliveNodesAtDistanceOne = mutableSetOf<DelayedForkGraphInnerVertex>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex) {
        super.addVertex(df, vertex)
        if (vertex.parent == root) {
            logger.debug("Adding node to aliveNodesAtDistanceOne")
            aliveNodesAtDistanceOne.add(vertex)
        }
    }

    override fun updateVertex(vertex: DelayedForkGraphInnerVertex) {
        if (vertex.delayedForkState.isDead) {
            aliveNodesAtDistanceOne.remove(vertex)
        }
    }

    override fun addExecutedStateWithConcreteTypes(state: PyState) {
        pathSelectorForExecutedStatesWithConcreteTypes.add(listOf(state))
    }

    override fun addStateToVertex(vertex: DelayedForkGraphVertex, state: PyState) {
        when (vertex) {
            is DelayedForkGraphRootVertex -> {
                pathSelectorWithoutDelayedForks.add(listOf(state))
            }

            is DelayedForkGraphInnerVertex -> {
                pathSelectorWithDelayedForks.add(listOf(state))
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
