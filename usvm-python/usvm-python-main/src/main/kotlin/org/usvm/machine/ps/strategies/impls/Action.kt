package org.usvm.machine.ps.strategies.impls

import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import kotlin.random.Random


interface Action {
    fun isAvailable(graph: DelayedForkGraph): Boolean
    fun makeAction(graph: DelayedForkGraph, random: Random): PyPathSelectorAction
}
