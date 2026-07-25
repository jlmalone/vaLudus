plugins {
    kotlin("jvm") version "2.3.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.valudus.ValudusCliKt")
}

tasks.test {
    useJUnitPlatform()
}
