package budgetwidget.buildlogic

import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room with KSP. Schemas are exported to `<module>/schemas` and checked in (SCOPE.md §5). */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            // Migration tests (Robolectric) read the exported schemas as test assets.
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
            }
            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("ksp", libs.library("androidx-room-compiler"))
                add("testImplementation", libs.library("androidx-room-testing"))
            }
        }
    }
}
