package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestElement
import de.infix.testBalloon.framework.core.parameter
import de.infix.testBalloon.framework.shared.internal.Constants
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalApi

/** Allure severity levels, mirroring Allure's severity label values. */
public enum class AllureSeverity {
    BLOCKER,
    CRITICAL,
    NORMAL,
    MINOR,
    TRIVIAL
    ;

    internal val labelValue: String get() = name.lowercase()
}

/**
 * An Allure tracking link. [url] may be null for any [type], in which case [AllureExecutionReport]
 * resolves it via the `allure.link.<type>.pattern` system property (`{}` is replaced with [name]),
 * like the allure-java adapters do.
 */
internal data class AllureLink(val name: String, val url: String? = null, val type: String = "custom")

/**
 * Allure metadata attached to a test element.
 *
 * Attach it via [TestConfig.allure]; child elements inherit it. Scalar fields set on a child
 * override inherited values, list fields append, flags combine with OR.
 */
internal data class AllureMetadata(
    val epic: String? = null,
    val feature: String? = null,
    val stories: List<String> = emptyList(),
    val parentSuite: String? = null,
    val suite: String? = null,
    val subSuite: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val descriptionHtml: String? = null,
    val severity: AllureSeverity? = null,
    val owner: String? = null,
    val tags: List<String> = emptyList(),
    val labels: List<Pair<String, String>> = emptyList(),
    val links: List<AllureLink> = emptyList(),
    val flaky: Boolean = false,
    val muted: Boolean = false
) : TestElement.KeyedParameter(Key) {
    companion object {
        internal val Key: Key<AllureMetadata> = object : Key<AllureMetadata> {}
    }

    internal fun mergedInto(inherited: AllureMetadata?): AllureMetadata = if (inherited == null) {
        this
    } else {
        AllureMetadata(
            epic = epic ?: inherited.epic,
            feature = feature ?: inherited.feature,
            stories = inherited.stories + stories,
            parentSuite = parentSuite ?: inherited.parentSuite,
            suite = suite ?: inherited.suite,
            subSuite = subSuite ?: inherited.subSuite,
            displayName = displayName ?: inherited.displayName,
            description = description ?: inherited.description,
            descriptionHtml = descriptionHtml ?: inherited.descriptionHtml,
            severity = severity ?: inherited.severity,
            owner = owner ?: inherited.owner,
            tags = inherited.tags + tags,
            labels = inherited.labels + labels,
            links = inherited.links + links,
            flaky = inherited.flaky || flaky,
            muted = inherited.muted || muted
        )
    }
}

/**
 * The resolved set of report-path-hidden suites, keyed by their raw internal paths. Each declaring
 * suite appends its own path to the inherited set, so a test's resolved parameter lists exactly its
 * hidden ancestors; [AllureExecutionReport] maps them to path depths and skips those segments.
 */
internal class AllureHiddenPathNodes(internal val rawPaths: Set<String>) : TestElement.KeyedParameter(Key) {
    companion object {
        internal val Key: Key<AllureHiddenPathNodes> = object : Key<AllureHiddenPathNodes> {}
    }
}

/** Builder for [AllureMetadata], used by [TestConfig.allure]. All members are optional. */
public class AllureMetadataBuilder internal constructor() {
    private var metadata = AllureMetadata()

    internal var hidesInReportPath: Boolean = false
        private set

    /** Sets the Behaviors-view top-level group (the epic > feature > story tree). */
    public fun epic(value: String) {
        metadata = metadata.copy(epic = value)
    }

    /** Sets the Behaviors-view second-level group. */
    public fun feature(value: String) {
        metadata = metadata.copy(feature = value)
    }

    /** Adds Behaviors-view third-level entries; a test may belong to several stories. */
    public fun story(vararg values: String) {
        metadata = metadata.copy(stories = metadata.stories + values)
    }

    /**
     * Sets the Suites-view `parentSuite` label. Declaring any of [parentSuite]/[suite]/[subSuite]
     * replaces the automatically derived Suites-view labels entirely (like allure-pytest's suite
     * decorators override its module-derived defaults).
     */
    public fun parentSuite(value: String) {
        metadata = metadata.copy(parentSuite = value)
    }

    /** Sets the Suites-view `suite` label. See [parentSuite] for the override semantics. */
    public fun suite(value: String) {
        metadata = metadata.copy(suite = value)
    }

    /** Sets the Suites-view `subSuite` label. See [parentSuite] for the override semantics. */
    public fun subSuite(value: String) {
        metadata = metadata.copy(subSuite = value)
    }

    /**
     * Overrides the result's display name (the list entry in every report tab), like
     * allure-junit4's method-level `@DisplayName`. Structure is untouched: fullName, the step
     * chain, the `testMethod` label and the history id keep using the declared test name.
     * Declaring it on a suite fails fast — a suite has no result of its own to rename; to
     * rename a group in the Suites view (allure-junit4's class-level `@DisplayName` role),
     * use [suite]/[parentSuite]/[subSuite] instead.
     */
    public fun displayName(value: String) {
        metadata = metadata.copy(displayName = value)
    }

    /**
     * Omits this suite from the reported path: fullName, the step chain and the history/tracking
     * ids skip its level, as if its children were declared directly in its parent. Registration,
     * execution and metadata inheritance are unchanged, and hiding applies to this suite only —
     * never to its children.
     *
     * Use it on structural wrapper suites that are implementation detail, e.g. a sandbox wrapper
     * hosting externally defined content — allure-junit4 equally keeps wrapper suites
     * (`@RunWith(Suite.class)`) out of its reports. Declaring it on a test or at the top level
     * fails fast: a test is the path leaf, and the top-level suite carries the report's
     * class-like roles (package, testClass and the derived suite label).
     */
    public fun hideInReportPath() {
        hidesInReportPath = true
    }

