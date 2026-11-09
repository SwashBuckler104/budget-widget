package budgetwidget.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applied once, on the root project. Formats the whole repo except build output and the submodule. */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(this == rootProject) { "bw.spotless must be applied to the root project only" }
            pluginManager.apply("com.diffplug.spotless")

            val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
            val composeRules = libs.library("compose-rules-ktlint").get().toString()
            val excluded = listOf("**/build/**", "**/.gradle/**", "third_party/**")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude(excluded)
                    ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
                }
                kotlinGradle {
                    target("**/*.gradle.kts")
                    targetExclude(excluded)
                    ktlint(ktlintVersion)
                }
                format("xml") {
                    target("**/*.xml")
                    targetExclude(excluded + "**/.idea/**")
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                format("misc") {
                    target("**/*.md", "**/*.yml", "**/*.toml", "**/*.properties", "**/.gitignore", "**/*.sh")
                    // SCOPE.md is the product spec and is kept byte-for-byte as written.
                    targetExclude(excluded + listOf("docs/SCOPE.md", "local.properties"))
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
    }
}
