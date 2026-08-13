import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

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
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    if (hasReleaseSigning) signingConfigs { create("release") { storeFile = file(releaseKeystorePath!!); storePassword = releaseKeystorePassword; keyAlias = releaseKeyAlias; keyPassword = releaseKeyPassword } }
    buildTypes { release { isMinifyEnabled = false; if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release") } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.36")
    implementation("com.googlecode.libphonenumber:geocoder:3.36")
    implementation("com.googlecode.libphonenumber:carrier:2.36")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    ksp("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val packageDebugUnitTestClasses by tasks.registering(Jar::class) {
    dependsOn("compileDebugUnitTestKotlin")
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"))
    archiveFileName.set("message-relay-debug-unit-test-classes.jar")
    destinationDirectory.set(file("${System.getProperty("user.home")}/.gradle/message-relay-test-jars"))
}

val packageDebugMainClasses by tasks.registering(Jar::class) {
    dependsOn("compileDebugKotlin")
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"))
    archiveFileName.set("message-relay-debug-main-classes.jar")
    destinationDirectory.set(file("${System.getProperty("user.home")}/.gradle/message-relay-test-jars"))
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    (this as Test).apply {
        dependsOn(packageDebugUnitTestClasses)
        dependsOn(packageDebugMainClasses)
    }
}

gradle.projectsEvaluated {
    tasks.named<Test>("testDebugUnitTest").configure {
        classpath += files(packageDebugUnitTestClasses.flatMap { it.archiveFile })
        classpath += files(packageDebugMainClasses.flatMap { it.archiveFile })
    }
}
