import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.Charset

plugins {
    kotlin("jvm") version "2.0.21"
}

group = "org.jetbrains"
version = "1.0.5"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(8)
}

tasks.getByName<KotlinCompile>("compileKotlin") {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }
}

val rustTargets = listOf(
    "x86_64-unknown-linux-gnu",
    "aarch64-unknown-linux-gnu",
    "x86_64-apple-darwin",
    "aarch64-apple-darwin",
    "x86_64-pc-windows-gnu",
    "aarch64-pc-windows-msvc"
)

fun findCargoPath(): String? {
    val command = if (System.getProperty("os.name").contains("Windows")) {
        listOf("where", "cargo")
    } else {
        listOf("which", "cargo")
    }

    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charset.defaultCharset()).readLine()
        process.waitFor()

        if (output.isNullOrBlank()) null else output
    } catch (_: Exception) {
        null
    }
}

val cargoPath = findCargoPath()

tasks.register<Exec>("buildRustLib") {
    workingDir = file("$projectDir/CasrAdapter")
    commandLine = listOf("bash", "build.sh") + rustTargets
    outputs.upToDateWhen {
        rustTargets.all { file("$projectDir/CasrAdapter/target/$it/release").exists() }
    }
}

fun File.listSharedLibs(): Array<File>? = listFiles { file ->
    file.extension in setOf("dylib", "so", "dll")
}

tasks.register("linkRustLib") {
    dependsOn("buildRustLib")
    doLast {
        val sourceDirs = rustTargets.map { file("$projectDir/CasrAdapter/target/$it/release") }
        val targetDir = file(projectDir.resolve("libs"))

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        sourceDirs.forEach { sourceDir ->
            if (!sourceDir.exists()) {
                throw GradleException("Source directory $sourceDir does not exist")
            }

            sourceDir.listSharedLibs()?.forEach { file ->
                val targetLink = targetDir.resolve("${file.parentFile.parentFile.name}-${file.name}")
                if (targetLink.exists()) {
                    targetLink.delete()
                }

                try {
                    Files.createSymbolicLink(targetLink.toPath(), file.toPath())
                } catch (_: UnsupportedOperationException) {
                    println("Warning: Symbolic links are not supported or failed to create. Falling back to file copy.")
                    Files.copy(file.toPath(), targetLink.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    throw GradleException("Failed to handle file ${file.name}", e)
                }
            }
        }
    }
}

tasks.jar {
    dependsOn("linkRustLib")
    projectDir.resolve("libs").listSharedLibs()?.forEach {
        from(it) {
            into("org/jetbrains/casr/adapter")
        }
    }
}

tasks.register<Exec>("cleanCargo") {
    workingDir = file("$projectDir/CasrAdapter")
    commandLine = listOf(cargoPath, "clean")
}

tasks.named("clean") {
    dependsOn("cleanCargo")
    doLast {
        val targetDir = file(projectDir.resolve("libs"))
        targetDir.listSharedLibs()?.forEach { it.delete() }
    }
}

pluginManager.apply("maven-publish")
project.pluginManager.withPlugin("maven-publish") {
    project.afterEvaluate {
        project.extensions.configure<PublishingExtension>("publishing") {
            publications {
                this@publications.create("runner", MavenPublication::class.java) {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()

                    from(components["java"])
                    projectDir.resolve("libs").listSharedLibs()?.forEach { file ->
                        artifact(file) {
                            classifier = file.nameWithoutExtension
                            extension = file.extension
                        }
                    }
                }
            }
            repositories {
                maven {
                    url = uri("https://maven.pkg.github.com/plan-research/kotlin-maven")
                    credentials {
                        username = project.findProperty("gpr.user")?.toString()
                            ?: System.getenv("MAVEN_REPOSITORY_LOGIN")
                        password = project.findProperty("gpr.token")?.toString()
                            ?: System.getenv("MAVEN_REPOSITORY_PASSWORD")
                    }
                }
            }
        }
    }
}
