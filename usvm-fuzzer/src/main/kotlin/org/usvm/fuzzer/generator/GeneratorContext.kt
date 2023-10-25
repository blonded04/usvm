package org.usvm.fuzzer.generator

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.usvm.fuzzer.util.UTestValueRepresentation
import kotlin.random.Random

data class GeneratorContext(
    val constants: Map<JcType, List<UTestValueRepresentation>>,
    val repository: GeneratorRepository,
    val random: Random,
    val jcClasspath: JcClasspath,
) {

    val classes =
        jcClasspath.locations
            .flatMap { it.classNames ?: setOf() }
            .mapNotNull { jcClasspath.findClassOrNull(it) }

}