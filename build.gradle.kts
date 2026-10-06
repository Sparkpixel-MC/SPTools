plugins {
    java
}

group = "cn.ymjacky"
version = "5.0-26.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.lucko.me/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.130-stable")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("net.luckperms:api:5.5")
    // Paper/Folia ships Gson on the server classpath, so it is not bundled.
    compileOnly("com.google.code.gson:gson:2.14.0")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName = "SPTools"
}
