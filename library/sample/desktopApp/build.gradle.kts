plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktfmt)
}

// The Kotlin coding conventions, as kotlinlang.org writes them.
ktfmt { kotlinLangStyle() }

dependencies {
    implementation(project(":sample:shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop { application { mainClass = "at.florianschuster.distortionwaves.sample.MainKt" } }
