package com.qompium.fibricheckexample_kotlin.ui.camera_tester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheck.camerasdk.FibriChecker.FibriBuilder
import com.qompium.fibricheck.camerasdk.listeners.FibriListener
import com.qompium.fibricheck.camerasdk.models.CameraSettings
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo
import com.qompium.fibricheckexample_kotlin.databinding.FragmentCameraTesterBinding
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FragmentCameraTester : Fragment() {

    private var _binding: FragmentCameraTesterBinding? = null
    private lateinit var fibrichecker: FibriChecker

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var lastData: Map<String, String>? = null
    private var cameraSettings: CameraSettings = CameraSettings()
    private lateinit var cameraInfo: CameraSettingsInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraTesterBinding.inflate(inflater, container, false)
        val root: View = binding.root

        fibrichecker = FibriBuilder(requireActivity(), binding.cameraFinder)
            .fibriListener(object: FibriListener() {
                override fun onMeasurementFinished(timestamp: Long) {
                    // To loop the camera
                    fibrichecker.stop()
                    fibrichecker.start()
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
    }

    private fun initWhiteBalance() {
        binding.sliderWhiteBalance.visibility = View.GONE
        binding.switchManualWhiteBalance.isChecked = false

        binding.switchManualWhiteBalance.setOnCheckedChangeListener { _, isChecked ->
            binding.sliderWhiteBalance.visibility = if (isChecked) View.VISIBLE else View.GONE
            cameraSettings.isManualWhiteBalanceEnabled = isChecked
            cameraSettings.manualWhiteBalance = CameraSettings.whiteBalanceToGains(binding.sliderWhiteBalance.value.roundToInt())
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.sliderWhiteBalance.addOnChangeListener(OnChangeListener { _, value, _ ->
            cameraSettings.manualWhiteBalance = CameraSettings.whiteBalanceToGains(value.roundToInt())
            fibrichecker.setCameraSettings(cameraSettings)
        })
    }

    private fun initFocus() {
        binding.sliderFocus.visibility = View.GONE
        binding.switchManualFocus.isChecked = false

        val focusRange = cameraInfo.focusRange
        binding.sliderFocus.valueFrom = focusRange.first
        binding.sliderFocus.valueTo = focusRange.second
        println("Range from: ${focusRange.first}, to: ${focusRange.second}")

        binding.switchManualFocus.setOnCheckedChangeListener { _, isChecked ->
            binding.sliderFocus.visibility = if (isChecked) View.VISIBLE else View.GONE
            cameraSettings.isManualFocusEnabled = isChecked
            cameraSettings.manualFocus = binding.sliderFocus.value
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.sliderFocus.addOnChangeListener(OnChangeListener { _, value, _ ->
            cameraSettings.manualFocus = value
            fibrichecker.setCameraSettings(cameraSettings)
        })
    }

    private fun initExposure() {
        binding.sliderIso.valueFrom = cameraInfo.isoRange.first.toFloat()
        binding.sliderIso.valueTo = cameraInfo.isoRange.second.toFloat() + 1
        binding.groupManualExposure.visibility = View.GONE

        binding.switchManualExposure.setOnCheckedChangeListener { buttonView, isChecked ->
            binding.groupManualExposure.visibility = if (isChecked) View.VISIBLE else View.GONE
            cameraSettings.isManualExposureEnabled = isChecked
            cameraSettings.manualExposureTime = dividerToExposureTime(binding.sliderExposureTime.value)
            cameraSettings.manualIsoValue = binding.sliderIso.value.toInt()
            fibrichecker.setCameraSettings(cameraSettings)
        }

        binding.sliderIso.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                cameraSettings.manualIsoValue = slider.value.toInt()
                binding.textIso.text = "ISO: ${slider.value.toInt()}"
                fibrichecker.setCameraSettings(cameraSettings)
            }
        })

        binding.sliderExposureTime.addOnSliderTouchListener(object: Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                cameraSettings.manualExposureTime = dividerToExposureTime(slider.value)
                binding.textExposureTime.text = "Exposure time: 1/${slider.value}"
                fibrichecker.setCameraSettings(cameraSettings)
            }
        })
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