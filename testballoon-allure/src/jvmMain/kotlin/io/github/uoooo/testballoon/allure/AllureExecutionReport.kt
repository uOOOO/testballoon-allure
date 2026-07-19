package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestElement
import de.infix.testBalloon.framework.core.TestExecutionReport
import de.infix.testBalloon.framework.shared.internal.Constants
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalApi
import io.qameta.allure.AllureConstants
import io.qameta.allure.AllureLifecycle
import io.qameta.allure.AllureResultsWriter
import io.qameta.allure.internal.Allure2ModelJackson
import io.qameta.allure.model.Attachment
import io.qameta.allure.model.Label
import io.qameta.allure.model.Link
import io.qameta.allure.model.Parameter
import io.qameta.allure.model.Stage
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.StepResult
import io.qameta.allure.model.TestResult
import io.qameta.allure.model.TestResultContainer
import io.qameta.allure.util.ResultsUtils
import java.io.InputStream
import java.util.UUID
import java.util.WeakHashMap
import kotlin.time.ExperimentalTime

/**
 * A [TestExecutionReport] that writes one Allure result JSON per finished test to [sink].
 *
 * Each result is fully built and written within a single [add] call (schedule -> write), keyed by a unique
 * uuid and using no thread-local state. Concurrent [add] calls are therefore safe, including under parallel
 * dispatchers and the Robolectric sandbox thread.
 *
 * Configuring AllureExecutionReports at two hierarchy levels covering the same tests (e.g. via both
 * an [AllureTestSession] and a suite-level `executionReport`) fails fast when execution starts, as
 * every test would otherwise be recorded twice.
 */
public class AllureExecutionReport(private val sink: AllureResultsSink) : TestExecutionReport() {
    /** Writes results into [resultsDirectory] on the local file system. */
    public constructor(resultsDirectory: String) : this(FileSystemResultsSink(resultsDirectory))

    private companion object {
        val claimedEventsLock = Any()

        // The framework delivers the same Starting event instance to every report configured for an
        // element, so a second claim means two AllureExecutionReports overlap. Weak keys: entries
        // vanish with their events.
        val claimedStartingEvents = WeakHashMap<TestElement.Event, Unit>()
    }

    private val lifecycle = AllureLifecycle(SinkResultsWriter(sink))

    // ResultsUtils.createThreadLabel() resolves java.lang.management, which does not exist on Android
    // (NoClassDefFoundError on ART). Probe it once; where unavailable, fall back to allure-kotlin's
    // Android thread name format, "name(id)".
    private val threadLabelSupported: Boolean = runCatching { ResultsUtils.createThreadLabel() }.isSuccess

