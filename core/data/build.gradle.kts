plugins {
    alias(libs.plugins.bw.android.library)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
}
