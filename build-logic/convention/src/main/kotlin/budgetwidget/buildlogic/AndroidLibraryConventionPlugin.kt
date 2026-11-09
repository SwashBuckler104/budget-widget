package budgetwidget.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                lint.targetSdk = Sdk.TARGET
                testOptions.targetSdk = Sdk.TARGET
                // Robolectric tests (Room, WorkManager, receivers) need merged resources and manifests.
                testOptions.unitTests.isIncludeAndroidResources = true
                // Robolectric's SDK 36 shared-memory shim uses jdk.internal.access.SharedSecrets.
                testOptions.unitTests.all {
                    it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                    // Hilt/KSP generate test sources even in modules without tests yet. Keep
                    // Gradle's "no tests found" guard for every module that has real tests.
                    it.failOnNoDiscoveredTests.set(file("src/test").exists())
                }
            }
            dependencies {
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", libs.library("truth"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("androidx-test-core"))
                add("testImplementation", libs.library("androidx-test-ext-junit"))
            }
        }
    }
}
