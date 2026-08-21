plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.miku.ray.remixicon"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }
}

dependencies {
    implementation(libs.material)
}
