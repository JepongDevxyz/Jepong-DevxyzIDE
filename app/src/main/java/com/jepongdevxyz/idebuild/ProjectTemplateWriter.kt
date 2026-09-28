package com.jepongdevxyz.idebuild

import java.io.File
import java.io.InputStream
import java.util.UUID

/** Writes a complete, launchable Android starter project before exposing it in the workspace. */
internal object ProjectTemplateWriter {
    fun create(workspace: File, requestedName: String, wrapperJar: InputStream): File {
        val safeName = requestedName.trim()
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .ifBlank { "MyAwesomeApp" }
            .let { if (it.first().isDigit()) "App$it" else it }
        val appId = "com.example." + safeName.lowercase()
        require(workspace.mkdirs() || workspace.isDirectory) { "Cannot create workspace" }
        val destination = File(workspace, safeName)
        require(!destination.exists()) { "A project named $safeName already exists" }
        val staging = File(workspace, ".new-$safeName-${UUID.randomUUID()}")
        require(staging.mkdirs()) { "Cannot create project staging directory" }
        try {
            File(staging, "settings.gradle.kts").writeText(
                """pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name="$safeName"
include(":app")
"""
            )
            File(staging, "build.gradle.kts").writeText(
                """plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
}
"""
            )
            val app = File(staging, "app").apply { mkdirs() }
            File(app, "build.gradle.kts").writeText(
                """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="$appId"
    compileSdk=35
    defaultConfig {
        applicationId="$appId"
        minSdk=26
        targetSdk=35
        versionCode=1
        versionName="1.0"
    }
}
"""
            )
            val main = File(app, "src/main").apply { mkdirs() }
            val source = File(main, "java/" + appId.replace('.', '/')).apply { mkdirs() }
            File(source, "MainActivity.kt").writeText(
                """package $appId
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "$safeName"; textSize = 24f })
    }
}
"""
            )
            File(main, "AndroidManifest.xml").writeText(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="$safeName">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
    </application>
</manifest>
"""
            )
            val wrapper = File(staging, "gradle/wrapper").apply { mkdirs() }
            File(wrapper, "gradle-wrapper.properties").writeText(
                """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
            )
            File(wrapper, "gradle-wrapper.jar").outputStream().use { wrapperJar.copyTo(it) }
            File(staging, "gradlew").writeText(
                """#!/system/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -classpath "${'$'}BASE_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "${'$'}@"
"""
            )
            File(staging, "gradlew").setExecutable(true, false)
            require(staging.renameTo(destination)) { "Cannot activate new project" }
            return destination
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        } finally {
            wrapperJar.close()
        }
    }
} }BASE_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
"""
            )
            File(staging, "gradlew").setExecutable(true, false)
            require(staging.renameTo(destination)) { "Cannot activate new project" }
            return destination
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        } finally {
            wrapperJar.close()
        }
    }
} }@"
"""
            )
            File(staging, "gradlew").setExecutable(true, false)
            require(staging.renameTo(destination)) { "Cannot activate new project" }
            return destination
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        } finally {
            wrapperJar.close()
        }
    }
} }BASE_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
"""
            )
            File(staging, "gradlew").setExecutable(true, false)
            require(staging.renameTo(destination)) { "Cannot activate new project" }
            return destination
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        } finally {
            wrapperJar.close()
        }
    }
}