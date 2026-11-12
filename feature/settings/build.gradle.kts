plugins {
    alias(libs.plugins.bw.android.feature)
}

dependencies {
    implementation(projects.core.datastore)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.datastore.preferences)
}
