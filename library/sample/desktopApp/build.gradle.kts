plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop { application { mainClass = "at.florianschuster.distortionwaves.sample.MainKt" } }
