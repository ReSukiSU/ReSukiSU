package com.resukisu.resukisu.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StartupBenchmark(
    private val compilationMode: CompilationMode,
) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.resukisu.resukisu",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = MacrobenchmarkScope::pressHome,
    ) {
        startActivityAndWait()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "compilation={0}")
        fun compilationModes(): List<Array<CompilationMode>> = listOf(
            arrayOf(CompilationMode.None()),
            arrayOf(
                CompilationMode.Partial(
                    baselineProfileMode = BaselineProfileMode.Require,
                )
            ),
        )
    }
}
