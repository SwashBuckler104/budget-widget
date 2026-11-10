plugins {
    alias(libs.plugins.bw.android.feature)
}

dependencies {
    testImplementation(projects.core.database)
    testImplementation(projects.core.datastore)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.datastore.preferences)
}
