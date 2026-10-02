import java.util.Properties

plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
val releaseVersion = Properties().apply {
    rootProject.file("../../version.properties").inputStream().use { load(it) }
}
val signingPath = providers.environmentVariable("DROPDUO_ANDROID_KEYSTORE").orNull
android {
    namespace = "app.dropduo.android"
    compileSdk = 36
    defaultConfig { applicationId = "app.dropduo.android"; minSdk = 29; targetSdk = 36; versionCode = releaseVersion.getProperty("versionCode").toInt(); versionName = releaseVersion.getProperty("versionName"); testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true; buildConfig = true }
    if (signingPath != null) {
        signingConfigs.create("distribution") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("DROPDUO_ANDROID_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("DROPDUO_ANDROID_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("DROPDUO_ANDROID_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribution")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.14.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
