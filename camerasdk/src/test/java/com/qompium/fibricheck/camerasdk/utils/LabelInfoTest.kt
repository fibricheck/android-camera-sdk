package com.qompium.fibricheck.camerasdk.utils

import com.qompium.fibricheck.camerasdk.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class LabelInfoTest {

    @Test
    fun `getLabel returns all required keys`() {
        val label = LabelInfo.getLabel()

        assertTrue("componentName key missing", label.containsKey("componentName"))
        assertTrue("udi key missing", label.containsKey("udi"))
        assertTrue("ceLabel key missing", label.containsKey("ceLabel"))
        assertTrue("manufacturer key missing", label.containsKey("manufacturer"))
        assertTrue("releaseDate key missing", label.containsKey("releaseDate"))
        assertTrue("ifu key missing", label.containsKey("ifu"))
    }

    @Test
    fun `componentName contains SDK version`() {
        val label = LabelInfo.getLabel()
        val componentName = label["componentName"]

        assertNotNull(componentName)
        assertTrue(
            "componentName should contain version",
            componentName!!.contains(BuildConfig.SDK_VERSION)
        )
        assertTrue(
            "componentName should start with 'FibriCheck Camera SDK Android'",
            componentName.startsWith("FibriCheck Camera SDK Android")
        )
    }

    @Test
    fun `udi has correct format and matches SDK version`() {
        val label = LabelInfo.getLabel()
        val udi = label["udi"]

        assertNotNull(udi)
        assertTrue(
            "UDI should start with correct prefix",
            udi!!.startsWith("(01)05419980589323(8012)CAMAND")
        )

        val versionCode = udi.substringAfter("CAMAND")
        assertTrue(
            "UDI version part should only contain digits",
            versionCode.all { it.isDigit() }
        )

        val expectedVersionCode = BuildConfig.SDK_VERSION
            .split('.')
            .joinToString("") { it.padStart(2, '0') }

        assertEquals(
            "UDI version code should match padded SDK version",
            expectedVersionCode,
            versionCode
        )
    }

    @Test
    fun `ceLabel is correct`() {
        val label = LabelInfo.getLabel()
        assertEquals("CE 1639", label["ceLabel"])
    }

    @Test
    fun `manufacturer is correct`() {
        val label = LabelInfo.getLabel()
        assertEquals(
            "Qompium NV - Kempische Steenweg 303/27 - 3500 Hasselt - Belgium",
            label["manufacturer"]
        )
    }

    @Test
    fun `releaseDate has YYYY-MM format`() {
        val label = LabelInfo.getLabel()
        val releaseDate = label["releaseDate"]

        assertNotNull(releaseDate)
        assertTrue(
            "releaseDate should match YYYY-MM format",
            releaseDate!!.matches(Regex("\\d{4}-\\d{2}"))
        )
    }

    @Test
    fun `ifu URL is correct`() {
        val label = LabelInfo.getLabel()
        assertEquals("https://pages.fibricheck.com/document-versions/", label["ifu"])
    }
}
