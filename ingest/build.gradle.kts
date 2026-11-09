plugins {
    alias(libs.plugins.bw.android.library)
    alias(libs.plugins.bw.hilt)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    // The only module allowed to see the upstream parser (enforced by the convention plugins).
    // `implementation`, never `api`, so com.pennywiseai.* stays off every other compile classpath.
    implementation(projects.parserCore)

    implementation(libs.androidx.core.ktx)
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.work.testing)
}
