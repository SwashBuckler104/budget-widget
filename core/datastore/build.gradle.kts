plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
