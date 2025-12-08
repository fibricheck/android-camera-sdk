package com.qompium.fibricheckexample_kotlin.ui.tester

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheck.camerasdk.FibriChecker.FibriBuilder
import com.qompium.fibricheck.camerasdk.listeners.FibriListener
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import com.qompium.fibricheck.camerasdk.models.CameraSettingMode
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInput
import com.qompium.fibricheck.camerasdk.models.WhiteBalanceMode
import com.qompium.fibricheckexample_kotlin.databinding.FragmentTesterBinding
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FragmentTester : Fragment() {
    private val TAG = "FC-Example"
    private lateinit var fibriChecker: FibriChecker

    private var formatter = SimpleDateFormat("mm:ss:SSS", Locale.getDefault())
    private var fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private var triggeredEvents = mutableMapOf<String, Boolean>()

    private var _binding: FragmentTesterBinding? = null
    private val binding get() = _binding!!

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "Storage Permission granted")
        } else {
            Log.i(TAG, "Storage Permission Denied")
            Toast.makeText(requireContext(), "Storage permission required to save measurements", Toast.LENGTH_LONG).show()
        }
    }

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

        // Request Storage Permissions for saving measurements
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ doesn't need WRITE_EXTERNAL_STORAGE for app-specific directories
            Log.i(TAG, "Storage permission not needed on Android 10+")
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

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

    private fun saveMeasurementToFile(measurementData: MeasurementData) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(measurementData)

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val timestamp = fileNameFormatter.format(Date())
            val fileName = "fibricheck_measurement_$timestamp.json"
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(jsonString)
            }

            addLog("Saved to: ${file.absolutePath}")
            Log.i(TAG, "Measurement saved to: ${file.absolutePath}")
            Toast.makeText(requireContext(), "Measurement saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save measurement to file", e)
            addLog("FAILED to save measurement: ${e.message}")
            Toast.makeText(requireContext(), "Failed to save measurement: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
                // Log.d("Measurement", jsonObject.toString(2))
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

        fibriChecker = FibriBuilder(viewGroup.context, binding.cameraFinder).build()

        fibriChecker.sampleTime = 20
        fibriChecker.fingerDetectionExpiryTime = 10000
        fibriChecker.pulseDetectionExpiryTime = 10000
        fibriChecker.setCameraSettings(CameraSettingsInput(
            CameraSettingMode.Locked, 0, 0,
            WhiteBalanceMode.Auto, Vec3f(), 6504,
            CameraSettingMode.Auto, 0f,
            true,
            true,
            true
        ))

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
                saveMeasurementToFile(measurementData)
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