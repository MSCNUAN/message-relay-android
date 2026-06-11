plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val releaseKeystorePath = providers.environmentVariable("MESSAGE_RELAY_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("MESSAGE_RELAY_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MESSAGE_RELAY_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MESSAGE_RELAY_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.messagerelay"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.messagerelay"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    if (hasReleaseSigning) signingConfigs { create("release") { storeFile = file(releaseKeystorePath!!); storePassword = releaseKeystorePassword; keyAlias = releaseKeyAlias; keyPassword = releaseKeyPassword } }
    buildTypes { release { isMinifyEnabled = false; if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release") } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    ksp("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
