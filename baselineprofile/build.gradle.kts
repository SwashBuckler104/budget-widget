plugins {
    alias(libs.plugins.bw.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

baselineProfile {
    // The emulator or phone the developer has connected; no Gradle-managed device download.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
