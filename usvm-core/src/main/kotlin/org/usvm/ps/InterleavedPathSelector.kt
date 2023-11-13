package org.usvm.ps

import org.usvm.UPathSelector

/**
 * An interleaved path selector.
 *
 * [update], [remove] and [add] operations are executed on each path selector. A pointer to the next path selector
 * advances when [add] is called.
 */
class InterleavedPathSelector<State>(
    private val selectors: List<UPathSelector<State>>,
) : UPathSelector<State> {
    init {
        require(selectors.size >= 2) { "Cannot create interleaved path selector from less than 2 selectors" }
    }

    constructor(vararg selectors: UPathSelector<State>) : this(selectors.toList())

    private var ptr = 0
    override fun isEmpty() = selectors[ptr].isEmpty() && selectors.all { it.isEmpty() }

    override fun peek(): State {
        val begin = ptr
        while (selectors[ptr].isEmpty()) {
            ptr = (ptr + 1) % selectors.size
            if (ptr == begin) {
                error("Empty queue")
            }
        }
        return selectors[ptr].peek()
    }

    override fun update(state: State): Boolean {
        selectors.forEach { it.update(state) }
        return true // todo
    }

    override fun add(state: State): Boolean {
        selectors.forEach { it.add(state) }
        ptr = (ptr + 1) % selectors.size
        return true // todo
    }

    override fun remove(state: State) {
        selectors.forEach { it.remove(state) }
    }
}
