plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

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

        jvmMain.dependencies {
            implementation(libs.ktor.okhttp)
        }
    }
}