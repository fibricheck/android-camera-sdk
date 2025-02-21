package com.qompium.fibricheckexample_kotlin.ui.tester

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheck.camerasdk.FibriChecker.FibriBuilder
import com.qompium.fibricheck.camerasdk.listeners.FibriListener
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData
import com.qompium.fibricheckexample_kotlin.databinding.FragmentTesterBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FragmentTester : Fragment() {
    private val TAG = "FC-Example"
    private lateinit var fibriChecker: FibriChecker

    private var formatter = SimpleDateFormat("mm:ss:SSS", Locale.getDefault())

    private var triggeredEvents = mutableMapOf<String, Boolean>()

    private var _binding: FragmentTesterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTesterBinding.inflate(inflater, container, false)
        triggeredEvents = mutableMapOf(
            "onFingerDetected" to false,
            "onFingerRemoved" to false,
            "onHeartBeat" to false,
            "onPulseDetected" to false,
            "onCalibrationReady" to false,
            "onPulseDetectionTimeExpired" to false,
            "onFingerDetectionTimeExpired" to false,
            "onMovementDetected" to false,
            "onMeasurementStart" to false,
            "onMeasurementFinished" to false,
            "onMeasurementError" to false,
            "onMeasurementProcessed" to false,
            "onSampleReady" to false,
            "onTimeRemaining" to false
        )

        // Request Camera Permissions
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                //Permission Granted
                Log.i(TAG, "Camera Permission granted")
            } else {
                // Permission Denied
                Log.i(TAG, "Camera Permission Denied")
            }
        }.launch(Manifest.permission.CAMERA)

        return binding.getRoot()
    }

    private fun updateEventUI() {
        val newText = triggeredEvents.map {
            if (it.value) {
                "✅ ${it.key}\n"
            } else {
                "❌ ${it.key}\n"
            }
        }.joinToString("")

        binding.eventOverview.text = newText
    }

    private fun logEvent(event: String, doLog: Boolean) {
        triggeredEvents[event] = true
        updateEventUI()

        if (doLog) {
            addLog(event)
        }
    }

    private fun addLog(txt: String) {
        var currText = binding.eventLog.text as String

        currText += "${formatter.format(Date())} $txt\n".trim()
        binding.eventLog.text = currText
    }

    private fun validateMeasurement(measurementData: MeasurementData) {
        try {
            val gson = Gson()
            val jsonObject = JSONObject(gson.toJson(measurementData))

            if (jsonObject.has("heartrate")
                && jsonObject.has("attempts")
                && jsonObject.has("skippedMovementDetection")
                && jsonObject.has("skippedPulseDetection")
                && jsonObject.has("skippedFingerDetection")
                && jsonObject.has("quadrants")
                && jsonObject.has("technical_details")
                && jsonObject.has("time")
                && jsonObject.has("measurement_timestamp")
            ) {
                addLog("VALID measurement")
            } else {
                addLog("FAILED to validate measurement")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            addLog("FAILED to parse measurement")
        }
    }

    private fun initialiseMeasurement() {
        val viewGroup: ViewGroup = binding.getRoot()

        fibriChecker = FibriBuilder(viewGroup.context, viewGroup).build()

        fibriChecker.sampleTime = 20
        fibriChecker.fingerDetectionExpiryTime = 10000
        fibriChecker.pulseDetectionExpiryTime = 10000

        fibriChecker.setFibriListener(object : FibriListener() {
            override fun onSampleReady(ppg: Double, raw: Double) {
                logEvent("onSampleReady", false)
            }

            override fun onFingerDetected() {
                logEvent("onFingerDetected", true)
            }

            override fun onFingerRemoved(y: Double, v: Double, stdDevY: Double) {
                logEvent("onFingerRemoved", true)
            }

            override fun onCalibrationReady() {
                logEvent("onCalibrationReady", true)
            }

            override fun onHeartBeat(value: Int) {
                logEvent("onHeartBeat", false)
            }

            override fun onTimeRemaining(seconds: Int) {
                logEvent("onTimeRemaining", false)
            }

            override fun onMeasurementFinished(timestamp: Long) {
                logEvent("onMeasurementFinished", true)
            }

            override fun onMeasurementStart(timestamp: Long) {
                logEvent("onMeasurementStart", true)
            }

            override fun onFingerDetectionTimeExpired() {
                logEvent("onFingerDetectionTimeExpired", true)
            }

            override fun onPulseDetected() {
                logEvent("onPulseDetected", true)
            }

            override fun onPulseDetectionTimeExpired() {
                logEvent("onPulseDetectionTimeExpired", true)
            }

            override fun onMovementDetected() {
                logEvent("onMovementDetected", true)
            }

            override fun onMeasurementProcessed(measurementData: MeasurementData) {
                logEvent("onMeasurementProcessed", true)
                validateMeasurement(measurementData)
            }

            override fun onMeasurementError(message: String?) {
                logEvent("onMeasurementError", true)
            }
        })
    }

    private fun startMeasurement() {
        addLog("START MEASUREMENT BUTTON-PRESS")
        fibriChecker.start()
        Log.i(TAG, "Start FibriCheck Measurement")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initialiseMeasurement()
        binding.buttonFirst.setOnClickListener { startMeasurement() }
        updateEventUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fibriChecker.stop()
    }
}