dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${property("ignitionVersion")}")
    testCompileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${property("ignitionVersion")}")
    testRuntimeOnly("com.inductiveautomation.ignitionsdk:ignition-common:${property("ignitionVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}
