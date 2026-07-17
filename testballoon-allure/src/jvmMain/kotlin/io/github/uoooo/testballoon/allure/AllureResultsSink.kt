package io.github.uoooo.testballoon.allure

import java.io.File

/**
 * Destination for the files an [AllureExecutionReport] produces (result JSON and attachments).
 *
 * Implementations receive complete file contents keyed by their Allure file name (e.g.
 * `<uuid>-result.json`, `<uuid>-attachment.txt`) and decide where to store them. The default,
 * [FileSystemResultsSink], writes into a local directory; instrumented (device) tests substitute
 * a sink which stores results where the host can retrieve them.
 */
public fun interface AllureResultsSink {
    public fun write(fileName: String, content: ByteArray)
}

/**
 * An [AllureResultsSink] writing each file into [resultsDirectory] on the local file system.
 */
public class FileSystemResultsSink(resultsDirectory: String) : AllureResultsSink {
    private val directory = File(resultsDirectory)

    override fun write(fileName: String, content: ByteArray) {
        directory.mkdirs()
        File(directory, fileName).writeBytes(content)
    }
}
