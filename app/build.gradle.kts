import com.android.build.api.dsl.ApplicationExtension
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    id("ktlint-convention")
}

// ktlint configuration
ktlint {
    android.set(true)
    outputColorName.set("RED")
}

// Load version configuration from version.properties
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    throw GradleException("version.properties file not found at ${versionPropsFile.absolutePath}")
}

// Load per-customer distribution configuration. Kept separate from
// version.properties because that file belongs to the release CI.
val distributionPropsFile = rootProject.file("distribution.properties")
val distributionProps = Properties()
if (distributionPropsFile.exists()) {
    distributionProps.load(FileInputStream(distributionPropsFile))
}
val silentApplicationId =
    distributionProps.getProperty("silentApplicationId")?.trim().orEmpty()
val silentApplicationIdPattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
val requireSilent =
    gradle.startParameter.taskNames.any { it.contains("Silent", ignoreCase = true) }

// The debug menu password.
//
// The plaintext lives in local.properties, which is gitignored, and never
// reaches the repository or the APK. Only its salted SHA-256 goes into
// BuildConfig, so the shipped binary carries something that cannot be turned
// back into the password.
//
// The salt is committed on purpose. It is not a secret, and it has to stay
// stable across releases: DebugAuth re-salts the password someone types before
// comparing it to the stored hash, so a salt that changed under them would
// lock out anyone who had already set their own password.
val debugPasswordSalt =
    providers.gradleProperty("pigeonpost.debug.password.salt").get().trim()

/**
 * Same construction the app uses in DebugAuth: SHA-256 over salt + password,
 * lowercase hex. Both sides have to agree or nobody can ever unlock the menu.
 */
fun debugPasswordHash(
    salt: String,
    password: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt.toByteArray(Charsets.UTF_8))
    digest.update(password.toByteArray(Charsets.UTF_8))
    // Masked to unsigned so the digits match DebugAuth.toHex byte for byte.
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

val localPropsFile = rootProject.file("local.properties")
val localProps = Properties()
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
val configuredDebugPassword =
    localProps.getProperty("pigeonpost.debug.password")?.trim().orEmpty()

val debugPassword =
    if (configuredDebugPassword.isNotEmpty()) {
        configuredDebugPassword
    } else {
        // Falling back to a hardcoded default would put a known password in
        // every APK, which is the thing this is here to prevent. A random one
        // keeps CI builds working, and CI has no use for the debug menu
        // anyway. It is printed so a developer can still get in.
        val generated = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val value = generated.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        logger.warn(
            "pigeonpost.debug.password is not set in local.properties. " +
                "A random debug menu password was generated for this build only: $value",
        )
        value
    }

val debugPasswordHash = debugPasswordHash(debugPasswordSalt, debugPassword)

extensions.configure<ApplicationExtension> {
    namespace = "com.octopus.pigeon.post"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        versionCode = versionProps["versionCode"].toString().toInt()
        versionName = versionProps["versionName"].toString()
        applicationId = "com.octopus.pigeon.post"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEBUG_PASSWORD_SALT", "\"$debugPasswordSalt\"")
        buildConfigField("String", "DEFAULT_DEBUG_PASSWORD_HASH", "\"$debugPasswordHash\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            // Without this, Studio picks the alphabetically first variant, which
            // is silentDebug. That is the wrong default for everyone working on
            // the app: it has no notifications and records nothing, so a run
            // looks broken rather than silent.
            isDefault = true
            // Shared code reads this on both flavours, so it has to exist on both.
            buildConfigField("boolean", "SILENT", "false")
        }
        create("silent") {
            dimension = "distribution"
            if (silentApplicationId.isEmpty()) {
                if (requireSilent) {
                    throw GradleException(
                        "distribution.properties has no silentApplicationId, so the silent " +
                            "flavour has no package name. Set it before building.",
                    )
                }
            } else {
                if (!silentApplicationIdPattern.matches(silentApplicationId)) {
                    throw GradleException(
                        "silentApplicationId \"$silentApplicationId\" is not a valid package " +
                            "name. Use a lowercase reverse-DNS root such as com.khfcjvm.snsjuhe.",
                    )
                }
                applicationId = silentApplicationId
            }
            buildConfigField("boolean", "SILENT", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Enable code obfuscation
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
        }
    }
    compileOptions {
        val javaVersion = JavaVersion.toVersion(libs.versions.java.get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // javaxMail conflict fixing
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md" // 可选
            // Log4j conflict fixing
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
        }
    }

    lint {
        // The silent flavour deliberately ships without POST_NOTIFICATIONS so that the
        // mandatory foreground-service notification is never displayed, while the
        // standard flavour declares it. Every notify() call lives in shared code, so
        // this check can only ever be right for one of the two variants and has to be
        // off for both.
        //
        // The real guarantee is the verifySilentNotificationManifest task below, which
        // asserts the merged manifest of the silent build instead of trusting a lint
        // warning that a future edit could silence.
        disable += "NotificationPermission"
    }
}

// Asserts the silent build's merged manifest really has no POST_NOTIFICATIONS
// declaration, and the standard build's really does. This replaces the lint check
// that was disabled above, and it fails the build rather than warning.
val verifySilentNotificationManifest by tasks.registering {
    group = "verification"
    description = "Verifies each flavour ends up with the intended notification permission"

    val standardManifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifest/standardDebug/processStandardDebugMainManifest/AndroidManifest.xml",
        )
    val silentManifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifest/silentDebug/processSilentDebugMainManifest/AndroidManifest.xml",
        )
    dependsOn("processStandardDebugMainManifest", "processSilentDebugMainManifest")
    inputs.file(standardManifest)
    inputs.file(silentManifest)

    doLast {
        val permission = "android.permission.POST_NOTIFICATIONS"
        val standard = standardManifest.get().asFile.readText()
        val silent = silentManifest.get().asFile.readText()

        if (!standard.contains(permission)) {
            throw GradleException(
                "The standard flavour must declare $permission, otherwise no notification " +
                    "can ever be shown. Check src/standard/AndroidManifest.xml.",
            )
        }
        if (silent.contains(permission)) {
            throw GradleException(
                "The silent flavour must not declare $permission: holding it would let " +
                    "Android display the foreground-service notification. Check " +
                    "src/silent/AndroidManifest.xml for a tools:node=\"remove\".",
            )
        }
        logger.lifecycle("Verified: standard declares POST_NOTIFICATIONS, silent does not.")
    }
}

tasks.named("check") {
    dependsOn(verifySilentNotificationManifest)
}

// Kotlin Gradle plugin configuration.
// This configures the project-level `kotlin` extension, so it belongs at the top level
// rather than nested inside the android block.
kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
    compilerOptions {
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
        )
    }
}

dependencies {

    implementation(project(":core:locale"))
    implementation(project(":core:logging"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Icons (Visibility/VisibilityOff) require material-icons-extended
    implementation(libs.androidx.compose.material.icons.extended)

    // ProcessPhoenix for app restart
    implementation(libs.process.phoenix)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Appcompat
    implementation(libs.androidx.appcompat)

    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)

    // JavaMail for email sending
    implementation(libs.javax.mail)
    implementation(libs.javax.activation)
    implementation(libs.zip4j)

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)

    // Room for database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Log4j for logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.api)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ktlint formatting is wired up by the "ktlint-convention" plugin applied above
