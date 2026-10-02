plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    systemProperty("nearport.interop", System.getProperty("nearport.interop", ""))
    systemProperty("nearport.fixtures", rootProject.file("../../protocol/test-vectors").absolutePath) }
