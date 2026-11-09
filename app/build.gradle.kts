plugins {
    alias(libs.plugins.bw.android.application)
    alias(libs.plugins.bw.android.compose)
    alias(libs.plugins.bw.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Release signing comes only from the environment (CI secrets, see release.yml). Without
    // KEYSTORE_PATH the release APK is built unsigned, so `assembleRelease` works for everyone.
    val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
    if (keystorePath != null) {
        fun required(name: String): String = providers.environmentVariable(name).orNull
            ?: throw GradleException("KEYSTORE_PATH is set but $name is missing")
        val release = signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = required("KEYSTORE_PASSWORD")
            keyAlias = required("KEY_ALIAS")
            keyPassword = required("KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = release
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.ingest)
    implementation(projects.notifications)
    implementation(projects.widget)
    implementation(projects.feature.analysis)
    implementation(projects.feature.transactions)
    implementation(projects.feature.categories)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
