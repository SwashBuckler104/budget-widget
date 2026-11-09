package budgetwidget.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig {
                    applicationId = packageRoot
                    targetSdk = Sdk.TARGET
                    versionName = providers.gradleProperty("budgetwidget.versionName").get()
                    versionCode = providers.gradleProperty("budgetwidget.versionCode").get().toInt()
                }
                // Google's encrypted dependency blob is unreadable to F-Droid / IzzyOnDroid scanners.
                dependenciesInfo {
                    includeInApk = false
                    includeInBundle = false
                }
                // These ship pre-stripped from Maven (Compose graphics, DataStore 1.3's multi-process
                // counter); without the NDK AGP would warn that it cannot strip them again.
                packaging.jniLibs.keepDebugSymbols +=
                    setOf("**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so")
                buildTypes.getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
        }
    }
}
