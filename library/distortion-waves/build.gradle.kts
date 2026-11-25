import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    explicitApi()

    // ./gradlew checkKotlinAbi compares the public API with api/, and
    // ./gradlew updateKotlinAbi writes it after an intended change.
    @OptIn(ExperimentalAbiValidation::class) abiValidation {}

    android {
        namespace = "at.florianschuster.distortionwaves"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    // Everything but Android draws with Skia through Skiko.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
                withWasmJs()
            }
        }
    }

    sourceSets {
        // The public API takes Modifier, Color and composable lambdas, so callers get these too.
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.ui.test)
            }
        }
    }
}

// ---- publishing --- //

group = "at.florianschuster.distortionwaves"

version = System.getenv("libraryVersionTag") ?: "local"

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (version != "local") signAllPublications()
    coordinates(group.toString(), "distortion-waves", version.toString())
    pom {
        name = "distortion-waves"
        description = "A colourful wave that carries one Compose Multiplatform view into the next."
        inceptionYear = "2026"
        url = "https://github.com/floschu/distortion-waves"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "floschu"
                name = "Florian Schuster"
                url = "https://github.com/floschu"
            }
        }
        scm {
            url = "https://github.com/floschu/distortion-waves"
            connection = "scm:git:git://github.com/floschu/distortion-waves.git"
            developerConnection = "scm:git:ssh://git@github.com/floschu/distortion-waves.git"
        }
    }
}

// ---- end publishing --- //
