plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.android.compose)
    alias(libs.plugins.bw.hilt)
}

android {
    // Applied to the app's release build: keeps what Glance instantiates by reflection.
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    // Brand palette and money colors, shared with the app so widgets match it.
    implementation(projects.core.designsystem)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.androidx.glance.appwidget.testing)
}
