dependencies {
    implementation(project(":webcompat-common"))
    compileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    compileOnly("com.inductiveautomation.ignitionsdk:event-stream-gateway:${property("ignitionVersion")}") {
        exclude(group = "com.inductiveautomation.eventstream", module = "web-ui")
    }
    compileOnly("com.inductiveautomation.ignitionsdk:event-stream-common:${property("ignitionVersion")}")
    compileOnly("org.eclipse.jetty:jetty-http:12.0.15")
    testCompileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    testRuntimeOnly("com.inductiveautomation.ignitionsdk:gateway-api:${property("ignitionVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.eclipse.jetty:jetty-http:12.0.15")
}

tasks.test {
    useJUnitPlatform()
}
