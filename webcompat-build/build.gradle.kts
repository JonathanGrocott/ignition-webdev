plugins {
    id("io.ia.sdk.modl")
}

val skipModuleSigning = providers.gradleProperty("skipModlSigning")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(true)

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
    moduleDescription.set("Web Dev-compatible Python endpoint module for Ignition 8.3")
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

tasks.named("writeModuleXml") {
    doLast {
        val moduleXmlFile = file("build/moduleContent/module.xml")
        if (!moduleXmlFile.exists()) {
            return@doLast
        }

        val version = project.version.toString()
        val moduleId = project.findProperty("moduleId").toString()
        val moduleName = project.findProperty("moduleName").toString()
        val ignitionVersion = project.findProperty("ignitionVersion").toString()

        val jarFiles = file("build/moduleContent")
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
            append("\t\t<description>Web Dev-compatible Python endpoint module for Ignition 8.3</description>\n")
            append("\t\t<requiredIgnitionVersion>").append(ignitionVersion).append("</requiredIgnitionVersion>\n")
            append("\t\t<freeModule>true</freeModule>\n")
            append("\t\t<hook scope=\"G\">com.jg.ignition.webcompat.gateway.GatewayHook</hook>\n")
            append("\t\t<hook scope=\"D\">com.jg.ignition.webcompat.designer.DesignerHook</hook>\n")
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
