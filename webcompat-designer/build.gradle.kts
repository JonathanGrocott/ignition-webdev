dependencies {
    implementation(project(":webcompat-common"))
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:${property("ignitionVersion")}")
}
