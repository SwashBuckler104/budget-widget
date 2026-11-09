import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "budgetwidget.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.serialization.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.bw.android.application.get().pluginId
            implementationClass = "budgetwidget.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.bw.android.library.get().pluginId
            implementationClass = "budgetwidget.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.bw.android.compose.get().pluginId
            implementationClass = "budgetwidget.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.bw.android.feature.get().pluginId
            implementationClass = "budgetwidget.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.bw.android.room.get().pluginId
            implementationClass = "budgetwidget.buildlogic.AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.bw.hilt.get().pluginId
            implementationClass = "budgetwidget.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.bw.jvm.library.get().pluginId
            implementationClass = "budgetwidget.buildlogic.JvmLibraryConventionPlugin"
        }
        register("spotless") {
            id = libs.plugins.bw.spotless.get().pluginId
            implementationClass = "budgetwidget.buildlogic.SpotlessConventionPlugin"
        }
    }
}
