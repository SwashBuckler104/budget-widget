plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.turbine)
    testImplementation(libs.androidx.datastore.preferences)
}
