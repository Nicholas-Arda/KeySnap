plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

/**
 * Packages the :bridge module's standalone classes.dex as an app asset. The app writes this
 * payload to a location the shell UID can read, then launches it with app_process; it is never
 * loaded into the app's own process.
 */
abstract class PackageBridgeDexTask : DefaultTask() {
  @get:InputFiles
  abstract val bridgeDex: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun packageDex() {
    val outputDir = outputDirectory.get().asFile
    outputDir.mkdirs()
    val dex = bridgeDex.files.single { it.name.endsWith(".dex") }
    dex.copyTo(File(outputDir, "bridge.dex"), overwrite = true)
  }
}

val bridgeDexClasspath: Configuration = configurations.create("bridgeDexClasspath") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val packageBridgeDex = tasks.register<PackageBridgeDexTask>("packageBridgeDex") {
  bridgeDex.setFrom(bridgeDexClasspath)
  outputDirectory.set(layout.buildDirectory.dir("generated/bridgeAssets"))
}

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
      packageBridgeDex,
      PackageBridgeDexTask::outputDirectory,
    )
  }
}

android {
  namespace = "io.github.nicholasarda.keysnap"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.nicholasarda.keysnap"
    // Android 11. Below this, any app holding WRITE_EXTERNAL_STORAGE can rewrite the bridge's
    // config in Android/data and make the shell-UID bridge run its commands. API 30+ closes
    // that directory to other apps entirely. Wireless Debugging — the only way to start Expert
    // Mode — is an Android 11 feature anyway, so nothing below 30 could use the app's core.
    minSdk = 30
    targetSdk = 36
    versionCode = 12
    versionName = "1.1.2"

    val autostartStateProbe = project.hasProperty("autostartStateProbe")
    if (autostartStateProbe) {
      require(project.hasProperty("sideBySide")) { "The autostart state probe is for the .dev phone install only" }
    }
    testInstrumentationRunner = if (autostartStateProbe) {
      "com.example.service.adb.AdvancedAutostartStateInstrumentation"
    } else {
      "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  // Supplied by the environment so no keystore path or password is ever committed. Absent on a
  // normal dev machine, in which case release builds stay unsigned rather than failing.
  val keystore = System.getenv("ARDA_KEYSTORE")?.let(::file)?.takeIf { it.isFile }
  signingConfigs {
    if (keystore != null) {
      create("release") {
        storeFile = keystore
        storePassword = System.getenv("ARDA_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("ARDA_KEY_ALIAS")
        keyPassword = System.getenv("ARDA_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    // Phone tests next to the Play install, without wiping its data: -PsideBySide
    // Advanced Mode on in only one of the two installs at a time; the bridges share a name and folder.
    debug {
      if (project.hasProperty("sideBySide")) {
        applicationIdSuffix = ".dev"
        versionNameSuffix = "-dev"
      }
    }
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
      // Exercise R8/resource shrinking on the phone without replacing the Play install.
      // Reuse the dev signature so a debug .dev install can be updated without losing its data.
      if (project.hasProperty("sideBySide")) {
        applicationIdSuffix = ".dev"
        versionNameSuffix = "-dev-release"
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // PrivacyPolicyTest reads the published policy from outside this module. Without declaring it,
  // Gradle calls the test task up-to-date after that file changes and the drift check never runs.
  tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("docs/privacy-policy.md"))
      .withPropertyName("publishedPrivacyPolicy")
      .withPathSensitivity(PathSensitivity.RELATIVE)
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    resources.excludes += setOf(
      "META-INF/LICENSE.md",
      "META-INF/NOTICE.md",
      // Bouncy Castle ships post-quantum lookup tables and localized certificate-review messages
      // as resources; libadb only uses its AES/HKDF primitives and we only build one X.509 cert.
      "org/bouncycastle/pqc/**",
      "org/bouncycastle/x509/*.properties",
      "org/bouncycastle/pkix/*.properties",
    )
  }
}

dependencies {
  // Shared, framework-free key mapping and protocol constants. The app and the detached bridge
  // must derive identical key codes, so both compile against the same classes.
  implementation(project(":bridge"))
  bridgeDexClasspath(project(mapOf("path" to ":bridge", "configuration" to "bridgeDexElements")))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Plain moshi: adapters are KSP-generated, so the reflection artifact (and kotlin-reflect) is dead weight.
  implementation(libs.moshi)
  implementation(libs.billing.ktx)
  implementation(libs.androidx.fragment)
  implementation(libs.bcprov)
  implementation(libs.bcpkix)
  implementation(libs.conscrypt.android)
  implementation(libs.libadb.android) {
    // libadb 3.1.0 brings the legacy Bouncy Castle provider transitively.
    // The app uses the jdk18on family above, so keep only that provider family.
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
  }
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.conscrypt.openjdk.uber)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}
