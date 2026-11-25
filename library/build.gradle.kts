plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.ktfmt) apply false
}

subprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
    // The Kotlin coding conventions, as kotlinlang.org writes them.
    configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { kotlinLangStyle() }

    // The plugin does not see the androidMain source set of the Android KMP library plugin.
    val androidMain = file("src/androidMain/kotlin")
    if (androidMain.exists()) {
        val check =
            tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask>("ktfmtCheckAndroidMain") {
                source = fileTree(androidMain)
                include("**/*.kt")
            }
        val format =
            tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatAndroidMain") {
                source = fileTree(androidMain)
                include("**/*.kt")
            }
        tasks.named("ktfmtCheck") { dependsOn(check) }
        tasks.named("ktfmtFormat") { dependsOn(format) }
    }
}
