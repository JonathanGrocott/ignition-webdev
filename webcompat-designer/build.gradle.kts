dependencies {
    implementation(project(":webcompat-common"))
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:${property("ignitionVersion")}")
    compileOnly("com.inductiveautomation.ignitionsdk:event-stream-designer:${property("ignitionVersion")}")
}
