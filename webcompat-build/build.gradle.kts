import java.nio.file.Files

plugins {
    id("io.ia.sdk.modl")
}

val skipModuleSigning = providers.gradleProperty("skipModlSigning")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(true)
val webCompatDescription = "Serve web resources, run Python HTTP endpoints, and receive HTTP events in Ignition."
val moduleContent = layout.buildDirectory.dir("moduleContent")

dependencies {
    modlImplementation(project(":webcompat-common"))
    modlImplementation(project(":webcompat-gateway"))
    modlImplementation(project(":webcompat-designer"))
}

ignitionModule {
    name.set(property("moduleName") as String)
    fileName.set("webdev-compat")
    id.set(property("moduleId") as String)
    moduleVersion.set(property("moduleVersion") as String)
    moduleDescription.set(webCompatDescription)
    requiredIgnitionVersion.set(property("ignitionVersion") as String)
    projectScopes.set(
        mapOf(
            ":webcompat-common" to "GD",
            ":webcompat-gateway" to "G",
            ":webcompat-designer" to "D"
        )
    )
    hooks.set(
        mapOf(
            "com.jg.ignition.webcompat.gateway.GatewayHook" to "G",
            "com.jg.ignition.webcompat.designer.DesignerHook" to "D"
        )
    )
    skipModlSigning.set(skipModuleSigning)
}

tasks.named("assembleModlStructure") {
    doFirst {
        // The assembly plugin copies into this directory and leaves old files in place.
        project.delete(moduleContent)
    }
}

tasks.named("writeModuleXml") {
    doLast {
        val contentDir = moduleContent.get().asFile
        val moduleXmlFile = contentDir.resolve("module.xml")
        if (!moduleXmlFile.exists()) {
            return@doLast
        }

        val version = project.version.toString()
        val moduleId = project.findProperty("moduleId").toString()
        val moduleName = project.findProperty("moduleName").toString()
        val ignitionVersion = project.findProperty("ignitionVersion").toString()

        // The assembly plugin includes the packaging project's empty JAR by default.
        Files.deleteIfExists(moduleXmlFile.toPath().resolveSibling("webcompat-build-$version.jar"))

        val jarFiles = contentDir
            .listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.map { it.name }
            ?.filterNot { it.startsWith("logback-") || it.startsWith("slf4j-") }
            ?.filterNot { it.startsWith("webcompat-build-") }
            ?.sorted()
            ?: emptyList()

        fun scopeFor(jar: String): String = when {
            jar.startsWith("webcompat-gateway-") -> "G"
            jar.startsWith("webcompat-designer-") -> "D"
            jar.startsWith("webcompat-common-") -> "GD"
            else -> "G"
        }

        val moduleXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<modules>\n")
            append("\t<module>\n")
            append("\t\t<name>").append(moduleName).append("</name>\n")
            append("\t\t<id>").append(moduleId).append("</id>\n")
            append("\t\t<version>").append(version).append("</version>\n")
            append("\t\t<description>").append(webCompatDescription).append("</description>\n")
            append("\t\t<requiredIgnitionVersion>").append(ignitionVersion).append("</requiredIgnitionVersion>\n")
            append("\t\t<freeModule>true</freeModule>\n")
            append("\t\t<hook scope=\"G\">com.jg.ignition.webcompat.gateway.GatewayHook</hook>\n")
            append("\t\t<hook scope=\"D\">com.jg.ignition.webcompat.designer.DesignerHook</hook>\n")
            append("\t\t<depends scope=\"GD\" required=\"false\">com.inductiveautomation.eventstream</depends>\n")
            append("\t\t<requiredFrameworkVersion>8</requiredFrameworkVersion>\n")

            jarFiles.forEach { jar ->
                append("\t\t<jar scope=\"").append(scopeFor(jar)).append("\">")
                    .append(jar)
                    .append("</jar>\n")
            }

            append("\t</module>\n")
            append("</modules>\n")
        }

        moduleXmlFile.writeText(moduleXml)
    }
}
