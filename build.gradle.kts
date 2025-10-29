import org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "org.jinix"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.auto.service:auto-service:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    implementation("org.ow2.asm:asm:9.8")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.27.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

gradlePlugin {
    plugins {
        create("jinix") {
            id = "jinix"
            implementationClass = "org.jinix.plugin.JinixPlugin"
        }
    }
}

configurations.all {
    withDependencies {
        val toModify = filterIsInstance<DefaultFileCollectionDependency>()

        toModify.forEach { dep ->   // JavaParser dependency from Gradle API conflicts with newer versions
            val filteredFiles = dep.files.filter { file -> !file.name.contains("javaparser") }
            if (!filteredFiles.isEmpty) {
                remove(dep)
                add(project.dependencies.create(project.files(filteredFiles)))
            } else {
                remove(dep)
            }
        }
    }
}

publishing {
    publications {
        create("mavenJava", MavenPublication::class) {
            artifactId = "jinix-plugin"
            artifact(tasks.jar)
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.test {
    useJUnitPlatform()
}