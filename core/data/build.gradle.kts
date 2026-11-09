plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.turbine)
}