    @OptIn(ExperimentalTime::class)
    override suspend fun add(event: TestElement.Event) {
        if (event is TestElement.Event.Starting) {
            val alreadyClaimed =
                synchronized(claimedEventsLock) {
                    claimedStartingEvents.put(event, Unit) != null
                }
            check(!alreadyClaimed) {
                "Multiple AllureExecutionReports are configured for" +
                    " '${pathSegments(event.element.testElementPath.toString()).joinToString(" > ")}'" +
                    " (e.g. via both AllureTestSession and a suite-level executionReport)." +
                    " Configure exactly one, as every test would otherwise be recorded twice."
            }
            return
        }
        if (event !is TestElement.Event.Finished) return
        // The claim has served its purpose: remove it deterministically instead of waiting for GC
        // (weak keys remain the backstop for elements whose Finished event never arrives).
        synchronized(claimedEventsLock) { claimedStartingEvents.remove(event.startingEvent) }
        val element = event.element
        if (element !is Test) return

        val uuid = UUID.randomUUID().toString()
        // The un-escaped path chain identifies the test for Allure trend tracking (like JUnit4's
        // class+method) and is the single source for display identity: leaf name, fullName, labels
        // and the step chain all derive from it. rawPath (the internal form) remains only the
        // runtime buffer key.
        val rawPath = element.testElementPath.toString()
        // Suites declared hidden (hideInReportPath) drop out of the chain right here, so every
        // derived field skips them — like allure-junit4, where wrapper suites leave no trace.
        // Hidden entries are guaranteed ancestors of this test (parameters inherit down the tree
        // only), so depth indexing is sufficient.
        val hiddenDepths =
            element
                .testElementParameter(AllureHiddenPathNodes.Key)
                ?.rawPaths
                .orEmpty()
                .map { pathSegments(it).size - 1 }
        val segments = pathSegments(rawPath).filterIndexed { index, _ -> index !in hiddenDepths }
        val pathId = segments.joinToString(" > ")
        // Tracking keys (never displayed) hash a control-character join of the segments, matching
        // allure-junit4's md5 history id format, so a name containing " > " cannot collide with a
        // nested path. Displayed fields keep the readable " > " join.
        val trackingId = ResultsUtils.md5(segments.joinToString("\u001F"))
        val meta = element.testElementParameter(AllureMetadata.Key)
        // The display name (the list entry) may be overridden like allure-junit4's method-level
        // @DisplayName; structural fields (fullName, steps, testMethod, tracking ids) keep the
        // path leaf. Suite-level declarations are rejected at config time (TestConfig.allure).
        val displayName = meta?.displayName ?: segments.last()

        val status =
            when {
                !element.testElementIsEnabled -> Status.SKIPPED
                event.succeeded -> Status.PASSED
                event.throwable is AssertionError -> Status.FAILED
                else -> Status.BROKEN
            }

        val result =
            TestResult()
                .setUuid(uuid)
                .setName(displayName)
                .setFullName(pathId)
                .setHistoryId(trackingId)
                .setTestCaseId(trackingId)
                .setStatus(status)
                .setStart(event.startingEvent.instant.toEpochMilliseconds())
                .setStop(event.instant.toEpochMilliseconds())

        result.labels.add(ResultsUtils.createFrameworkLabel("testBalloon"))
        result.labels.add(ResultsUtils.createLanguageLabel("kotlin"))
        result.labels.add(ResultsUtils.createHostLabel())
        // Approximation: the report processes the Finished event on (or near) the thread that ran the
        // test, but unlike the JUnit adapters it does not observe the test body itself.
        result.labels.add(
            if (threadLabelSupported) {
                ResultsUtils.createThreadLabel()
            } else {
                Label().setName("thread").setValue(Thread.currentThread().let { "${it.name}(${it.id})" })
            }
        )

        val pathLevels = segments.dropLast(1)

        // Automatic structural labels mirror the JUnit adapters: the package-qualified top-level suite
        // takes the class-like roles (package drives the Packages view, which splits it on dots,
        // collapsing single-child runs), the leaf takes the method role.
        pathLevels.firstOrNull()?.let {
            result.labels.add(ResultsUtils.createPackageLabel(it))
            result.labels.add(ResultsUtils.createTestClassLabel(it))
        }
        segments.lastOrNull()?.let { result.labels.add(ResultsUtils.createTestMethodLabel(it)) }

        // Suites-view labels: the derived default is a single suite label carrying the top-level
        // suite — exactly what allure-junit4 emits (suite = class). Nesting is deliberately NOT
        // unfolded into parentSuite/suite/subSuite: with descriptive (e.g. BDD-style) level names
        // the trio turns sentences into group names; the nested structure stays available in
        // fullName and the step chain. Explicit values (TestConfig.allure { suite(...) }) replace
        // the derived label entirely — like allure-pytest, whose suite decorators override its
        // module-derived defaults. Allure labels are append-only, so replacement must happen here
        // at emission.
        if (meta != null && (meta.parentSuite ?: meta.suite ?: meta.subSuite) != null) {
            meta.parentSuite?.let { result.labels.add(ResultsUtils.createParentSuiteLabel(it)) }
            meta.suite?.let { result.labels.add(ResultsUtils.createSuiteLabel(it)) }
            meta.subSuite?.let { result.labels.add(ResultsUtils.createSubSuiteLabel(it)) }
        } else {
            pathLevels.firstOrNull()?.let { result.labels.add(ResultsUtils.createSuiteLabel(it)) }
        }

        meta?.let { m ->
            m.epic?.let { result.labels.add(ResultsUtils.createEpicLabel(it)) }
            m.feature?.let { result.labels.add(ResultsUtils.createFeatureLabel(it)) }
            m.stories.forEach { result.labels.add(ResultsUtils.createStoryLabel(it)) }
            m.severity?.let { result.labels.add(ResultsUtils.createSeverityLabel(it.labelValue)) }
            m.owner?.let { result.labels.add(ResultsUtils.createOwnerLabel(it)) }
            m.tags.forEach { result.labels.add(ResultsUtils.createTagLabel(it)) }
            m.labels.forEach { (labelName, labelValue) ->
                result.labels.add(Label().setName(labelName).setValue(labelValue))
            }
            m.description?.let { result.description = it }
            m.descriptionHtml?.let { result.descriptionHtml = it }
            m.links.forEach { result.links.add(it.toModelLink()) }
        }

        val flaky = meta?.flaky == true
        val muted = meta?.muted == true
        if (event.throwable != null || flaky || muted) {
            result.statusDetails =
                StatusDetails()
                    .setFlaky(flaky)
                    .setMuted(muted)
                    .apply {
                        event.throwable?.let { throwable ->
                            setMessage(throwable.message ?: throwable::class.simpleName)
                            setTrace(throwable.stackTraceToString())
                        }
                    }
        }

        // Steps mirror the suite/test nesting below the top-level suite (root → leaf): each level nests
        // inside the previous step's `steps`. Ancestor (suite) steps are marked PASSED ("entered this
        // context"); only the leaf (the test) carries the actual execution result — matching the Kotest
        // reporter's Context/Given/When = PASSED, Then = status convention.
        val chainNames = segments.drop(1)
        var stepContainer = result.steps
        chainNames.forEachIndexed { index, stepName ->
            val stepStatus = if (index == chainNames.lastIndex) status else Status.PASSED
            val step =
                StepResult()
                    .setName(stepName)
                    .setStatus(stepStatus)
                    // All steps share the test's timing — per-step timing is not available from the event.
                    .setStart(event.startingEvent.instant.toEpochMilliseconds())
                    .setStop(event.instant.toEpochMilliseconds())
            stepContainer.add(step)
            stepContainer = step.steps
        }

        // Drain everything recorded by the runtime APIs during this test body (same path key the body
        // saw): test-level attachments/parameters go onto the result, the runtime step tree grafts
        // under the innermost registration-time step.
        AllureRuntimeBuffer.drain(rawPath)?.let { runtime ->
            runtime.parameters.forEach {
                result.parameters.add(Parameter().setName(it.name).setValue(it.value))
            }
            runtime.attachments.forEach { result.attachments.add(writeAttachment(it)) }
            runtime.rootSteps.forEach { stepContainer.add(it.toStepResult()) }
        }

        lifecycle.scheduleTestCase(result)
        // scheduleTestCase() resets stage to SCHEDULED; set FINISHED afterwards on the same stored
        // reference so writeTestCase() serializes it correctly.
        result.setStage(Stage.FINISHED)
        lifecycle.writeTestCase(uuid)
    }

