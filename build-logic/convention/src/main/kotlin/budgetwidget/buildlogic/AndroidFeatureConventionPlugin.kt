package budgetwidget.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module: Android library + Compose + Hilt, wired to the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("bw.android.library")
            pluginManager.apply("bw.android.compose")
            pluginManager.apply("bw.hilt")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
            }
        }
    }
}
