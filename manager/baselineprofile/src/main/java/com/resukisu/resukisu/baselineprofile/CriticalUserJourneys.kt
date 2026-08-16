package com.resukisu.resukisu.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

private const val SWIPE_EDGE_FRACTION = 0.2f
private const val SWIPE_STEPS = 20
private const val HOME_PAGER_PAGE_TRANSITIONS = 5
private const val PAGER_SETTLE_TIMEOUT_MILLIS = 1_000L

internal fun MacrobenchmarkScope.scrollHomePagerBackAndForth() {
    val width = device.displayWidth
    val centerY = device.displayHeight / 2
    val left = (width * SWIPE_EDGE_FRACTION).toInt()
    val right = width - left

    repeat(HOME_PAGER_PAGE_TRANSITIONS) {
        device.swipe(right, centerY, left, centerY, SWIPE_STEPS)
        device.waitForIdle(PAGER_SETTLE_TIMEOUT_MILLIS)
    }

    repeat(HOME_PAGER_PAGE_TRANSITIONS) {
        device.swipe(left, centerY, right, centerY, SWIPE_STEPS)
        device.waitForIdle(PAGER_SETTLE_TIMEOUT_MILLIS)
    }
}
