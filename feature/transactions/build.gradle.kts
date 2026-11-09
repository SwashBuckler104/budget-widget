plugins {
    alias(libs.plugins.bw.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.turbine)
    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
}
