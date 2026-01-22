plugins {
    application
    id("com.gradleup.shadow") version "9.2.2"
}

group = "flammable.bunny"
version = "v1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.formdev:flatlaf:3.4")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("flammable.bunny.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks.shadowJar {
    archiveBaseName.set("Lingle")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
}

// Fix task dependency warnings
tasks.named("distZip") {
    dependsOn(tasks.shadowJar)
}
tasks.named("distTar") {
    dependsOn(tasks.shadowJar)
}
tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}
tasks.named("startShadowScripts") {
    dependsOn(tasks.jar)
}

sourceSets {
    named("main") {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }
}
