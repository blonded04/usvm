package org.usvm.fuzzer.generator

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.fuzzer.generator.arrays.ArrayGenerator
import org.usvm.fuzzer.generator.collections.list.ArrayListGenerator
import org.usvm.fuzzer.generator.collections.list.HashSetGenerator
import org.usvm.fuzzer.generator.collections.list.LinkedHashSetGenerator
import org.usvm.fuzzer.generator.collections.list.LinkedListGenerator
import org.usvm.fuzzer.generator.collections.map.ConcurrentHashMapGenerator
import org.usvm.fuzzer.generator.collections.map.HashMapGenerator
import org.usvm.fuzzer.generator.collections.map.LinkedHashMapGenerator
import org.usvm.fuzzer.generator.collections.map.TreeMapGenerator
import org.usvm.fuzzer.generator.`object`.*
import org.usvm.fuzzer.generator.other.StringGenerator
import org.usvm.fuzzer.generator.primitives.*
import org.usvm.fuzzer.util.*
import org.usvm.instrumentation.util.stringType
import sun.reflect.generics.tree.Tree

class GeneratorRepository {

    private val generators = HashMap<JcType, List<Generator>>()
    lateinit var context: GeneratorContext
    lateinit var jcClasspath: JcClasspath

    fun registerGeneratorContext(generatorContext: GeneratorContext) {
        context = generatorContext
        jcClasspath = generatorContext.jcClasspath
    }

    private fun Generator.appendContext(): Generator = this.also { it.ctx = context }

    fun getGeneratorForType(jcType: JcType): Generator = with(jcType.unboxIfNeeded()) {
        when (this) {
            is JcArrayType -> ArrayGenerator(elementType)
            is JcPrimitiveType -> getPrimitiveGeneratorForType(this, jcClasspath)
            is JcClassType -> getBuiltInOrUserGenerator(this, jcClasspath)
            else -> TODO()
        }.appendContext()
    }

    fun getMockGeneratorForType(jcType: JcClassType) = MockClassGenerator(jcType).appendContext()

    private fun getBuiltInOrUserGenerator(jcType: JcClassType, jcClasspath: JcClasspath) =
        when (jcType.typeNameWOGenerics) {
            jcClasspath.stringType().typeNameWOGenerics -> StringGenerator()
            jcClasspath.arrayListType().typeNameWOGenerics -> ArrayListGenerator(jcType)
            jcClasspath.listType().typeNameWOGenerics -> getRandomFrom(
                ArrayListGenerator(jcType), LinkedListGenerator(jcType), HashSetGenerator(jcType), LinkedHashSetGenerator(jcType)
            )
            jcClasspath.hashSetType().typeNameWOGenerics -> HashSetGenerator(jcType)
            jcClasspath.linkedHashMapType().typeNameWOGenerics -> LinkedListGenerator(jcType)
            jcClasspath.mapType().typeNameWOGenerics -> getRandomFrom(
                HashMapGenerator(jcType), LinkedHashMapGenerator(jcType), TreeMapGenerator(jcType), ConcurrentHashMapGenerator(jcType)
            )
            jcClasspath.hashMapType().typeNameWOGenerics -> HashMapGenerator(jcType)
            jcClasspath.linkedHashMapType().typeNameWOGenerics -> LinkedHashMapGenerator(jcType)
            jcClasspath.treeMapType().typeNameWOGenerics -> TreeMapGenerator(jcType)
            else -> getSuitableGeneratorForType(jcType)
        }

    private fun getSuitableGeneratorForType(jcType: JcClassType): Generator {
        val jcClass = jcType.jcClass
        return when {
            jcClass.isAbstract || jcClass.isInterface -> SafeAbstractClassGenerator(jcType)
            jcClass.isEnum -> EnumClassGenerator(jcType)
            GeneratorSettings.generationMode == GenerationMode.SAFE -> SafeUserClassGenerator(jcType)
            GeneratorSettings.generationMode == GenerationMode.UNSAFE -> UnsafeUserClassGenerator(jcType)
            else -> error("Cant find suitable generator for type ${jcType.typeName}")
        }
    }

    private fun getRandomFrom(vararg generators: Generator) = generators.random()

    private val JcType.typeNameWOGenerics
        get() = typeName.substringBefore('<')

    private fun getPrimitiveGeneratorForType(jcType: JcType, jcClasspath: JcClasspath) =
        when (jcType) {
            jcClasspath.boolean -> BooleanGenerator()
            jcClasspath.byte -> ByteGenerator()
            jcClasspath.int -> IntegerGenerator()
            jcClasspath.short -> ShortGenerator()
            jcClasspath.long -> LongGenerator()
            jcClasspath.float -> FloatGenerator()
            jcClasspath.double -> DoubleGenerator()
            jcClasspath.char -> CharGenerator()
            else -> error("Not primitive type")
        }


}