plugins {

    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.compose)

}



android {

    namespace = "com.mtp.mytoolsproject"

    compileSdk = 37



    defaultConfig {

        applicationId = "com.mtp.mytoolsproject"

        minSdk = 26

        targetSdk = 37

        versionCode = 1

        versionName = "1.0"



        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }



    buildTypes {

        release {

            isMinifyEnabled = false

            proguardFiles(

                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"

            )

        }

    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_11

        targetCompatibility = JavaVersion.VERSION_11

    }

    buildFeatures {

        compose = true

    }

}

kotlin {

    compilerOptions {

        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)

    }

}



dependencies {

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.appcompat)

    implementation(libs.material)

    implementation(libs.androidx.biometric)

    implementation("com.google.zxing:core:3.5.3")

    // Segurança melhorada
    implementation(libs.androidx.security.crypto)
    
    // Logging estruturado
    implementation(libs.timber)
    
    // Detecção de root
    implementation(libs.scotty.rootbeer)



    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)



    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

}