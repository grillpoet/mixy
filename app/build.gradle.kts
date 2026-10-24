plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.grillpoet.mixy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.grillpoet.mixy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // TODO(release): 上架前替换为正式签名配置。
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        // AppLinkUrlError: 4 个 VIEW intent-filter 是纯 MIME 类型过滤器（打开方式），
        // 不是 App Link，无 scheme/host 是正确写法。lint 误判。
        disable += "AppLinkUrlError"
        // ExportedService: Media3 的 MediaSessionService 必须 exported=true，
        // 否则系统通知栏/蓝牙等无法发现服务。
        disable += "ExportedService"
        // GradleDependency: 版本更新提示，不需要每次构建都看。
        disable += "GradleDependency"
        // ChromeOsAbiSupport: 不做 ChromeOS 适配，x86_64 abi 不需要。
        disable += "ChromeOsAbiSupport"
        // PictureInPictureIssue: targetSdk 31+ 建议用 setAutoEnterEnabled + setSourceRectHint。
        // 当前用 lifecycle ON_STOP 手动进 PiP，功能可用，改造暂不做。
        disable += "PictureInPictureIssue"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.androidx.documentfile)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)

    // Coil GIF 解码器（同时支持 GIF 和动画 WebP）
    implementation(libs.coil.gif)

    // androidTest 的 Compose 版本约束依赖此 BOM。
    // Gradle 8.11 会报 "declared multiple times" 警告，但删掉会导致
    // debugAndroidTestRuntimeClasspath 解析失败（ui-test-junit4 找不到版本），
    // 所以必须保留。警告忽略。
    implementation(libs.androidx.lifecycle.runtime.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}