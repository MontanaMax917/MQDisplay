plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.mqdisplay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mqdisplay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

//    https://mvnrepository.com/artifact/org.videolan.android/libvlc-all  <-- all versions
//    implementation ("org.videolan.android:libvlc-all:3.3.4")
    implementation ("org.videolan.android:libvlc-all:3.6.0-eap14")

    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.4")
    implementation ("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
}