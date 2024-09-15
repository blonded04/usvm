package org.usvm.machine.ps.strategies.impls

import org.usvm.UPathSelector
import org.usvm.language.PyInstruction
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

fun makeDelayedForkByInstructionPriorityStrategy(
    random: Random,
): RandomizedPriorityActionStrategy =
    RandomizedPriorityActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedForkByInstruction,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedForkByInstruction
        ),
        baselineProbabilities
    )

fun makeDelayedForkByInstructionWeightedStrategy(
    random: Random,
): WeightedActionStrategy =
    WeightedActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedForkByInstruction,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedForkByInstruction
        ),
        baselineWeights
    )

sealed class DelayedForkByInstructionAction : Action {
    protected fun findAvailableInstructions(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex) -> Boolean,
    ): List<PyInstruction> =
        graph.nodesByInstruction.entries.filter { (_, nodes) ->
            nodes.any { it in graph.aliveNodesAtDistanceOne && isAvailable(it) }
        }.map {
            it.key
        }

    protected fun chooseDelayedFork(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex) -> Boolean,
        random: Random,
    ): DelayedForkGraphInnerVertex {
        val availableInstructions = findAvailableInstructions(graph, isAvailable)
        val size = availableInstructions.size
        require(size > 0)
        val idx = random.nextInt(0, size)
        val rawNodes = graph.nodesByInstruction[availableInstructions[idx]]
            ?: error("${availableInstructions[idx]} not in map graph.nodesByInstruction")
        val nodes = rawNodes.filter {
            it in graph.aliveNodesAtDistanceOne && isAvailable(it)
        }
        require(nodes.isNotEmpty())
        return nodes.random(random)
    }
}

data object ServeNewDelayedForkByInstruction : DelayedForkByInstructionAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        findAvailableInstructions((graph as DelayedForkByInstructionGraph), predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkGraph,
        random: Random,
    ): PyPathSelectorAction =
        MakeDelayedFork(chooseDelayedFork((graph as DelayedForkByInstructionGraph), predicate, random))
}

data object ServeOldDelayedForkByInstruction : DelayedForkByInstructionAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkGraph): Boolean =
        findAvailableInstructions((graph as DelayedForkByInstructionGraph), predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkGraph,
        random: Random,
    ): PyPathSelectorAction =
        MakeDelayedFork(chooseDelayedFork((graph as DelayedForkByInstructionGraph), predicate, random))
}

class DelayedForkByInstructionGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex,
) : BaselineDelayedForkGraph(basePathSelectorCreation, root) {
    internal val nodesByInstruction =
        mutableMapOf<PyInstruction, MutableSet<DelayedForkGraphInnerVertex>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex) {
        super.addVertex(df, vertex)
        val set = nodesByInstruction[vertex.delayedFork.state.pathNode.statement]
            ?: mutableSetOf<DelayedForkGraphInnerVertex>().also {
                nodesByInstruction[vertex.delayedFork.state.pathNode.statement] = it
            }
        set.add(vertex)
    }
}

class DelayedForkByInstructionGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>,
) : DelayedForkGraphCreation {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(
        root: DelayedForkGraphRootVertex,
    ): DelayedForkByInstructionGraph =
        DelayedForkByInstructionGraph(basePathSelectorCreation, root)
}
