package com.resukisu.resukisu.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class HomePagerBenchmark(
    private val compilationMode: CompilationMode,
) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun userScroll() = benchmarkRule.measureRepeated(
        packageName = "com.resukisu.resukisu",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        scrollHomePagerBackAndForth()
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
