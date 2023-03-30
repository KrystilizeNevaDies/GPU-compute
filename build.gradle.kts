import org.lwjgl.Lwjgl
import org.lwjgl.Snapshot

plugins {
    java
    `java-library`
    `maven-publish`
    id("org.lwjgl.plugin") version "0.0.20"
}

group = "org.krystilize"
version = "-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

// Include source code in the jar
tasks.withType<Jar> {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

dependencies {
    Lwjgl {
//        this.allNatives = true;
        implementation(Lwjgl.Module.core)
        implementation(Lwjgl.Module.glfw)
        implementation(Lwjgl.Module.opengl)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// use the DuplicatesStrategy.EXCLUDE strategy to ignore duplicate classes
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}