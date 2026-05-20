// TOP-LEVEL build.gradle (Project: LunchboxDelivery)

plugins {
    // Uses the version defined in gradle/libs.versions.toml (currently 9.1.0)
    alias(libs.plugins.android.application) apply false
    // Google Services plugin for Firebase
    id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
