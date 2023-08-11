package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest
import org.usvm.util.isException

internal class ListWrapperReturnsVoidTest : JavaMethodTestRunner() {
    @Test
    fun testRunForEach() = disableTest("Unexpected expr of type void: JcLambdaExpr") {
        checkDiscoveredPropertiesWithExceptions(
            ListWrapperReturnsVoidExample::runForEach,
            eq(4),
            { _, l, r -> l == null && r.isException<NullPointerException>() },
            { _, l, r -> l.isEmpty() && r.getOrThrow() == 0 },
            { _, l, r -> l.isNotEmpty() && l.all { it != null } && r.getOrThrow() == 0 },
            { _, l, r -> l.isNotEmpty() && l.any { it == null } && r.getOrThrow() > 0 },
        )
    }

    @Test
    fun testSumPositiveForEach() = disableTest("No result found | lambda") {
        checkDiscoveredPropertiesWithExceptions(
            ListWrapperReturnsVoidExample::sumPositiveForEach,
            eq(5),
            { _, l, r -> l == null && r.isException<NullPointerException>() },
            { _, l, r -> l.isEmpty() && r.getOrThrow() == 0 },
            { _, l, r -> l.isNotEmpty() && l.any { it == null } && r.isException<NullPointerException>() },
            { _, l, r -> l.isNotEmpty() && l.any { it <= 0 } && r.getOrThrow() == l.filter { it > 0 }.sum() },
            { _, l, r -> l.isNotEmpty() && l.any { it > 0 } && r.getOrThrow() == l.filter { it > 0 }.sum() }
        )
    }
}