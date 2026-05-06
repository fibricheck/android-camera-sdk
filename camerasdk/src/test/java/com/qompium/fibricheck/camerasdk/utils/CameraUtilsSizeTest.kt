package com.qompium.fibricheck.camerasdk.utils

import android.util.Size
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraUtilsSizeTest {

    @Test
    fun `getSmallestSize returns the only element when given a single size`() {
        val sizes = arrayOf(Size(1920, 1080))
        assertEquals(Size(1920, 1080), CameraUtils.getSmallestSize(sizes))
    }

    @Test
    fun `getSmallestSize returns smallest by area`() {
        val sizes = arrayOf(Size(1920, 1080), Size(1280, 720), Size(640, 480))
        assertEquals(Size(640, 480), CameraUtils.getSmallestSize(sizes))
    }

    @Test
    fun `getSmallestSize handles sizes with same area`() {
        // 100x200 and 200x100 have the same area — either is a valid result
        val sizes = arrayOf(Size(100, 200), Size(200, 100), Size(50, 50))
        assertEquals(Size(50, 50), CameraUtils.getSmallestSize(sizes))
    }

    @Test
    fun `getSmallestSize is not fooled by large width with small height`() {
        val sizes = arrayOf(Size(4000, 10), Size(100, 100))
        // 4000*10=40000 vs 100*100=10000 → 100x100 is smaller
        assertEquals(Size(100, 100), CameraUtils.getSmallestSize(sizes))
    }
}
