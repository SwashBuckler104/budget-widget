package budgetwidget.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal object Sdk {
    // Current stable AndroidX (Compose 1.12, core 1.19) requires compileSdk 37. targetSdk stays
    // at 36 as SCOPE.md §2 locks it; compileSdk has no runtime effect. docs/DECISIONS.md ADR-0007.
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
}

internal val JAVA_VERSION = JavaVersion.VERSION_17
internal val KOTLIN_JVM_TARGET = JvmTarget.JVM_17

/** The only project allowed to depend on the upstream parser (anti-corruption layer, SCOPE.md §5). */
private const val PARSER_PROJECT = ":parser-core"
private const val PARSER_CONSUMER = ":ingest"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal val Project.packageRoot: String
    get() = providers.gradleProperty("budgetwidget.packageRoot").get()

/** `:app` -> root, `:core:model` -> `<root>.core.model`, `:feature:settings` -> `<root>.feature.settings`. */
internal fun Project.moduleNamespace(): String {
    if (path == ":app") return packageRoot
    val suffix = path.removePrefix(":").split(':').joinToString(".") { it.replace('-', '_') }
    return "$packageRoot.$suffix"
}

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        namespace = moduleNamespace()
        compileSdk = Sdk.COMPILE
        defaultConfig.minSdk = Sdk.MIN
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
        configureLint(lint)
    }
    configureKotlin()
    enforceParserBoundary()
    // Compose/activity pull lifecycle 2.9.x transitively, whose bundled lint checks are
    // incompatible with AGP 9.5 lint (ObsoleteLintCustomCheck). Lifecycle artifacts align via
    // Gradle constraints, so one constraint lifts the whole family.
    dependencies.constraints.add("implementation", libs.library("androidx-lifecycle-runtime"))
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(KOTLIN_JVM_TARGET)
            allWarningsAsErrors.set(true)
        }
    }
}

internal fun configureLint(lint: Lint) {
    lint.apply {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Version-drift checks would turn every upstream release into a red CI run.
        // Dependabot proposes upgrades instead. OldTargetApi: targetSdk 36 is pinned by
        // SCOPE.md §2. See docs/DECISIONS.md (ADR-0006).
        informational += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "OldTargetApi",
        )
    }
}

/**
 * Fails configuration if a project other than [PARSER_CONSUMER] declares a dependency on
 * [PARSER_PROJECT]. `:ingest` uses `implementation`, so the parser never leaks transitively.
 */
internal fun Project.enforceParserBoundary() {
    if (path == PARSER_CONSUMER) return
    val owner = path
    configurations.configureEach {
        val configurationName = name
        dependencies.withType<ProjectDependency>().configureEach {
            if (path == PARSER_PROJECT) {
                throw GradleException(
                    "$owner depends on $PARSER_PROJECT via '$configurationName'. Only $PARSER_CONSUMER may " +
                        "use the upstream parser; go through its SmsParser interface instead.",
                )
            }
        }
    }
}
