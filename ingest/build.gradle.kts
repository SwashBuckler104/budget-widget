plugins {
    alias(libs.plugins.bw.android.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
}
