import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime

plugins {
    id("com.github.ben-manes.versions") version "0.53.0"
}

// Configure the dependencyUpdates task to emit JSON so downstream parsing works
tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

// Register a custom task that runs dependencyUpdates, parses the report, and optionally creates a PR branch
tasks.register("updateDependencies") {
    group = "dependency management"
    description = "Run dependencyUpdates, parse report, and optionally prepare a PR branch."

    // Ensure the plugin-provided task runs first
    dependsOn("dependencyUpdates")

    doLast {
        println("🔍 dependencyUpdates finished. Parsing report…")

        val jsonReport = File(buildDir, "dependencyUpdates/report.json")
        val txtReport = File(buildDir, "dependencyUpdates/report.txt")
        val reportFile = when {
            jsonReport.exists() -> jsonReport
            txtReport.exists() -> txtReport
            else -> throw GradleException("dependencyUpdates report not found in ${jsonReport.parentFile?.absolutePath}")
        }

        // Parse report.json to extract outdated dependencies
        val report = if (reportFile.extension == "json") {
            JsonSlurper().parse(reportFile) as Map<*, *>
        } else {
            // Fallback when only text report exists
            mapOf("outdated" to mapOf("dependencies" to emptyList<Map<String, Any?>>()))
        }
        val outdatedSection = report["outdated"] as? Map<*, *>
        val outdated = (outdatedSection?.get("dependencies") as? List<Map<*, *>>).orEmpty()

        println("📦 Outdated dependencies: ${outdated.size}")
        outdated.take(20).forEach {
            val group = it["group"]
            val name = it["name"]
            val current = it["version"]
            val available = (it["available"] as? Map<*, *>)?.get("release")
            println(" - $group:$name $current -> $available")
        }

        // Path to version catalog
        val tomlFile = File(rootDir, "gradle/libs.versions.toml")
        if (!tomlFile.exists()) {
            println("ℹ️ libs.versions.toml not found at ${tomlFile.absolutePath}. Skipping auto-update.")
            return@doLast
        }

        if (outdated.isEmpty()) {
            println("✅ No dependency updates found.")
            return@doLast
        }

        // Optionally create a PR branch
        try {
            // Ensure we are inside a Git repository
            exec { commandLine("git", "rev-parse", "--is-inside-work-tree") }

            val branchName = "update-dependencies-${LocalDateTime.now().toLocalDate()}"

            // 1️⃣ Check if the local branch already exists
            val existingBranches = ByteArrayOutputStream()
            exec {
                commandLine("git", "branch", "--list", branchName)
                standardOutput = existingBranches
                isIgnoreExitValue = true
            }
            if (existingBranches.toString().trim().isNotEmpty()) {
                println("ℹ️ Local branch '$branchName' exists. Deleting it first…")
                exec {
                    commandLine("git", "branch", "-D", branchName)
                }
            }

            // 2️⃣ Create a new branch
            exec {
                commandLine("git", "checkout", "-b", branchName)
            }

            // Stage the report file for reference
            exec {
                commandLine("git", "add", reportFile.absolutePath)
            }

            // Commit the report file (ignore if no changes)
            exec {
                isIgnoreExitValue = true
                commandLine(
                    "git",
                    "commit",
                    "-m",
                    "chore: dependencyUpdates report (no automatic bumps)"
                )
            }

            // Push the branch to origin (ignore if no remote configured)
            exec {
                isIgnoreExitValue = true
                commandLine("git", "push", "-u", "origin", branchName)
            }

            println("🚀 Branch '$branchName' prepared. Review report and update versions as needed.")

        } catch (e: Exception) {
            println("⚠️ Git operations skipped: ${e.message}")
        }
    }
}
