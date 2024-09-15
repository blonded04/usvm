package org.usvm.machine.ps.strategies.impls

import org.usvm.language.PyInstruction
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.TypeRating
import kotlin.collections.HashMap

class ConcreteTypeCoverageDelayedForkState : DelayedForkState() {
    var instructionToCoveredByConcrete: HashMap<PyInstruction, Boolean> = hashMapOf()
    var instructionToTypeRating: HashMap<PyInstruction, TypeRatingByConcreteTypeCoverage> = hashMapOf()

    override fun addTypeRating(typeRating: TypeRating) {
        super.addTypeRating(typeRating)
        TODO("Not yet implemented")
    }
}