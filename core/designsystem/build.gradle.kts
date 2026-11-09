plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.android.compose)
}

dependencies {
    // Exported with the BOM so consumers resolve the same Compose versions.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}
