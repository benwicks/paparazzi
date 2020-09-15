/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import app.cash.paparazzi.VERSION
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import java.util.Locale

@Suppress("unused")
class PaparazziPlugin : Plugin<Project> {
  @OptIn(ExperimentalStdlibApi::class)
  override fun apply(project: Project) {
    require(project.plugins.hasPlugin("com.android.library")) {
      "The Android Gradle library plugin must be applied before the Paparazzi plugin."
    }

    project.configurations.getByName("testImplementation").dependencies.add(
        project.dependencies.create("app.cash.paparazzi:paparazzi:$VERSION")
    )

    val variants = project.extensions.getByType(LibraryExtension::class.java)
        .libraryVariants
    variants.all { variant ->
      val variantSlug = variant.name.capitalize(Locale.US)

      val writeResourcesTask = project.tasks.register(
          "preparePaparazzi${variantSlug}Resources", PrepareResourcesTask::class.java
      ) {
        // TODO: variant-aware file path
        it.outputs.file("${project.buildDir}/intermediates/paparazzi/resources.txt")

        // Temporary, until AGP provides outputDir as Provider<File>
        it.mergeResourcesProvider = variant.mergeResourcesProvider
        it.outputDir = project.layout.buildDirectory.dir("intermediates/paparazzi/resources.txt")
        it.dependsOn(variant.mergeResourcesProvider)
      }

      val testVariantSlug = variant.unitTestVariant.name.capitalize(Locale.US)

      project.plugins.withType(JavaBasePlugin::class.java) {
        project.tasks.named("compile${testVariantSlug}JavaWithJavac")
            .configure { it.dependsOn(writeResourcesTask) }
      }

      project.plugins.withType(KotlinBasePluginWrapper::class.java) {
        project.tasks.named("compile${testVariantSlug}Kotlin")
            .configure { it.dependsOn(writeResourcesTask) }
      }

      val recordTaskProvider = project.tasks.register("recordPaparazzi${variantSlug}") {
        System.setProperty("paparazzi.record", "true")
      }

      val verifyTaskProvider = project.tasks.register("verifyPaparazzi${variantSlug}") {
        System.setProperty("paparazzi.verify", "true")
      }

      val testTaskProvider = project.tasks.named("test${testVariantSlug}", Test::class.java) {
        it.systemProperty("paparazzi.test.record", System.getProperty("paparazzi.record") ?: "false")
        it.systemProperty("paparazzi.test.verify", System.getProperty("paparazzi.verify") ?: "false")
      }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }

      testTaskProvider.configure {
        it.doLast {
          val uri = project.buildDir.toPath().resolve("reports/paparazzi/index.html").toUri()
          project.logger.log(LIFECYCLE, "See the Paparazzi report at: $uri")
        }
      }
    }
  }
}