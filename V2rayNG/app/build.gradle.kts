import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
}

val appVersionName = "2.2.7"
val appVersionCode = 737

val jakartaTime = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
val appBuildDate = jakartaTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
val apkBuildDate = jakartaTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

base {
    archivesName.set("MikuRay_$appVersionName")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37
    
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.miku.ray"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        
        resValue("string", "uwu_version_name", appVersionName.toString())
        resValue("string", "uwu_version_code", appVersionCode.toString())
        resValue("string", "uwu_package_name", applicationId.toString())
        resValue("string", "uwu_build_date", appBuildDate.toString())

        val abiFilterList = (project.findProperty("ABI_FILTERS") as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("appSigning") {
            val ksPath = project.findProperty("KS_PATH") as? String
            if (!ksPath.isNullOrEmpty()) {
                storeFile = file(ksPath)
                storePassword = project.findProperty("KS_PASS") as? String
                keyAlias = project.findProperty("KS_ALIAS") as? String
                keyPassword = project.findProperty("KEY_PASS") as? String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (project.hasProperty("KS_PATH")) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        debug {
            if (project.hasProperty("KS_PATH")) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        val versionCodes = mapOf(
            "armeabi-v7a" to 4, 
            "arm64-v8a" to 4, 
            "x86" to 4, 
            "x86_64" to 4, 
            "universal" to 4
        )

        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }
            val abi = abiFilter?.identifier ?: "universal"

            if (versionCodes.containsKey(abi)) {
                val multiplier = versionCodes[abi]!!
                output.versionCode.set((1000000 * multiplier) + appVersionCode)
            }

            if (variant.buildType == "debug") {
                val abiSuffix = if (abi != "universal") "-$abi" else ""
                output.outputFileName.set("MikuRay_$appVersionName$abiSuffix-debug-$apkBuildDate.apk")
            }
        }
    }
}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment)
    implementation(libs.palette)
    implementation(libs.play.services.location)

    // UI Libraries
    implementation(libs.material)
    implementation(libs.editorkit)
    implementation(libs.flexbox)
    implementation(libs.skydoves.colorpickerview)
    implementation(libs.qmdeve.blurview)
    implementation(libs.com.airbnb.android.lottie)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Language and Processing Libraries
    implementation(libs.language.base)
    implementation(libs.language.json)

    // Intent and Utility Libraries
    implementation(libs.quickie.foss)
    implementation(libs.core)

    // Image loading & cropping
    implementation(libs.glide)
    implementation(libs.ucrop)

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Testing Libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.org.mockito.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
