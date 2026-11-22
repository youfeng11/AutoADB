import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val releaseSigningFile: File = rootProject.file(".signing/release.properties")
val releaseSigningProperties: Properties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val releaseStoreFile: String =
    releaseSigningProperties.getProperty("storeFile", ".signing/autoadb-release.p12")
val releaseKeyAlias: String = releaseSigningProperties.getProperty("keyAlias", "autoadb-release")
val releaseStorePassword: String? = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
    ?: releaseSigningProperties.getProperty("storePassword")
val releaseKeyPassword: String? = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
    ?: releaseSigningProperties.getProperty("keyPassword")
val releaseSigningReady =
    listOf(releaseStoreFile, releaseKeyAlias, releaseStorePassword, releaseKeyPassword)
        .all { !it.isNullOrBlank() }
val validateReleaseSigningConfiguration: TaskProvider<Task> =
    tasks.register("validateReleaseSigningConfiguration") {
        group = "verification"
        description = "Checks that Release signing credentials are configured."
        val configured = releaseSigningReady
        doLast {
            check(configured) {
                "Release signing is missing. Restore the release keystore and local properties, or set RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD."
            }
        }
    }
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigningConfiguration)
}

android {
    namespace = "com.youfeng.autoadb"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.youfeng.autoadb"
        minSdk = 30
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
