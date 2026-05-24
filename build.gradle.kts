plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.antixray"
version = findProperty("pluginVersion") as String? ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "SpigotMC"
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "PaperMC"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "PlaceholderAPI"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
    maven("https://repo.dmulloy2.net/repository/public/") {
        name = "ProtocolLib"
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:${property("spigotApiVersion")}")
    compileOnly("com.comphenix.protocol:ProtocolLib:${property("protocolLibVersion")}")
    compileOnly("me.clip:placeholderapi:${property("placeholderApiVersion")}")
    compileOnly("com.github.MilkBowl:VaultAPI:${property("vaultVersion")}")

    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")

    implementation("com.zaxxer:HikariCP:${property("hikariCpVersion")}")
    implementation("org.xerial:sqlite-jdbc:${property("sqliteJdbcVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.mockito:mockito-core:${property("mockitoVersion")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${property("mockitoVersion")}")
    testImplementation("org.spigotmc:spigot-api:${property("spigotApiVersion")}")
    testImplementation("org.mockito:mockito-inline:${property("mockitoInlineVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("Medusa-Anti-Xray")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    relocate("com.github.benmanes.caffeine", "com.antixray.lib.caffeine")
    relocate("com.zaxxer.hikari", "com.antixray.lib.hikari")
    relocate("org.sqlite", "com.antixray.lib.sqlite")

    // Exclude unnecessary SQLite native libraries (relocated package path)
    exclude("**/sqlite/native/Linux-Android/**")
    exclude("**/sqlite/native/FreeBSD/**")
    exclude("**/sqlite/native/Linux/ppc64/**")
    exclude("**/sqlite/native/Linux/x86/**")
    exclude("**/sqlite/native/Linux/arm/**")
    exclude("**/sqlite/native/Linux/armv6/**")
    exclude("**/sqlite/native/Linux/armv7/**")
    exclude("**/sqlite/native/Linux-Musl/x86/**")
    exclude("**/sqlite/native/Windows/x86/**")
    exclude("**/sqlite/native/Windows/armv7/**")
    exclude("**/sqlite/native/Windows/aarch64/**")
    
    // Also clean up any Maven pom/metadata files
    exclude("META-INF/maven/**")
    exclude("META-INF/dependency-descriptor.properties")

    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}
