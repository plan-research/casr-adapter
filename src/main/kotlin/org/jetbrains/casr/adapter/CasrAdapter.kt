package org.jetbrains.casr.adapter

import java.io.File
import java.io.InputStream
import java.nio.file.Files

object CasrAdapter {
    init {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val platform = when {
            osName.contains("win") && arch.contains("aarch64") -> "aarch64-pc-windows-gnu"
            osName.contains("win") -> "x86_64-pc-windows-gnu"
            osName.contains("mac") && arch.contains("aarch64") -> "aarch64-apple-darwin"
            osName.contains("mac") -> "x86_64-apple-darwin"
            osName.contains("nix") || osName.contains("nux") -> if (arch.contains("aarch64")) {
                "aarch64-unknown-linux-gnu"
            } else {
                "x86_64-unknown-linux-gnu"
            }

            else -> throw UnsupportedOperationException("Unsupported combination of OS: $osName and Arch: $arch")
        }
        val libName = System.mapLibraryName("casr_adapter")
        val libPath = "$platform-$libName"

        val tempLib = extractLibrary(libPath)
        System.load(tempLib.absolutePath)
    }

    private fun extractLibrary(resourcePath: String): File {
        val inputStream: InputStream = CasrAdapter::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Library not found in JAR: $resourcePath")

        val tempFile = Files.createTempFile("native-lib", ".dll").toFile()
        tempFile.deleteOnExit()

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    external fun parseAndClusterStackTraces(rawStacktraces: List<String>): List<Int>
}