    /**
     * The framework offers no public accessor for path segments, so this splits the path's internal ID
     * form («root↘…↘leaf») and undoes its escaping (space→NBSP, '/'→'⧸'). Revisit once the framework
     * exposes path segments publicly.
     */
    @OptIn(TestBalloonInternalApi::class)
    private fun pathSegments(rawPath: String): List<String> = rawPath
        .removeSurrounding("«", "»")
        .split(Constants.INTERNAL_PATH_ELEMENT_SEPARATOR)
        .map { it.replace(Constants.ESCAPED_SPACE, ' ').replace('⧸', '/') }

    private fun AllureLink.toModelLink(): Link {
        val resolvedUrl = url ?: System.getProperty("allure.link.$type.pattern")?.replace("{}", name)
        return Link().setName(name).setUrl(resolvedUrl).setType(type)
    }

    private fun PendingStep.toStepResult(): StepResult {
        val stepResult =
            StepResult()
                .setName(name)
                .setStatus(
                    when (failure) {
                        null -> Status.PASSED
                        is AssertionError -> Status.FAILED
                        else -> Status.BROKEN
                    }
                ).setStart(startMs)
                .setStop(stopMs)
        failure?.let {
            stepResult.statusDetails = StatusDetails().setMessage(it.message ?: it::class.simpleName)
        }
        parameters.forEach { stepResult.parameters.add(Parameter().setName(it.name).setValue(it.value)) }
        attachments.forEach { stepResult.attachments.add(writeAttachment(it)) }
        children.forEach { stepResult.steps.add(it.toStepResult()) }
        return stepResult
    }

    private fun writeAttachment(pending: PendingAttachment): Attachment {
        // Mirrors AllureLifecycle.prepareAttachment: dot-normalize the caller-provided extension,
        // omit it when absent.
        val extension =
            pending.fileExtension
                .takeIf { it.isNotEmpty() }
                ?.let { if (it.startsWith('.')) it else ".$it" }
                .orEmpty()
        val fileName = "${UUID.randomUUID()}${AllureConstants.ATTACHMENT_FILE_SUFFIX}$extension"
        sink.write(fileName, pending.content)
        return Attachment()
            .setName(pending.name)
            .setType(pending.type)
            .setSource(fileName)
    }
}

/**
 * Adapts an [AllureResultsSink] to allure-java's [AllureResultsWriter], preserving the file naming
 * convention of its `FileSystemResultsWriter` (`<uuid>-result.json`, `<uuid>-container.json`) and its
 * serialization (the Allure2 Jackson mapper).
 */
private class SinkResultsWriter(private val sink: AllureResultsSink) : AllureResultsWriter {
    private val mapper = Allure2ModelJackson.createMapper()

    override fun write(testResult: TestResult) {
        val uuid = testResult.uuid ?: UUID.randomUUID().toString()
        sink.write("$uuid${AllureConstants.TEST_RESULT_FILE_SUFFIX}", mapper.writeValueAsBytes(testResult))
    }

    override fun write(testResultContainer: TestResultContainer) {
        val uuid = testResultContainer.uuid ?: UUID.randomUUID().toString()
        sink.write(
            "$uuid${AllureConstants.TEST_RESULT_CONTAINER_FILE_SUFFIX}",
            mapper.writeValueAsBytes(testResultContainer)
        )
    }

    override fun write(source: String, attachment: InputStream) {
        sink.write(source, attachment.use { it.readBytes() })
    }
}
