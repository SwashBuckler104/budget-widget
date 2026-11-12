package budgetwidget.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** A `com.android.test` module that instruments `:app` (the baseline profile generator). */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")
            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                // Profile collection needs API 28+ on the device; the module itself may target lower.
                defaultConfig.targetSdk = Sdk.TARGET
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                lint.targetSdk = Sdk.TARGET
                targetProjectPath = ":app"
            }
        }
    }
}
