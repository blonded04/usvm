package org.usvm.merging

import mu.KotlinLogging
import org.usvm.UPathSelector
import org.usvm.UState

val logger = KotlinLogging.logger { }

/**
 * Wraps an [underlyingPathSelector] path selector into merging one. The merging done on [peek] function and works
 * as follows:
 * - first, it takes the state from the [underlyingPathSelector]
 * - then it searches for close states to the peeked one via [closeStatesSearcher]
 * - if no states were found, returns the original one
 * - if there are some states, peeks the first one, tries to merge it with the original one and returns the result
 * - if merging fails, returns the closest state to the original one
 * - when there are no successful merges in [advanceLimit] peeks, returns the state from the [underlyingPathSelector]
 */
class MergingPathSelector<State : UState<*, *, *, *, *, State>>(
    private val underlyingPathSelector: UPathSelector<State>,
    private val closeStatesSearcher: CloseStatesSearcher<State>,
    private val advanceLimit: Int = 15,
) : UPathSelector<State> {
    override fun isEmpty(): Boolean = underlyingPathSelector.isEmpty()

    private sealed interface SelectorState {
        class Advancing(var steps: Int) : SelectorState
        object Peeking : SelectorState
    }

    private var selectorState: SelectorState = SelectorState.Advancing(0)

    override fun peek(): State {
        val state = underlyingPathSelector.peek()

        val resultState = when (val selectorState = selectorState) {
            is SelectorState.Advancing -> {
                val closeState = closeStatesSearcher.findCloseStates(state).firstOrNull() ?: return state
                val mergedState = state.mergeWith(closeState, Unit)
                if (mergedState == null) {
                    selectorState.steps++
                    if (selectorState.steps == advanceLimit) {
                        this.selectorState = SelectorState.Peeking
                    }
                    logger.debug { "Advance state: $closeState to $state" }
                    return closeState
                }
                selectorState.steps = 0

                logger.info { "Merged states: ${state.id} + ${closeState.id} == ${mergedState.id}" }

                remove(state)
                remove(closeState)
                check(add(mergedState)) { "Unexpected" }

                mergedState
            }

            is SelectorState.Peeking -> {
                this.selectorState = SelectorState.Advancing(0)
                state
            }
        }
        return resultState
    }

    override fun update(state: State): Boolean {
        if (!underlyingPathSelector.update(state)) {
            closeStatesSearcher.remove(state)
            return false
        }

        closeStatesSearcher.update(state)
        return true
    }

    override fun add(state: State): Boolean {
        if (!underlyingPathSelector.add(state)) {
            return false
        }

        closeStatesSearcher.add(listOf(state))
        return true
    }

    override fun remove(state: State) {
        underlyingPathSelector.remove(state)
        closeStatesSearcher.remove(state)
    }
}