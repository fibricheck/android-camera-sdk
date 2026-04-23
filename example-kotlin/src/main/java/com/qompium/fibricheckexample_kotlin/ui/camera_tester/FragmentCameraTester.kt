package com.qompium.fibricheckexample_kotlin.ui.camera_tester

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheck.camerasdk.FibriChecker.FibriBuilder
import com.qompium.fibricheck.camerasdk.listeners.FibriListener
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import com.qompium.fibricheck.camerasdk.models.CameraSettingMode
import com.qompium.fibricheck.camerasdk.models.CameraSettings
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInput
import com.qompium.fibricheck.camerasdk.models.CameraSettingsState
import com.qompium.fibricheck.camerasdk.models.HdrMode
import com.qompium.fibricheck.camerasdk.models.WhiteBalanceMode
import com.qompium.fibricheckexample_kotlin.databinding.FragmentCameraTesterBinding
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FragmentCameraTester : Fragment() {

    private var _binding: FragmentCameraTesterBinding? = null
    private lateinit var fibrichecker: FibriChecker

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var lastData: Map<String, String>? = null
    private var cameraSettings: CameraSettingsInput = CameraSettingsInput(internal_hdrMode = HdrMode.Auto, internal_logHdr = true)
    private lateinit var cameraInfo: CameraSettingsInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraTesterBinding.inflate(inflater, container, false)
        val root: View = binding.root
        root.keepScreenOn = true

        fibrichecker = FibriBuilder(requireActivity(), binding.cameraFinder)
            .fibriListener(object: FibriListener() {
                override fun onMeasurementProcessed(measurementData: MeasurementData?) {
                    if (measurementData != null) {
                        println(measurementData.technical_details)
                        println(measurementData.cameraSettings.exposureMode)
                        println(measurementData.cameraSettings.iso?.subList(0, min(
                            measurementData.cameraSettings.iso!!.size, 10)))
                        println(measurementData.cameraSettings.exposureTime?.subList(0, min(
                            measurementData.cameraSettings.exposureTime!!.size, 10)))
                        println(measurementData.cameraSettings.whiteBalanceMode)
                        println(measurementData.cameraSettings.whiteBalance?.subList(0, min(
                            measurementData.cameraSettings.whiteBalance!!.size, 10)))
                        println(measurementData.cameraSettings.focusMode)
                        println(measurementData.cameraSettings.focus?.subList(0, min(
                            measurementData.cameraSettings.focus!!.size, 10)))
                        println(measurementData.cameraSettings.hdrMode)
                        println(measurementData.cameraSettings.hdr?.subList(0, min(
                            measurementData.cameraSettings.hdr!!.size, 10)))
                    }

                    fibrichecker.stop()
                }
            })
            .build()
        
        fibrichecker.sampleTime = 20
        fibrichecker.fingerDetectionExpiryTime = 10000
        fibrichecker.pulseDetectionExpiryTime = 10000
        fibrichecker.setCameraSettings(cameraSettings)
        cameraInfo = fibrichecker.cameraInfo

        initUi()

        return root
    }

    private fun initUi() {
        initWhiteBalance()
        initFocus()
        initExposure()
        initHdr()
        initLogButtons()
    }

    private fun initWhiteBalance() {
        binding.whiteBalanceState.onValueChange = { index, value ->
            val whiteBalanceMode = when (value) {
                "Auto" -> WhiteBalanceMode.Auto
                "Locked" -> WhiteBalanceMode.Locked
                "Rgb" -> WhiteBalanceMode.ManualRgb
                "Kelvin" -> WhiteBalanceMode.ManualKelvin
                else -> WhiteBalanceMode.Locked
            }

            cameraSettings.internal_whiteBalanceMode = whiteBalanceMode
            when (whiteBalanceMode) {
                WhiteBalanceMode.ManualRgb -> cameraSettings.internal_manualWhiteBalanceRgb = Vec3f(binding.whiteBalanceSliderR.value, binding.whiteBalanceSliderG.value, binding.whiteBalanceSliderB.value)
                WhiteBalanceMode.ManualKelvin -> cameraSettings.internal_manualWhiteBalanceKelvin = binding.whiteBalanceSliderKelvin.value.toInt()
                else -> {}
            }

            updateWhiteBalance()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.whiteBalanceSliderR.addOnChangeListener { _, value, _ ->
            cameraSettings.internal_manualWhiteBalanceRgb = Vec3f(value, cameraSettings.internal_manualWhiteBalanceRgb.g, cameraSettings.internal_manualWhiteBalanceRgb.b)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderG.addOnChangeListener { _, value, _ ->
            cameraSettings.internal_manualWhiteBalanceRgb = Vec3f(cameraSettings.internal_manualWhiteBalanceRgb.r, value, cameraSettings.internal_manualWhiteBalanceRgb.b)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderB.addOnChangeListener { _, value, _ ->
            cameraSettings.internal_manualWhiteBalanceRgb = Vec3f(cameraSettings.internal_manualWhiteBalanceRgb.r, cameraSettings.internal_manualWhiteBalanceRgb.g, value)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderKelvin.addOnChangeListener { _, value, _ ->
            cameraSettings.internal_manualWhiteBalanceKelvin = value.toInt()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        updateWhiteBalance()
    }

    private fun updateWhiteBalance() {
        val state = when(cameraSettings.internal_whiteBalanceMode) {
            WhiteBalanceMode.Locked -> "Locked"
            WhiteBalanceMode.Auto -> "Auto"
            WhiteBalanceMode.ManualRgb -> "Rgb"
            WhiteBalanceMode.ManualKelvin -> "Kelvin"
        }

        binding.whiteBalanceState.value = state
        binding.whiteBalanceRgb.visibility = if (cameraSettings.internal_whiteBalanceMode == WhiteBalanceMode.ManualRgb) View.VISIBLE else View.GONE
        binding.whiteBalanceSliderKelvin.visibility = if (cameraSettings.internal_whiteBalanceMode == WhiteBalanceMode.ManualKelvin) View.VISIBLE else View.GONE
    }

    private fun initFocus() {
        binding.focusState.onValueChange = { index, value ->
            val mode = when (value) {
                "Auto" -> CameraSettingMode.Auto
                "Locked" -> CameraSettingMode.Locked
                "Manual" -> CameraSettingMode.Manual
                else -> CameraSettingMode.Locked
            }

            cameraSettings.internal_focusMode = mode
            if (mode == CameraSettingMode.Manual) {
                cameraSettings.internal_manualFocus = binding.focusSlider.value
            }

            updateFocus()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        val focusRange = cameraInfo.focusRange
        binding.focusSlider.valueFrom = focusRange.first
        binding.focusSlider.valueTo = focusRange.second

        binding.focusSlider.addOnChangeListener(OnChangeListener { _, value, _ ->
            cameraSettings.internal_manualFocus = value
            fibrichecker.setCameraSettings(cameraSettings)
        })

        updateFocus()
    }

    private fun updateFocus() {
        val state = when(cameraSettings.internal_focusMode) {
            CameraSettingMode.Locked -> "Locked"
            CameraSettingMode.Auto -> "Auto"
            CameraSettingMode.Manual -> "Manual"
        }

        binding.focusState.value = state
        binding.focusSlider.visibility = if (cameraSettings.internal_focusMode == CameraSettingMode.Manual) View.VISIBLE else View.GONE
    }

    private fun initExposure() {
        binding.exposureState.onValueChange = { index, value ->
            val mode = when (value) {
                "Auto" -> CameraSettingMode.Auto
                "Locked" -> CameraSettingMode.Locked
                "Manual" -> CameraSettingMode.Manual
                else -> CameraSettingMode.Locked
            }

            cameraSettings.internal_exposureMode = mode
            if (mode == CameraSettingMode.Manual) {
                cameraSettings.internal_manualIso = binding.sliderIso.value.toInt()
                cameraSettings.internal_manualExposureTime = binding.sliderExposureTime.value.toLong()
            }

            updateExposure()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        val isoDelta = cameraInfo.isoRange.second - cameraInfo.isoRange.first
        val stepSize = isoDelta / 20.0f
        binding.sliderIso.stepSize = stepSize
        binding.sliderIso.value = cameraInfo.isoRange.first + stepSize * 3
        binding.sliderIso.valueFrom = cameraInfo.isoRange.first.toFloat()
        binding.sliderIso.valueTo = cameraInfo.isoRange.second.toFloat()

        binding.sliderIso.addOnChangeListener { slider, value, fromUser ->
            cameraSettings.internal_manualIso = slider.value.roundToInt()
            binding.textIso.text = "ISO: ${slider.value.roundToInt()}"
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.sliderExposureTime.addOnChangeListener { slider, value, fromUser ->
            cameraSettings.internal_manualExposureTime = dividerToExposureTime(slider.value)
            binding.textExposureTime.text = "Exposure time: 1/${slider.value}"
            fibrichecker.setCameraSettings(cameraSettings)
        }

        updateExposure()
    }

    private fun initHdr() {
        binding.hdrState.onValueChange = { index, value ->
            val mode = when (value) {
                "Auto" -> HdrMode.Auto
                "Off" -> HdrMode.Off
                else -> HdrMode.Auto
            }

            cameraSettings.internal_hdrMode = mode
            fibrichecker.setCameraSettings(cameraSettings)
        }
    }

    private fun initLogButtons() {
        binding.switchLogExposure.setOnCheckedChangeListener { button, bool ->
            cameraSettings.internal_logExposure = bool
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.switchLogWhiteBalance.setOnCheckedChangeListener { button, bool ->
            cameraSettings.internal_logWhiteBalance = bool
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.switchLogFocus.setOnCheckedChangeListener { button, bool ->
            cameraSettings.internal_logFocus = bool
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.switchLogHdr.setOnCheckedChangeListener { button, bool ->
            cameraSettings.internal_logHdr = bool
            fibrichecker.setCameraSettings(cameraSettings)
        }
    }

    private fun updateExposure() {
        val state = when(cameraSettings.internal_exposureMode) {
            CameraSettingMode.Locked -> "Locked"
            CameraSettingMode.Auto -> "Auto"
            CameraSettingMode.Manual -> "Manual"
        }

        binding.exposureState.value = state
        binding.groupManualExposure.visibility = if (cameraSettings.internal_exposureMode == CameraSettingMode.Manual) View.VISIBLE else View.GONE
    }

    private fun dividerToExposureTime(divider: Float): Long {
        return (1_000_000_000L / divider).roundToLong()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fibrichecker.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fibrichecker.stop()
    }

    private fun onNewMetadata(metadata: Map<String, String>) {
        println()
        println("Metadata:")
        metadata.forEach { (key, value) ->
            if (lastData?.get(key) != value) {
                println("\t$key: $value")
            }
        }

        lastData = metadata
    }
}