import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("multiplatform") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.vanniktech.maven.publish") version "0.31.0"
    `maven-publish`
    signing
}

val libName = "skeo"
val libVersion = "0.2.5"
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
        nodejs()
        binaries.executable()
    }
    linuxX64()
    linuxArm64()

    mingwX64()

    macosX64()
    macosArm64()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    jvmToolchain(JavaVersion.VERSION_17.majorVersion.toIntOrNull() ?: (JavaVersion.VERSION_17.ordinal + 1))
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation("com.fleeksoft.ksoup:ksoup:0.2.3")
            implementation("io.ktor:ktor-client-core:3.1.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
    }
}

mavenPublishing {
    publishToMavenCentral(host = SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
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