plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.android.compose)
    alias(libs.plugins.bw.hilt)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.glance.appwidget.testing)
}
