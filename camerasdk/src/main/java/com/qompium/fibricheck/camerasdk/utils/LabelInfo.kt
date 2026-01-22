package com.qompium.fibricheck.camerasdk.utils

import com.qompium.fibricheck.camerasdk.BuildConfig

class LabelInfo {
  companion object {
    private const val UDI_NUMBER = "0044" // Increment this
    private const val UDI_PREFIX = "(01)05419980589323(8012)" // Always the same
    private const val UDI_PRODUCT = "CAMAND" // Always the same
    private const val CE_LABEL = "CE 1639"
    private const val MANUFACTURER = "Qompium NV - Kempische Steenweg 303/27 - 3500 Hasselt - Belgium"
    private const val IFU_URL = "https://pages.fibricheck.com/ifu"

    fun getLabel(): Map<String, String> {
      val version = BuildConfig.SDK_VERSION

      return mapOf(
        "componentName" to "FibriCheck Camera SDK Android $version",
        "udi" to buildUdi(version),
        "ceLabel" to CE_LABEL,
        "manufacturer" to MANUFACTURER,
        "releaseDate" to BuildConfig.RELEASE_DATE.substring(0, 7), // Remove day from the release date
        "ifu" to IFU_URL
      )
    }

    private fun buildUdi(version: String): String {
      val versionCode = version.split('.').map { it.padStart(2, '0')}.joinToString("")
      return "${UDI_PREFIX}${UDI_NUMBER}${UDI_PRODUCT}$versionCode"
    }
  }
}