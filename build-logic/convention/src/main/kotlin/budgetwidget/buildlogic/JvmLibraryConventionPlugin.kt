package budgetwidget.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Pure Kotlin/JVM module (no Android APIs), e.g. `:core:model`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            configureKotlin()
            // Gradle runs on JDK 21; compile against the JDK 17 API so nothing newer slips into Android code.
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions.freeCompilerArgs.add("-Xjdk-release=${JAVA_VERSION.majorVersion}")
            }
            extensions.configure<Lint> { configureLint(this) }
            enforceParserBoundary()
        }
    }
}
