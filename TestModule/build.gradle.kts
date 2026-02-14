plugins {
    id("java")
    id("jinix") version "0.0.1"
}

group = "org.example"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


tasks.test {
    useJUnitPlatform()
}