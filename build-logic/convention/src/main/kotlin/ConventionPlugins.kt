import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        dependencies {
            add("testImplementation", lib("junit"))
            add("testImplementation", lib("truth"))
        }
    }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        dependencies {
            add("testImplementation", lib("junit"))
            add("testImplementation", lib("truth"))
        }
    }
}

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            includeSourceInformation.set(true)
        }
        dependencies {
            val bom = platform(lib("compose-bom"))
            add("implementation", bom)
            add("implementation", lib("compose-ui"))
            add("implementation", lib("compose-foundation"))
            add("implementation", lib("compose-material3"))
            add("implementation", lib("compose-ui-tooling-preview"))
            add("debugImplementation", lib("compose-ui-tooling"))
        }
    }
}

/** Feature modules: Compose + Hilt ViewModels + the shared core modules (ARCHITECTURE §4.2 rule 1). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("atlas.android.library")
        pluginManager.apply("atlas.android.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:map"))
            add("implementation", lib("androidx-activity-compose"))
            add("implementation", lib("androidx-lifecycle-runtime-compose"))
            add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", lib("androidx-navigation-compose"))
            add("implementation", lib("kotlinx-serialization-json"))
            add("implementation", lib("kotlinx-coroutines-android"))
            add("testImplementation", lib("kotlinx-coroutines-test"))
        }
    }
}
