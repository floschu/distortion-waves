plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "at.florianschuster.distortionwaves.sample.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm("desktop")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SampleShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":distortion-waves"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.compose.ui.test)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// ./gradlew :sample:shared:desktopTest -Pshowcase records the frames for media/showcase.sh.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("showcase")) {
        systemProperty(
            "showcase",
            layout.buildDirectory.dir("showcase").get().asFile.path,
        )
        (project.findProperty("showcaseSeconds") as String?)?.let {
            systemProperty("showcaseSeconds", it)
        }
        maxHeapSize = "4g"
        outputs.upToDateWhen { false }
    }
}
