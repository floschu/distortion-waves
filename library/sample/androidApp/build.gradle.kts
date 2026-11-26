plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktfmt)
}

// The Kotlin coding conventions, as kotlinlang.org writes them.
ktfmt { kotlinLangStyle() }

android {
    namespace = "at.florianschuster.distortionwaves.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "at.florianschuster.distortionwaves.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        // The library's version: the release tag without its v, or "local" on any other build.
        versionName = System.getenv("libraryVersionTag") ?: "local"
    }
    buildTypes {
        // The build to judge the wave by: R8 shrinks and optimises it, as it
        // would a shipped app. Signed with the debug key so it installs as is.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(libs.androidx.activity.compose)
}
