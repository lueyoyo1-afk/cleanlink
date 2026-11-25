import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 签名口令不进版本库：这个仓库是公开的，写进 build.gradle.kts 等于把私钥交出去。
// 本地放在 local.properties（已被 .gitignore 排除）。
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String): String? = signingProps.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "me.ipromptnest.cleanlink"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.ipromptnest.cleanlink"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        // 自用应用，debug 与 release 用同一张证书：
        // 否则两个包互相覆盖不了，应用内更新下载完也装不上。
        create("shared") {
            val storePath = signingValue("CLEANLINK_STORE") ?: "keystore/cleanlink.jks"
            val storePass = signingValue("CLEANLINK_STORE_PASSWORD")
            val alias = signingValue("CLEANLINK_KEY_ALIAS") ?: "cleanlink"
            val keyPass = signingValue("CLEANLINK_KEY_PASSWORD")

            require(storePass != null) {
                "缺少签名口令。在 local.properties 里补上 CLEANLINK_STORE_PASSWORD / CLEANLINK_KEY_PASSWORD。"
            }
            storeFile = file(storePath)
            storePassword = storePass
            keyAlias = alias
            keyPassword = keyPass ?: storePass
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
