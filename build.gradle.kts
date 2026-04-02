import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.erotoro"
version = "1.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.tcoded.com/releases")
}

dependencies {
    compileOnly("dev.folia:folia-api:1.20.1-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("project" to mapOf("version" to project.version))
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = project.configurations.runtimeClasspath.map { setOf(it) }
    archiveClassifier.set("")

    dependencies {
        exclude {
            it.moduleGroup != "org.bstats"
        }
    }

    relocate("org.bstats", project.group.toString())
}
