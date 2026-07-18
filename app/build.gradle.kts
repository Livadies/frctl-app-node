plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseKeystorePath = providers.environmentVariable("FRCTL_KEYSTORE_PATH").orElse(providers.gradleProperty("FRCTL_KEYSTORE_PATH")).orNull
val releaseKeystorePassword = providers.environmentVariable("FRCTL_KEYSTORE_PASSWORD").orElse(providers.gradleProperty("FRCTL_KEYSTORE_PASSWORD")).orNull
val releaseKeyAlias = providers.environmentVariable("FRCTL_KEY_ALIAS").orElse(providers.gradleProperty("FRCTL_KEY_ALIAS")).orElse("key0").get()
val releaseKeyPassword = providers.environmentVariable("FRCTL_KEY_PASSWORD").orElse(providers.gradleProperty("FRCTL_KEY_PASSWORD")).orNull

android {
    namespace = "io.frctl.app"
    compileSdk = 35

    signingConfigs {
        if (releaseKeystorePath != null && releaseKeystorePassword != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "io.frctl.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${providers.gradleProperty("FRCTL_GITHUB_CLIENT_ID").orElse("").get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha03")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
