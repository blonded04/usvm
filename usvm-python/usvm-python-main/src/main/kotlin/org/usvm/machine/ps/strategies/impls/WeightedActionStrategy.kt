package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import org.usvm.machine.ps.strategies.PyPathSelectorActionStrategy
import org.usvm.machine.ps.weightedRandom
import kotlin.random.Random


class WeightedActionStrategy(
    private val random: Random,
    private val actions: List<Action>,
    private val weights: List<Double>,
) : PyPathSelectorActionStrategy {
    init {
        require(actions.size == weights.size)
    }
    override fun chooseAction(graph: DelayedForkGraph): PyPathSelectorAction? {
        val availableActions = actions.mapIndexedNotNull { idx, action ->
            if (!action.isAvailable(graph)) {
                return@mapIndexedNotNull null
            }
            action to weights[idx]
        }
        if (availableActions.isEmpty()) {
            return null
        }
        logger.debug("Available actions: {}", availableActions)
        val action = weightedRandom(random, availableActions) { it.second }.first
        logger.debug("Making action {}", action)
        return action.makeAction(graph, random)
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