    /** Sets the plain-text body shown in the test's detail view. */
    public fun description(text: String) {
        metadata = metadata.copy(description = text)
    }

    /** Sets the HTML body shown in the test's detail view. */
    public fun descriptionHtml(html: String) {
        metadata = metadata.copy(descriptionHtml = html)
    }

    /** Sets the severity shown in the detail view and the severity widget; absent means normal. */
    public fun severity(value: AllureSeverity) {
        metadata = metadata.copy(severity = value)
    }

    /** Sets the owner shown in the test's detail view. */
    public fun owner(value: String) {
        metadata = metadata.copy(owner = value)
    }

    /** Adds tags used by the report's filtering and search. */
    public fun tag(vararg values: String) {
        metadata = metadata.copy(tags = metadata.tags + values)
    }

    /**
     * Adds a raw label. Grouping views read specific names (the suite trio, epic/feature/story);
     * any other name is informational and searchable only.
     */
    public fun label(name: String, value: String) {
        metadata = metadata.copy(labels = metadata.labels + (name to value))
    }

    /**
     * Adds a tracking link, shown in the test's detail view. Without [url], the URL is resolved via the
     * `allure.link.<type>.pattern` system property (`{}` is replaced with [name]).
     */
    public fun link(name: String, url: String? = null, type: String = "custom") {
        metadata = metadata.copy(links = metadata.links + AllureLink(name, url, type))
    }

    /** Adds an issue link. Without [url], the URL comes from `allure.link.issue.pattern`. */
    public fun issue(id: String, url: String? = null) {
        link(id, url, "issue")
    }

    /** Adds a TMS link. Without [url], the URL comes from `allure.link.tms.pattern`. */
    public fun tmsLink(id: String, url: String? = null) {
        link(id, url, "tms")
    }

    /** Sets the `flaky` flag in the test result's Allure statusDetails. */
    public fun flaky() {
        metadata = metadata.copy(flaky = true)
    }

    /** Sets the `muted` flag in the test result's Allure statusDetails. */
    public fun muted() {
        metadata = metadata.copy(muted = true)
    }

    internal fun build(): AllureMetadata = metadata
}

/**
 * Attaches [AllureMetadata] to this test configuration. Child elements inherit it; the metadata
 * configured here merges field-wise into inherited metadata (scalars override, lists append).
 * List entries repeated across hierarchy levels are emitted repeatedly (Allure dedups tags in
 * the UI), matching JUnit4 class+method annotation behavior.
 *
 * ```
 * testConfig = TestConfig.allure {
 *     epic("Shop")
 *     feature("Checkout")
 *     story("Checkout → Receipt")
 *     severity(AllureSeverity.CRITICAL)
 * }
 * ```
 */
public fun TestConfig.allure(configure: AllureMetadataBuilder.() -> Unit): TestConfig {
    // A sandbox-loaded copy would record metadata under a duplicated key the report cannot see.
    RobolectricSandboxGuard.ensurePortable()
    val builder = AllureMetadataBuilder().apply(configure)
    val configured = builder.build()
    // The parameter lambda's receiver is the element this config is attached to. The declaration
    // parameter is attached first, so its site check runs before the hidden-path accumulation.
    val config = parameter(AllureMetadata.Key) { inherited ->
        checkDeclarationSite(configured, builder.hidesInReportPath)
        configured.mergedInto(inherited)
    }
    if (!builder.hidesInReportPath) return config
    return config.parameter(AllureHiddenPathNodes.Key) { inherited ->
        AllureHiddenPathNodes((inherited?.rawPaths ?: emptySet()) + testElementPath.toString())
    }
}

/**
 * The declaration-site rules in one place, mirroring allure-junit4's role model: a test is the
 * method role (only it has a result of its own to rename), the top-level suite is the class role
 * (it feeds package/testClass and the derived suite label), and nested suites are structure
 * (only they can be hidden).
 */
private fun TestElement.checkDeclarationSite(configured: AllureMetadata, hidesInReportPath: Boolean) {
    check(configured.displayName == null || this is Test) {
        "allure { displayName(...) } applies to individual tests only, like allure-junit4's" +
            " method-level @DisplayName — but it was declared on '$this'." +
            " A suite has no result of its own to rename. To rename a group in the report's" +
            " Suites view (junit4's class-level @DisplayName role)," +
            " use suite()/parentSuite()/subSuite() instead."
    }
    if (!hidesInReportPath) return
    check(this !is Test) {
        "allure { hideInReportPath() } applies to nested suites only — but it was declared on" +
            " test '$this'. A test is the path leaf and cannot be hidden; to change its list" +
            " entry, use displayName() instead."
    }
    check(isNestedElement()) {
        "allure { hideInReportPath() } applies to nested suites only — but it was declared on" +
            " '$this'. The top level carries the report's class-like roles (package, testClass" +
            " and the derived suite label) and cannot be hidden."
    }
}

// The framework offers no public depth accessor, so this counts on the path's internal ID form
// (same reliance as AllureExecutionReport.pathSegments): only nested elements contain a separator.
@OptIn(TestBalloonInternalApi::class)
private fun TestElement.isNestedElement(): Boolean =
    testElementPath.toString().contains(Constants.INTERNAL_PATH_ELEMENT_SEPARATOR)

/** Convenience overload covering the common epic/feature/story case. */
public fun TestConfig.allure(epic: String, feature: String, vararg stories: String): TestConfig = allure {
    epic(epic)
    feature(feature)
    story(*stories)
}
