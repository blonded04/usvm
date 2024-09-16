package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import org.usvm.machine.ps.strategies.PyPathSelectorActionStrategy
import kotlin.random.Random

class RandomizedPriorityActionStrategy(
    private val random: Random,
    private val actions: List<Action>,
    private val probabilities: List<Double>,
) : PyPathSelectorActionStrategy {
    init {
        require(actions.size == probabilities.size)
    }
    override fun chooseAction(graph: DelayedForkGraph): PyPathSelectorAction? {
        val availableActions: List<Pair<Action, Double>> = actions.mapIndexedNotNull { idx, action ->
            if (!action.isAvailable(graph)) {
                return@mapIndexedNotNull null
            }
            action to probabilities[idx]
        }
        if (availableActions.isEmpty()) {
            return null
        }
        logger.debug("Available actions: {}", availableActions)
        val action = makeChoice(availableActions)
        logger.debug("Making action {}", action)
        return action.makeAction(graph, random)
    }

    private fun makeChoice(availableActions: List<Pair<Action, Double>>): Action {
        availableActions.dropLast(1).forEach {
            val coin = random.nextDouble()
            if (coin < it.second) {
                return it.first
            }
        }
        return availableActions.last().first
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
