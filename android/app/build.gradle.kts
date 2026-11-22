import java.util.Properties

plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }

// Release signing secrets live outside the repository (default ~/.linksms-signing/keystore.properties,
// or the path in LINKSMS_SIGNING_PROPERTIES). Without them, release builds are left unsigned.
val signingProps = Properties().apply {
 val path = System.getenv("LINKSMS_SIGNING_PROPERTIES") ?: "${System.getProperty("user.home")}/.linksms-signing/keystore.properties"
 file(path).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
 namespace = "com.linksms.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.linksms.app"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.4.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
 signingConfigs {
  if (signingProps.containsKey("storeFile")) create("release") {
   storeFile = file(signingProps.getProperty("storeFile"))
   storePassword = signingProps.getProperty("storePassword")
   keyAlias = signingProps.getProperty("keyAlias")
   keyPassword = signingProps.getProperty("keyPassword")
  }
 }
 buildTypes {
  release {
   isMinifyEnabled = true
   isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   signingConfig = signingConfigs.findByName("release")
  }
 }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.fragment:fragment-ktx:1.8.5")
 implementation("androidx.biometric:biometric:1.1.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
