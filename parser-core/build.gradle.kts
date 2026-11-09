import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Thin wrapper that compiles the pinned PennyWise parser-core sources (AGPL-3.0) for the JVM.
// Nothing under third_party/ is ever edited; see docs/PARSER_UPGRADE.md and docs/DECISIONS.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

val upstream: Directory = rootProject.layout.projectDirectory.dir("third_party/pennywise/parser-core")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Upstream targets JDK 21; we consume it from Android, so hold it to the JDK 17 API.
        freeCompilerArgs.add("-Xjdk-release=17")
        // Upstream code: warnings are reported (docs/upstream/) but cannot be fixed here.
        allWarningsAsErrors = false
    }
    sourceSets {
        main {
            kotlin.srcDir(upstream.dir("src/main/kotlin"))
            kotlin.srcDir(upstream.dir("src/commonMain/kotlin"))
        }
        test {
            kotlin.srcDir(upstream.dir("src/test/kotlin"))
        }
    }
}

sourceSets {
    test {
        resources.srcDir(upstream.dir("src/test/resources"))
    }
}

dependencies {
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/** Upstream test classes covering the banks we ship for (SCOPE.md §2). Each must run and pass. */
val ourBankTestClasses = listOf(
    "KotakBankParserTest",
    "com.pennywiseai.parser.core.bank.FederalBankParserTest",
    "FederalBankOutgoingParserTest",
    "FederalBankMandateParsingTest",
    "com.pennywiseai.parser.core.bank.JupiterBankParserTest",
    "com.pennywiseai.parser.core.bank.SliceParserTest",
    "com.pennywiseai.parser.core.bank.AxisBankParserTest",
    "SBIBankParserTest",
    "com.pennywiseai.parser.core.bank.HDFCBankParserTest",
    "com.pennywiseai.parser.core.bank.HDFCMandateParsingTest",
)

val testResultsDir = layout.buildDirectory.dir("test-results/test")
val submoduleDir: File = rootProject.file("third_party/pennywise")

val verifyOurBankTestsRan = tasks.register("verifyOurBankTestsRan") {
    description = "Fails unless every upstream test class for our banks ran at least one test, all passing."
    group = "verification"
    inputs.dir(testResultsDir)
    val expected = ourBankTestClasses
    val resultsDir = testResultsDir
    // `./gradlew :parser-core:test --tests X` is a deliberate partial run; don't fail it.
    val filteredRun = gradle.startParameter.taskNames.any { it == "--tests" || it.startsWith("--tests=") }
    onlyIf("test run is not filtered with --tests") { !filteredRun }
    doLast {
        val dir = resultsDir.get().asFile
        val problems = expected.mapNotNull { className ->
            val report = dir.resolve("TEST-$className.xml")
            if (!report.isFile) return@mapNotNull "$className: no test report (class missing or filtered out)"
            val suite = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report).documentElement
            fun count(attr: String) = suite.getAttribute(attr).toIntOrNull() ?: 0
            val ran = count("tests") - count("skipped")
            val failed = count("failures") + count("errors")
            logger.lifecycle("parser-core: %-58s %4d tests, %d failed".format(className, ran, failed))
            when {
                ran <= 0 -> "$className: 0 tests ran"
                failed > 0 -> "$className: $failed failed"
                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Upstream bank tests not green:\n" + problems.joinToString("\n"))
        }
    }
}

val verifySubmoduleClean = tasks.register("verifySubmoduleClean") {
    description = "Fails if building or testing modified anything inside third_party/pennywise."
    group = "verification"
    val dir = submoduleDir
    doLast {
        val process = ProcessBuilder("git", "-C", dir.absolutePath, "status", "--porcelain")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "git status failed in $dir:\n$output" }
        if (output.isNotEmpty()) throw GradleException("Submodule third_party/pennywise was modified:\n$output")
    }
}

tasks.test {
    useJUnitPlatform()
    // Some upstream tests resolve paths relative to upstream's parser-core dir or walk up to
    // upstream's settings.gradle.kts (docs consistency checks). Run them from there.
    workingDir = upstream.asFile
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(verifyOurBankTestsRan, verifySubmoduleClean)
}

tasks.check {
    dependsOn(verifyOurBankTestsRan, verifySubmoduleClean)
}
