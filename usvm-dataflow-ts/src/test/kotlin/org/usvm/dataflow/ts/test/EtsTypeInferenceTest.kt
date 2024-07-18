package org.usvm.dataflow.ts.test

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethodImpl
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.util.EtsTraits

class EtsTypeInferenceTest {

    companion object {
        private fun loadArkFile(path: String): EtsFile {
            val stream = object {}::class.java.getResourceAsStream("/$path")
                ?: error("Resource not found: $path")
            val arkDto = EtsFileDto.loadFromJson(stream)
            // println("arkDto = $arkDto")
            val ark = convertToEtsFile(arkDto)
            // println("ark = $ark")
            return ark
        }
    }

    @Test
    fun `test type inference`() {
        val name = "types"
        val arkFile = loadArkFile("ir/$name.ts.json")
        val graph = EtsApplicationGraph(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `test type inference on applications_settings_data`() {
        val arkFile = loadArkFile("ir/applications_settings_data/Utils/SettingsDBHelper.ets.json")
        val graph = EtsApplicationGraph(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
            .asSequence()
            .flatMap { it.methods }
            .filter { (it as EtsMethodImpl).modifiers.contains("PublicKeyword") }
            .toList()
        println("entrypoints = $entrypoints")

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }
}
