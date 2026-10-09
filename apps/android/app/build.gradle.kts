import java.util.Properties

plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
val releaseVersion = Properties().apply {
    rootProject.file("../../version.properties").inputStream().use { load(it) }
}
// The Release workflow sets these; version.properties covers local and test builds.
val appVersionName = providers.environmentVariable("DROPDUO_VERSION").orNull ?: releaseVersion.getProperty("versionName")
val appVersionCode = (providers.environmentVariable("DROPDUO_BUILD").orNull ?: releaseVersion.getProperty("versionCode")).toInt()
val signingPath = providers.environmentVariable("DROPDUO_ANDROID_KEYSTORE").orNull
android {
    namespace = "app.dropduo.android"
    compileSdk = 36
    defaultConfig { applicationId = "app.dropduo.android"; minSdk = 29; targetSdk = 36; versionCode = appVersionCode; versionName = appVersionName; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
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
// Releases upload only the APK, so its license and notices travel inside it as assets.
abstract class CopyNotices : DefaultTask() {
    @get:InputFiles abstract val notices: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun copy() {
        val dir = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        notices.forEach { it.copyTo(dir.resolve(if (it.name == "dependencies.md") "DEPENDENCIES.md" else it.name), overwrite = true) }
    }
}
val copyNotices = tasks.register<CopyNotices>("copyNotices") {
    notices.from(rootProject.file("../../LICENSE"), rootProject.file("../../NOTICE"), rootProject.file("../../docs/dependencies.md"))
}
androidComponents { onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyNotices, CopyNotices::outputDir) } }
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
