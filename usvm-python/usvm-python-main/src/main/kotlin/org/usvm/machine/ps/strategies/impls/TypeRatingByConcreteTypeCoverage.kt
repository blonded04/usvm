package org.usvm.machine.ps.strategies.impls

import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.DelayedForkStrategy
import org.usvm.machine.ps.strategies.TypeRating

class TypeRatingByConcreteTypeCoverage : DelayedForkStrategy {
    override fun chooseTypeRating(state: DelayedForkState): TypeRating {
        var concreteTypeCoverageState = state as ConcreteTypeCoverageDelayedForkState
        TODO("Not yet implemented")
    }
}
