package budgetwidget.buildlogic

import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** A permit: holding it is what lets one KSP task run. */
abstract class KspPermit : BuildService<BuildServiceParameters.None>

/**
 * Runs KSP tasks one at a time across the whole build (docs/DECISIONS.md ADR-0034). KSP2 runs in
 * the Gradle daemon and concurrent tasks share its cached analysis classloader, whose global
 * state they then corrupt: clean parallel builds intermittently failed with SDK types that
 * "could not be resolved", or a module's KSP task "succeeded" with no output, so Hilt missed its
 * bindings. Everything else still runs in parallel; KSP is a few seconds per module.
 */
internal fun Project.runKspSerially() {
    val permit = gradle.sharedServices.registerIfAbsent("kspPermit", KspPermit::class.java) {
        maxParallelUsages.set(1)
    }
    tasks.withType(KspAATask::class.java).configureEach { usesService(permit) }
}
