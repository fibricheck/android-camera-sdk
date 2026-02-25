package com.qompium.fibricheckexample_kotlin.ui.camera_tester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider.OnChangeListener
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheck.camerasdk.FibriChecker.FibriBuilder
import com.qompium.fibricheck.camerasdk.listeners.FibriListener
import com.qompium.fibricheck.camerasdk.listeners.RawDataListener
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import com.qompium.fibricheck.camerasdk.models.CameraSettingMode
import com.qompium.fibricheck.camerasdk.models.CameraSettings
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo
import com.qompium.fibricheck.camerasdk.models.WhiteBalanceMode
import com.qompium.fibricheckexample_kotlin.databinding.FragmentCameraTesterBinding
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class FragmentCameraTester : Fragment() {

    private var _binding: FragmentCameraTesterBinding? = null
    private lateinit var fibrichecker: FibriChecker

    private val binding get() = _binding!!
    private var lastData: Map<String, String>? = null
    private var cameraSettings: CameraSettings = CameraSettings()
    private lateinit var cameraInfo: CameraSettingsInfo
    private var measurementCount = 0;

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraTesterBinding.inflate(inflater, container, false)
        val root: View = binding.root

        fibrichecker = FibriBuilder(requireActivity(), binding.cameraFinder)
            .fibriListener(object: FibriListener() {
                override fun onSampleReady(ppg: Double, raw: Double) {
                    // println("PPG: $ppg")
                }

                override fun onMeasurementStart(timestamp: Long) {
                    println("onMeasurementStart")
                    root.keepScreenOn = true
                }

                override fun onMeasurementFinished(timestamp: Long) {
                    println("onMeasurementFinished")
                    // To loop the camera
                    root.keepScreenOn = false
                    println("Finished Measurements: ${++this@FragmentCameraTester.measurementCount}")
                    fibrichecker.start()
                }

                override fun onMeasurementError(message: String?) {
                    println("onMeasurementError")
                    root.keepScreenOn = false
                }
            })
            .rawDataListener(object: RawDataListener {
                override fun onNewData(
                    data: ByteArray,
                    metadata: Map<String, String>
                ) {
                    // println("Received raw image of ${data.size} bytes")
                    onNewMetadata(metadata)
                }
            })
            .build()
        
        fibrichecker.sampleTime = 20
        fibrichecker.fingerDetectionExpiryTime = 5000
        fibrichecker.pulseDetectionExpiryTime = 0
        cameraSettings.rawDataEnabled = false
        fibrichecker.setCameraSettings(cameraSettings)
        cameraInfo = fibrichecker.cameraInfo

        initUi()

        return root
    }

    private fun initUi() {
        initControls()
        initWhiteBalance()
        initFocus()
        initExposure()
    }

    private fun initControls() {
        binding.buttonRecord.setOnClickListener {
            fibrichecker.start()
        }

        binding.buttonPreview.setOnClickListener {
            fibrichecker.preview()
        }
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

            cameraSettings.whiteBalanceMode = whiteBalanceMode
            when (whiteBalanceMode) {
                WhiteBalanceMode.ManualRgb -> cameraSettings.manualWhiteBalanceRgb = Vec3f(binding.whiteBalanceSliderR.value, binding.whiteBalanceSliderG.value, binding.whiteBalanceSliderB.value)
                WhiteBalanceMode.ManualKelvin -> cameraSettings.manualWhiteBalanceKelvin = binding.whiteBalanceSliderKelvin.value.toInt()
                else -> {}
            }

            updateWhiteBalance()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.whiteBalanceSliderR.addOnChangeListener { _, value, _ ->
            cameraSettings.manualWhiteBalanceRgb = Vec3f(value, cameraSettings.manualWhiteBalanceRgb.g, cameraSettings.manualWhiteBalanceRgb.b)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderG.addOnChangeListener { _, value, _ ->
            cameraSettings.manualWhiteBalanceRgb = Vec3f(cameraSettings.manualWhiteBalanceRgb.r, value, cameraSettings.manualWhiteBalanceRgb.b)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderB.addOnChangeListener { _, value, _ ->
            cameraSettings.manualWhiteBalanceRgb = Vec3f(cameraSettings.manualWhiteBalanceRgb.r, cameraSettings.manualWhiteBalanceRgb.g, value)
            fibrichecker.setCameraSettings(cameraSettings)
        }
        binding.whiteBalanceSliderKelvin.addOnChangeListener { _, value, _ ->
            cameraSettings.manualWhiteBalanceKelvin = value.toInt()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        updateWhiteBalance()
    }

    private fun updateWhiteBalance() {
        val state = when(cameraSettings.whiteBalanceMode) {
            WhiteBalanceMode.Locked -> "Locked"
            WhiteBalanceMode.Auto -> "Auto"
            WhiteBalanceMode.ManualRgb -> "Rgb"
            WhiteBalanceMode.ManualKelvin -> "Kelvin"
        }

        binding.whiteBalanceState.value = state
        binding.whiteBalanceRgb.visibility = if (cameraSettings.whiteBalanceMode == WhiteBalanceMode.ManualRgb) View.VISIBLE else View.GONE
        binding.whiteBalanceSliderKelvin.visibility = if (cameraSettings.whiteBalanceMode == WhiteBalanceMode.ManualKelvin) View.VISIBLE else View.GONE
    }

    private fun initFocus() {
        binding.focusState.onValueChange = { index, value ->
            val mode = when (value) {
                "Auto" -> CameraSettingMode.Auto
                "Locked" -> CameraSettingMode.Locked
                "Manual" -> CameraSettingMode.Manual
                else -> CameraSettingMode.Locked
            }

            cameraSettings.focusMode = mode
            if (mode == CameraSettingMode.Manual) {
                cameraSettings.manualFocusValue = binding.focusSlider.value
            }

            updateFocus()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        val focusRange = cameraInfo.focusRange
        binding.focusSlider.valueFrom = focusRange.first
        binding.focusSlider.valueTo = focusRange.second

        binding.focusSlider.addOnChangeListener(OnChangeListener { _, value, _ ->
            cameraSettings.manualFocusValue = value
            fibrichecker.setCameraSettings(cameraSettings)
        })

        updateFocus()
    }

    private fun updateFocus() {
        val state = when(cameraSettings.focusMode) {
            CameraSettingMode.Locked -> "Locked"
            CameraSettingMode.Auto -> "Auto"
            CameraSettingMode.Manual -> "Manual"
        }

        binding.focusState.value = state
        binding.focusSlider.visibility = if (cameraSettings.focusMode == CameraSettingMode.Manual) View.VISIBLE else View.GONE
    }

    private fun initExposure() {
        binding.exposureState.onValueChange = { index, value ->
            val mode = when (value) {
                "Auto" -> CameraSettingMode.Auto
                "Locked" -> CameraSettingMode.Locked
                "Manual" -> CameraSettingMode.Manual
                else -> CameraSettingMode.Locked
            }

            cameraSettings.exposureMode = mode
            if (mode == CameraSettingMode.Manual) {
                cameraSettings.manualIsoValue = binding.sliderIso.value.toInt()
                cameraSettings.manualExposureTime = binding.sliderExposureTime.value.toLong()
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
            cameraSettings.manualIsoValue = slider.value.roundToInt()
            binding.textIso.text = "ISO: ${slider.value.roundToInt()}"
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.sliderExposureTime.addOnChangeListener { slider, value, fromUser ->
            cameraSettings.manualExposureTime = dividerToExposureTime(slider.value)
            binding.textExposureTime.text = "Exposure time: 1/${slider.value}"
            fibrichecker.setCameraSettings(cameraSettings)
        }

        updateExposure()
    }

    private fun updateExposure() {
        val state = when(cameraSettings.exposureMode) {
            CameraSettingMode.Locked -> "Locked"
            CameraSettingMode.Auto -> "Auto"
            CameraSettingMode.Manual -> "Manual"
        }

        binding.exposureState.value = state
        binding.groupManualExposure.visibility = if (cameraSettings.exposureMode == CameraSettingMode.Manual) View.VISIBLE else View.GONE
    }

    private fun dividerToExposureTime(divider: Float): Long {
        return (1_000_000_000L / divider).roundToLong()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fibrichecker.preview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fibrichecker.stop()
    }

    private fun onNewMetadata(metadata: Map<String, String>) {
//        println()
//        println("Metadata:")
//        metadata.forEach { (key, value) ->
//            if (lastData?.get(key) != value) {
//                println("\t$key: $value")
//            }
//        }

        lastData = metadata
    }
}