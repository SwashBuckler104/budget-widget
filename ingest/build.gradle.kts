plugins {
    alias(libs.plugins.bw.android.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    // The only module allowed to see the upstream parser (enforced by the convention plugins).
    // `implementation`, never `api`, so com.pennywiseai.* stays off every other compile classpath.
    implementation(projects.parserCore)
}
