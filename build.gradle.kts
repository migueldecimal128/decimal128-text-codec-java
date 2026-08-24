import org.gradle.plugins.signing.SigningExtension

group = "com.decimal128"
version = "0.9.0"

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The conformance corpus lives in decimal128-resources, mounted as a submodule
// at <repo>/resources. Nothing is copied into this repo, so the test task is
// handed the absolute corpus root and the loader reads it from there -- which
// is what makes it independent of the runner's working directory.
val corpusRoot = rootDir.resolve("resources").absolutePath

tasks.test {
    useJUnitPlatform()
    // The codec spells its internal-consistency claims as `assert`. Gradle arms
    // assertions on test tasks by default; this states it rather than relying on
    // it, because a build where they are off silently drops every one of them.
    enableAssertions = true
    environment("DECIMAL128_RESOURCES", corpusRoot)
    // The canonical table is 84,783 rows through the codec twice.
    maxHeapSize = "1g"
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.decimal128", "decimal128-text-codec-java", version.toString())

    pom {
        name.set("decimal128-text-codec-java")
        description.set("Thin BID128/DPD128 ↔ text codec for IEEE 754-2019 decimal128 for Java.")
        url.set("https://github.com/migueldecimal128/decimal128-text-codec-java")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("migueldecimal128")
                name.set("MiguelDecimal128")
                email.set("miguel@decimal128.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/migueldecimal128/decimal128-text-codec-java.git")
            developerConnection.set("scm:git:ssh://github.com/migueldecimal128/decimal128-text-codec-java.git")
            url.set("https://github.com/migueldecimal128/decimal128-text-codec-java")
        }
    }
}

extensions.getByType<SigningExtension>().useGpgCmd()
