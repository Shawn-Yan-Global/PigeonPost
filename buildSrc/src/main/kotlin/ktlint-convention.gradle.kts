/**
 * Shared ktlint auto-format configuration for all modules.
 *
 * Formatting runs automatically before building, assembling and testing, so nobody has to remember
 * to run ktlint by hand. The rules themselves live in `.editorconfig` at the repo root.
 *
 * A module applies the ktlint plugin itself (through the version catalog, so the version is
 * declared in one place) and then adds this plugin to wire the format task into the build:
 *
 * ```kotlin
 * plugins {
 *     alias(libs.plugins.ktlint)
 *     id("ktlint-convention")
 * }
 * ```
 */
plugins.withId("org.jlleitschuh.gradle.ktlint") {
    val formatTaskName = "ktlintFormat"

    // Format before building
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(formatTaskName)
    }

    // Format before assembling any variant (Debug/Release)
    tasks.matching { it.name.startsWith("assemble") }.configureEach {
        dependsOn(formatTaskName)
    }

    // Format before running tests
    tasks.matching { it.name.startsWith("test") }.configureEach {
        dependsOn(formatTaskName)
    }
}
