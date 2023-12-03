import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("com.vanniktech.maven.publish") version "0.25.3"
    `maven-publish`
    signing
}

val libName = "skeo"
val libVersion = "0.1.1"
val artifact = "dev.datlag.skeo"
group = artifact
version = libVersion

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }
    linuxX64()
    mingwX64()
    macosArm64()
    macosX64()
    iosSimulatorArm64()
    iosArm64()
    iosX64()

    jvmToolchain(JavaVersion.VERSION_17.majorVersion.toIntOrNull() ?: (JavaVersion.VERSION_17.ordinal + 1))
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.drewcarlson:ktsoup-core:0.3.0")
                implementation("org.drewcarlson:ktsoup-ktor:0.3.0")
                implementation("dev.datlag.jsunpacker:jsunpacker:1.0.2")
                implementation("io.ktor:ktor-client-core:2.3.6")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}

mavenPublishing {
    publishToMavenCentral(host = SonatypeHost.S01, automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = artifact,
        artifactId = libName,
        version = libVersion
    )

    pom {
        name.set(libName)
        description.set("Kotlin multiplatform video source scraper.")
        url.set("https://github.com/DatL4g/Skeo")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/DatL4g/Skeo")
            connection.set("scm:git:git://github.com/DatL4g/Skeo.git")
        }

        developers {
            developer {
                id.set("DatL4g")
                name.set("Jeff Retz (DatLag)")
                url.set("https://github.com/DatL4g")
            }
        }
    }
}