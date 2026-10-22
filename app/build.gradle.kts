import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
    if (sequence.isEmpty() || sequence.size > size) return false
    return indices.any { start ->
        start + sequence.size <= size && sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
    }
}

val verifyStringEncoding = tasks.register("verifyStringEncoding") {
    group = "verification"
    description = "Checks UTF-8 source/resource files and parses Android XML resources."
    doLast {
        val roots = listOf(
            file("src/main/res"),
            file("src/main/java"),
            file("src/test"),
            file("src/androidTest"),
            rootProject.file("docs"),
        ).filter(File::exists)
        val mojibake = listOf(
            byteArrayOf(0xC3.toByte(), 0x83.toByte()),
            byteArrayOf(0xC3.toByte(), 0x82.toByte()),
            byteArrayOf(0xC3.toByte(), 0xA2.toByte()),
        )
        val sources = roots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
        sources.forEach { source ->
            val bytes = source.readBytes()
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
            } catch (error: Exception) {
                throw GradleException("Invalid UTF-8 in ${source.path}", error)
            }
            if (mojibake.any { sequence -> bytes.containsSequence(sequence) }) {
                throw GradleException("Possible mojibake in ${source.path}")
            }
        }
        fileTree(file("src/main/res")) { include("**/*.xml") }.forEach { resource ->
            resource.inputStream().use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyStringEncoding)
}

android {
    namespace = "com.majyd.casa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.majyd.casa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.majyd.casa.test.CasaTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Compose lint 1.4.x crashes with the Kotlin analysis API bundled here.
        disable += setOf("FrequentlyChangingValue", "RememberInComposition", "NullSafeMutableLiveData", "AutoboxingStateCreation")
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.androidx.exifinterface)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)

    kapt(libs.hilt.compiler)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
}
