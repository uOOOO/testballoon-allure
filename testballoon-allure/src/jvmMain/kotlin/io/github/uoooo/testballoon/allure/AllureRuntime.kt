package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.Test
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class PendingAttachment(val name: String, val type: String, val content: ByteArray, val fileExtension: String)

internal class PendingParameter(val name: String, val value: String)

/** A runtime step recorded by [allureStep], carrying real start/stop timing and its own status. */
internal class PendingStep(val name: String, val startMs: Long) {
    var stopMs: Long = startMs
    var failure: Throwable? = null
    val children = mutableListOf<PendingStep>()
    val attachments = mutableListOf<PendingAttachment>()
    val parameters = mutableListOf<PendingParameter>()
}

/** Everything recorded at runtime for one test. */
internal class TestRuntimeRecord {
    val rootSteps = mutableListOf<PendingStep>()
    val attachments = mutableListOf<PendingAttachment>()
    val parameters = mutableListOf<PendingParameter>()
}

/**
 * Buffers runtime data (steps, attachments, parameters) produced inside test bodies until
 * [AllureExecutionReport] drains it into that test's result. Keyed by the test element's path string,
 * which is available both in the test body (via [Test.ExecutionScope]) and in the report (via the
 * finished event's element). Access is guarded by [lock] so concurrent test bodies and the report
 * stay safe. The tree returned by [drain] is still reachable through captured [PendingStep]
 * references, so runtime APIs must not be invoked from coroutines that outlive their test (the
 * framework's structured concurrency guarantees this for supported usage).
 */
internal object AllureRuntimeBuffer {
    private val lock = Any()
    private val records = mutableMapOf<String, TestRuntimeRecord>()

    fun addStep(key: String, parent: PendingStep?, step: PendingStep): Unit = synchronized(lock) {
        if (parent != null) {
            parent.children.add(step)
        } else {
            records.getOrPut(key) { TestRuntimeRecord() }.rootSteps.add(step)
        }
    }

    fun addAttachment(key: String, step: PendingStep?, attachment: PendingAttachment): Unit = synchronized(lock) {
        if (step != null) {
            step.attachments.add(attachment)
        } else {
            records.getOrPut(key) { TestRuntimeRecord() }.attachments.add(attachment)
        }
    }

    fun addParameter(key: String, step: PendingStep?, parameter: PendingParameter): Unit = synchronized(lock) {
        if (step != null) {
            step.parameters.add(parameter)
        } else {
            records.getOrPut(key) { TestRuntimeRecord() }.parameters.add(parameter)
        }
    }

    fun drain(key: String): TestRuntimeRecord? = synchronized(lock) {
        records.remove(key)
    }
}

/** Coroutine context element carrying the innermost active [allureStep]. */
internal class AllureStepContext(val step: PendingStep) : AbstractCoroutineContextElement(AllureStepContext) {
    companion object Key : CoroutineContext.Key<AllureStepContext>
}

/**
 * Records a nested Allure step around [body], with its own timing and status. Steps nest when called
 * inside another [allureStep]. On failure, the step (and each enclosing step) is marked failed/broken
 * and the exception propagates. Recorded data is consumed by an [AllureExecutionReport]; without one
 * configured, it stays buffered (unused) until the test process exits.
 */
public suspend fun <T> Test.ExecutionScope.allureStep(name: String, body: suspend () -> T): T {
    RobolectricSandboxGuard.ensurePortable()
    val step = PendingStep(name, System.currentTimeMillis())
    AllureRuntimeBuffer.addStep(
        testElementPath.toString(),
        currentCoroutineContext()[AllureStepContext]?.step,
        step
    )
    try {
        return withContext(AllureStepContext(step)) { body() }
    } catch (throwable: Throwable) {
        step.failure = throwable
        throw throwable
    } finally {
        step.stopMs = System.currentTimeMillis()
    }
}

/**
 * Attaches text [content] to the current step, or to the test result when called outside [allureStep].
 * [fileExtension] optionally names the attachment file's extension (with or without the leading dot),
 * like allure-java's `Allure.attachment`; without it, the file is written without an extension.
 * Recorded data is consumed by an [AllureExecutionReport]; without one configured, it stays buffered
 * (unused) until the test process exits.
 */
public suspend fun Test.ExecutionScope.allureAttachment(
    name: String,
    content: String,
    type: String = "text/plain",
    fileExtension: String = ""
) {
    allureAttachment(name, content.encodeToByteArray(), type, fileExtension)
}

/**
 * Attaches binary [content] to the current step, or to the test result when called outside
 * [allureStep]. [fileExtension] optionally names the attachment file's extension (with or without
 * the leading dot), like allure-java's `Allure.attachment`; without it, the file is written without
 * an extension. Recorded data is consumed by an [AllureExecutionReport]; without one configured,
 * it stays buffered (unused) until the test process exits.
 */
public suspend fun Test.ExecutionScope.allureAttachment(
    name: String,
    content: ByteArray,
    type: String,
    fileExtension: String = ""
) {
    RobolectricSandboxGuard.ensurePortable()
    AllureRuntimeBuffer.addAttachment(
        testElementPath.toString(),
        currentCoroutineContext()[AllureStepContext]?.step,
        PendingAttachment(name, type, content, fileExtension)
    )
}

/**
 * Records an Allure parameter on the current step, or on the test result when called outside
 * [allureStep]. Recorded data is consumed by an [AllureExecutionReport]; without one configured,
 * it stays buffered (unused) until the test process exits.
 */
public suspend fun Test.ExecutionScope.allureParameter(name: String, value: String) {
    RobolectricSandboxGuard.ensurePortable()
    AllureRuntimeBuffer.addParameter(
        testElementPath.toString(),
        currentCoroutineContext()[AllureStepContext]?.step,
        PendingParameter(name, value)
    )
}
