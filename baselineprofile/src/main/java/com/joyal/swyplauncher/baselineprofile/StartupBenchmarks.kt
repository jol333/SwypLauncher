package com.joyal.swyplauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup Benchmarks for Swyp Launcher.
 * 
 * This class measures app startup time with different compilation modes
 * to verify the effectiveness of the baseline profile.
 * 
 * To run the benchmarks:
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Measures startup time without any compilation (worst case scenario).
     */
    @Test
    fun startupCompilationNone() = benchmark(CompilationMode.None())

    /**
     * Measures startup time with partial compilation using baseline profile.
     * This represents typical first-time user experience after app install from Play Store.
     */
    @Test
    fun startupCompilationBaselineProfiles() =
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    /**
     * Measures startup time with full compilation (best case scenario after device optimization).
     */
    @Test
    fun startupCompilationFull() = benchmark(CompilationMode.Full())

    private fun benchmark(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = "com.joyal.swyplauncher",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
