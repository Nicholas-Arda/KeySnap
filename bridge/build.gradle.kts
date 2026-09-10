import java.util.Properties

plugins {
  `java-library`
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

/**
 * The bridge runs inside `app_process` as the shell UID, so it must not carry the Kotlin
 * stdlib or any other runtime dependency: the shipped artifact is a single classes.dex that is
 * copied to /data/local/tmp. Framework classes are resolved against android.jar at compile time
 * only; the device supplies them at runtime.
 *
 * Everything below is resolved during configuration and captured as plain values so the dexing
 * task stays compatible with the configuration cache.
 */
val androidSdkDir: File = run {
  val fromLocalProperties = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("sdk.dir") }
  val path = fromLocalProperties
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME.")
  File(path)
}

/** The compile SDK directory name varies by installation (android-36, android-36.1, ...). */
val androidJarFile: File = File(androidSdkDir, "platforms").listFiles().orEmpty()
  .filter { it.isDirectory && File(it, "android.jar").isFile }
  .maxByOrNull { it.name }
  ?.let { File(it, "android.jar") }
  ?: error("No android.jar found under ${File(androidSdkDir, "platforms")}")

val d8Executable: File = run {
  val buildTools = File(androidSdkDir, "build-tools").listFiles().orEmpty()
    .filter { it.isDirectory }
    .maxByOrNull { it.name }
    ?: error("No build-tools found under ${File(androidSdkDir, "build-tools")}")
  val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
  File(buildTools, if (isWindows) "d8.bat" else "d8")
}

dependencies {
  compileOnly(files(androidJarFile))
  testImplementation(libs.junit)
  // android.jar only stubs org.json at compile time; a real implementation is needed to actually
  // parse JSON in tests. Test-only — the shipped classes.dex is built from main sources alone.
  testImplementation("org.json:json:20231013")
}

tasks.withType<Test>().configureEach {
  useJUnit()
}

val compiledClassesDir: Provider<Directory> = layout.buildDirectory.dir("classes/java/main")
val bridgeDexDir: Provider<Directory> = layout.buildDirectory.dir("bridgeDex")

/**
 * Produces the standalone classes.dex the app ships in its assets. `d8` is invoked directly
 * because this is a plain java-library and has no Android Gradle Plugin dexing step.
 */
abstract class DexBridgeTask : DefaultTask() {
  @get:InputDirectory
  abstract val classesDirectory: DirectoryProperty

  @get:InputFile
  abstract val androidJar: RegularFileProperty

  @get:InputFile
  abstract val d8: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:javax.inject.Inject
  abstract val execOperations: org.gradle.process.ExecOperations

  @TaskAction
  fun dex() {
    val outputDir = outputDirectory.get().asFile
    outputDir.mkdirs()
    val classFiles = classesDirectory.get().asFile.walkTopDown()
      .filter { it.isFile && it.extension == "class" }
      .map { it.absolutePath }
      .toList()
    check(classFiles.isNotEmpty()) { "No compiled bridge classes to dex" }

    execOperations.exec {
      // d8.bat/d8 shells out to `java` and refuses to run unless JAVA_HOME is set in its own
      // environment; the Gradle daemon's JVM doesn't propagate one automatically, so supply the
      // JDK the daemon itself is running on.
      environment("JAVA_HOME", System.getProperty("java.home"))
      commandLine(
        buildList {
          add(d8.get().asFile.absolutePath)
          add("--min-api")
          add("24")
          add("--lib")
          add(androidJar.get().asFile.absolutePath)
          add("--output")
          add(outputDir.absolutePath)
          addAll(classFiles)
        },
      )
    }
  }
}

val dexBridge = tasks.register<DexBridgeTask>("dexBridge") {
  dependsOn(tasks.named("classes"))
  classesDirectory.set(compiledClassesDir)
  androidJar.set(layout.projectDirectory.file(androidJarFile.absolutePath))
  d8.set(layout.projectDirectory.file(d8Executable.absolutePath))
  outputDirectory.set(bridgeDexDir)
}

/** Exposes bridge/build/bridgeDex/classes.dex so :app can copy it into its assets. */
val bridgeDexElements = configurations.create("bridgeDexElements") {
  isCanBeConsumed = true
  isCanBeResolved = false
}

artifacts {
  add(bridgeDexElements.name, dexBridge.flatMap { it.outputDirectory.file("classes.dex") })
}
