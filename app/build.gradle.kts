import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room3)
    alias(libs.plugins.roborazzi)
}

// Release signing (ADR 0005): the keystore and its properties live outside git. Create them once
// with scripts/generate-release-keystore.sh. Without keystore.properties the release APK is unsigned.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
}

android {
    namespace = "dev.alializadeh.orbit"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.alializadeh.orbit"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the real app without touching its data (ADR 0005).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

    androidResources {
        // Generates the locale config (en + fa) from the res/values-* folders; see res/resources.properties.
        generateLocaleConfig = true
    }

    lint {
        // Copy deck: English and Persian always ship together.
        error += listOf("MissingTranslation", "ExtraTranslation")
        // Orbit ships sideloaded APKs, never App Bundles, so per-app locale switching needs no
        // Play Core language downloads.
        disable += "AppBundleLocaleChanges"
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    outputDir = layout.projectDirectory.dir("src/test/screenshots")
}

// Room DAO tests run on the plain JVM: unit tests get sqlite-bundled's JVM artifact (host natives)
// instead of the Android AAR, which ships the same classes with Android-only natives.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "androidx.sqlite", module = "sqlite-bundled-android")
}

// The third-party modules the release APK ships (group:name, one per line), for LicencesTest:
// Settings → About → Open-source licences must cover every one of them.
val releaseRuntimeModules = layout.buildDirectory.file("orbit/release-runtime-modules.txt")
val writeReleaseRuntimeModules = tasks.register("writeReleaseRuntimeModules") {
    val modules = configurations.named("releaseRuntimeClasspath").flatMap { classpath ->
        classpath.incoming.resolutionResult.rootComponent.map { root ->
            val found = sortedSetOf<String>()
            val seen = HashSet<ResolvedComponentResult>()
            fun walk(component: ResolvedComponentResult) {
                if (!seen.add(component)) return
                (component.id as? ModuleComponentIdentifier)?.let { found += "${it.group}:${it.module}" }
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
            }
            walk(root)
            found.joinToString("\n")
        }
    }
    inputs.property("modules", modules)
    val output = releaseRuntimeModules
    outputs.file(output)
    doLast { output.get().asFile.writeText(modules.get() + "\n") }
}

tasks.withType<Test>().configureEach {
    dependsOn(writeReleaseRuntimeModules)
    inputs.file(releaseRuntimeModules).withPropertyName("releaseRuntimeModules").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("orbit.releaseRuntimeModules", releaseRuntimeModules.get().asFile.absolutePath)
    // Robolectric 4.17 at SDK 37 reaches into FileDescriptor internals (ApplicationSharedMemory),
    // which JDK 17+ hides unless exported.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    // The age CLI interop test runs only when the CLI is available; make that an input so a run
    // with ORBIT_AGE_CLI set is never skipped as up to date after a run without it.
    inputs.property("orbitAgeCli", providers.environmentVariable("ORBIT_AGE_CLI").orElse(""))
    // DatabaseMigrationTest reads the exported Room schemas.
    inputs.dir(layout.projectDirectory.dir("schemas")).withPropertyName("roomSchemas").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.materialkolor)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kage)

    // Device-bound state (ADR 0006): a typed DataStore with a kotlinx-serialization JSON serializer.
    implementation(libs.androidx.datastore)
    // The phone's screen lock as the system prompt (lock/ScreenLock: the Backup passphrase now,
    // App lock in slice 7), and Coil for photos (no network artifact).
    implementation(libs.androidx.biometric)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    // Robolectric at SDK 37 needs the current androidx.test stack; Compose's ui-test pulls an older
    // Espresso whose input injection breaks on Android 17.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.espresso.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
