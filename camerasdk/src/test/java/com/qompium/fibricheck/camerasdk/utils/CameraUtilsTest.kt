package com.qompium.fibricheck.camerasdk.utils

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import org.junit.Assert.*
import org.junit.Test

class CameraUtilsTest {

    @Test
    fun `whiteBalanceToGains output is always in 0 to 2 range`() {
        listOf(0, 1900, 2700, 4000, 6500, 6600, 10000).forEach { wb ->
            val result = CameraUtils.whiteBalanceToGains(wb)
            assertTrue("red out of range for wb=$wb", result.r in 0f..2f)
            assertTrue("green out of range for wb=$wb", result.g in 0f..2f)
            assertTrue("blue out of range for wb=$wb", result.b in 0f..2f)
        }
    }

    @Test
    fun `whiteBalanceToGains very low temperature has zero blue gain and maximum red gain`() {
        // temperature = 0 / 100 = 0 → blue branch (<=19) → blue=0, red=255
        val result = CameraUtils.whiteBalanceToGains(0)
        assertEquals(2.0f, result.r, 0.001f)
        assertEquals(0.0f, result.b, 0.001f)
    }

    @Test
    fun `whiteBalanceToGains temperature at 19 boundary has zero blue gain`() {
        // temperature = 1900 / 100 = 19 → exactly at blue=0 boundary
        val result = CameraUtils.whiteBalanceToGains(1900)
        assertEquals(2.0f, result.r, 0.001f)
        assertEquals(0.0f, result.b, 0.001f)
    }

    @Test
    fun `whiteBalanceToGains temperature at 66 boundary has both red and blue at maximum gain`() {
        // temperature = 6600 / 100 = 66 → red=255 (<=66 branch), blue=255 (>=66 branch)
        val result = CameraUtils.whiteBalanceToGains(6600)
        assertEquals(2.0f, result.r, 0.001f)
        assertEquals(2.0f, result.b, 0.001f)
    }

    @Test
    fun `whiteBalanceToGains high temperature has maximum blue gain and reduced red gain`() {
        // temperature = 10000 / 100 = 100 → blue fixed at 255, red calculated from formula
        val result = CameraUtils.whiteBalanceToGains(10000)
        assertEquals(2.0f, result.b, 0.001f)
        assertTrue("red gain should be below maximum at 10000K", result.r < 2.0f)
    }

    @Test
    fun `whiteBalanceToGains 6500K matches expected RGB gains`() {
        // temperature = 65 → red=255, green≈254, blue≈250
        val result = CameraUtils.whiteBalanceToGains(6500)
        assertEquals(2.0f, result.r, 0.001f)
        assertEquals(1.992f, result.g, 0.01f)
        assertEquals(1.961f, result.b, 0.01f)
    }

    @Test
    fun `getHighestQualityHdrProfile returns -1 for empty set`() {
        assertEquals(-1L, CameraUtils.getHighestQualityHdrProfile(emptySet()))
    }

    @Test
    fun `getHighestQualityHdrProfile returns STANDARD when only SDR available`() {
        val profiles = setOf(DynamicRangeProfiles.STANDARD)
        assertEquals(DynamicRangeProfiles.STANDARD, CameraUtils.getHighestQualityHdrProfile(profiles))
    }

    @Test
    fun `getHighestQualityHdrProfile prefers HLG10 over STANDARD`() {
        val profiles = setOf(DynamicRangeProfiles.STANDARD, DynamicRangeProfiles.HLG10)
        assertEquals(DynamicRangeProfiles.HLG10, CameraUtils.getHighestQualityHdrProfile(profiles))
    }

    @Test
    fun `getHighestQualityHdrProfile prefers HDR10 over HLG10`() {
        val profiles = setOf(DynamicRangeProfiles.HLG10, DynamicRangeProfiles.HDR10)
        assertEquals(DynamicRangeProfiles.HDR10, CameraUtils.getHighestQualityHdrProfile(profiles))
    }

    @Test
    fun `getHighestQualityHdrProfile prefers HDR10_PLUS over HDR10`() {
        val profiles = setOf(DynamicRangeProfiles.HDR10, DynamicRangeProfiles.HDR10_PLUS)
        assertEquals(DynamicRangeProfiles.HDR10_PLUS, CameraUtils.getHighestQualityHdrProfile(profiles))
    }

    @Test
    fun `getHighestQualityHdrProfile returns highest from mixed set`() {
        val profiles = setOf(
            DynamicRangeProfiles.STANDARD,
            DynamicRangeProfiles.HLG10,
            DynamicRangeProfiles.HDR10,
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM
        )
        assertEquals(
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM,
            CameraUtils.getHighestQualityHdrProfile(profiles)
        )
    }

    @Test
    fun `getHighestQualityHdrProfile ignores unknown profiles`() {
        val profiles = setOf(999L)
        assertEquals(-1L, CameraUtils.getHighestQualityHdrProfile(profiles))
    }

    @Test
    fun `tonemapModeToString returns null for null input`() {
        assertNull(CameraUtils.tonemapModeToString(null))
    }

    @Test
    fun `tonemapModeToString returns correct string for each known mode`() {
        assertEquals("CONTRAST_CURVE", CameraUtils.tonemapModeToString(CaptureResult.TONEMAP_MODE_CONTRAST_CURVE))
        assertEquals("FAST", CameraUtils.tonemapModeToString(CaptureResult.TONEMAP_MODE_FAST))
        assertEquals("HIGH_QUALITY", CameraUtils.tonemapModeToString(CaptureResult.TONEMAP_MODE_HIGH_QUALITY))
        assertEquals("GAMMA_VALUE", CameraUtils.tonemapModeToString(CaptureResult.TONEMAP_MODE_GAMMA_VALUE))
        assertEquals("PRESET_CURVE", CameraUtils.tonemapModeToString(CaptureResult.TONEMAP_MODE_PRESET_CURVE))
    }

    @Test
    fun `tonemapModeToString returns UNKNOWN for unrecognised mode`() {
        val result = CameraUtils.tonemapModeToString(999)
        assertNotNull(result)
        assertTrue("should start with UNKNOWN", result!!.startsWith("UNKNOWN"))
        assertTrue("should contain value", result.contains("999"))
    }
}
