plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    systemProperty("dropduo.interop", System.getProperty("dropduo.interop", ""))
    systemProperty("dropduo.fixtures", rootProject.file("../../protocol/test-vectors").absolutePath) }
