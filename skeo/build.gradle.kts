plugins {
    `maven-publish`
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
    alias(libs.plugins.serialization)
    signing
}

val libName = "skeo"
val libVersion = "0.3.0"
val artifact = "dev.datlag.skeo"

group = artifact
version = libVersion

kotlin {
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }

    wasmJs {
        browser()
        nodejs()
        binaries.executable()
    }

    linuxX64()
    linuxArm64()

    mingwX64()

    androidNativeX64()
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines)
            implementation(libs.immutable)
            api(libs.ktor)
            api(libs.ksoup)
            implementation(libs.ksoup.network)
            implementation(libs.serialization.json)
            implementation(libs.tooling)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
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