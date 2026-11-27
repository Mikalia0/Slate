package com.slate.android.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlateBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private val target = "com.slate.android"

    @Test fun coldStartup() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) { startActivityAndWait() }

    @Test fun coldStartupWithoutProfile() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) { startActivityAndWait() }

    @Test fun fileManagerScroll() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            val files = device.wait(Until.findObject(By.text("Files")), 5000)
                ?: device.wait(Until.findObject(By.desc("Files")), 2000)
                ?: error("Complete onboarding and set the app language to English before running.")
            files.click()
            device.waitForIdle()
        }
    ) {
        repeat(5) {
            device.swipe(device.displayWidth/2, device.displayHeight*3/4, device.displayWidth/2, device.displayHeight/4, 18)
        }
    }
}
