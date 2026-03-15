dependencies {
    implementation(project(":webcompat-common"))
    compileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    testCompileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    testRuntimeOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
}

tasks.test {
    useJUnitPlatform()
}
