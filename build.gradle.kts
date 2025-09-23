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
    implementation("com.github.javaparser:javaparser-core:3.27.0")

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

sourceSets.main {
    java.srcDirs("src/main/java", "build/generated/sources/annotationProcessor/java/main")
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