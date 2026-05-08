package com.qompium.fibricheck.testsequence;

import android.Manifest;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qompium.fibricheck.camerasdk.FibriChecker;
import com.qompium.fibricheck.camerasdk.listeners.FibriListener;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementCameraSettings;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData;
import com.qompium.fibricheck.testsequence.databinding.FragmentFirstBinding;

import java.util.List;
import java.util.Map;

public class FirstFragment extends Fragment implements TestSequenceManager.TestSequenceListener {

    private final String TAG = "FC-Example";
    private FragmentFirstBinding binding;
    private FibriChecker fibriChecker;

    private TestSequenceManager testSequenceManager;

    private TextView textCurrentStepNumber;
    private TextView textCurrentStepTitle;
    private TextView textCurrentStepInstruction;
    private TextView textStatusMessage;
    private TextView textDebugEvent;
    private CardView cardCurrentStep;
    private LinearLayout layoutStepsList;
    private ScrollView scrollSteps;
    private ImageButton buttonRestart;
    private Button buttonProceed;
    private Button buttonSkip;
    private Button buttonViewSettings;
    private MeasurementCameraSettings lastCameraSettings;
    private boolean pendingBackgroundingConfirm = false;
    private boolean isAccEnabled = false;
    private boolean isGyroEnabled = false;
    private boolean isGravEnabled = false;
    private boolean isRotationEnabled = false;

    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

        testSequenceManager = new TestSequenceManager();
        testSequenceManager.setListener(this);
        container.setKeepScreenOn(true);

        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Log.i(TAG, "Camera Permission granted");
            } else {
                Log.i(TAG, "Camera Permission Denied");
            }
        }).launch(Manifest.permission.CAMERA);

        return binding.getRoot();
    }

    private ViewGroup getCameraContainer() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            ViewGroup container = activity.getCameraPreviewContainer();
            if (container != null) {
                return container;
            }
        }
        return binding.getRoot();
    }

    private void showCameraPlaceholder(boolean show) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showCameraPlaceholder(show);
        }
    }

    private int getCurrentStepNumber() {
        return testSequenceManager.getCurrentStepIndex() + 1;
    }

    private void initMeasurement() {
        ViewGroup cameraContainer = getCameraContainer();

        fibriChecker = new FibriChecker.FibriBuilder(cameraContainer.getContext(), cameraContainer).build();

        // Adjust settings
        fibriChecker.sampleTime = 10;
        // fibriChecker.movementDetectionEnabled = true;
        // fibriChecker.fingerDetectionExpiryTime = 0;
        // fibriChecker.pulseDetectionExpiryTime = 0;
       
        isAccEnabled = fibriChecker.accEnabled;
        isGyroEnabled = fibriChecker.gyroEnabled;
        isGravEnabled = fibriChecker.gravEnabled;
        isRotationEnabled = fibriChecker.rotationEnabled;

        // vary timeouts based on current step
        int step = getCurrentStepNumber();
        if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
            fibriChecker.fingerDetectionExpiryTime = 3;
            fibriChecker.pulseDetectionExpiryTime = 10;
        } else if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
            fibriChecker.fingerDetectionExpiryTime = -1; // No timeout - wait for user to place finger
            fibriChecker.pulseDetectionExpiryTime = 1; // 1 second for quick pulse timeout test
        } else if (step == TestSequenceManager.STEP_BACKGROUNDING) {
            fibriChecker.fingerDetectionExpiryTime = -1; // No timeout - SDK must survive backgrounding
            fibriChecker.pulseDetectionExpiryTime = -1;
        } else if (step == TestSequenceManager.STEP_CALIBRATION
                || step == TestSequenceManager.STEP_MOVEMENT_DETECTED
                || step == TestSequenceManager.STEP_RECORDING_START) {
            fibriChecker.fingerDetectionExpiryTime = -1; // No timeout - wait for user to place finger
            fibriChecker.pulseDetectionExpiryTime = -1; // No timeout - finger is already on camera
        } else {
            // No timeout - wait for user to place finger
            fibriChecker.fingerDetectionExpiryTime = -1;
            // 30 seconds for pulse detection, this can take a while
            fibriChecker.pulseDetectionExpiryTime = 30;
        }

        Log.d(TAG, "initMeasurement: step=" + step +
                ", fingerTimeout=" + fibriChecker.fingerDetectionExpiryTime +
                ", pulseTimeout=" + fibriChecker.pulseDetectionExpiryTime);

        fibriChecker.setFibriListener(new FibriListener() {

            @Override
            public void onSampleReady(final double ppg, double raw) {
                if (getCurrentStepNumber() == TestSequenceManager.STEP_SAMPLE_READY) {
                    requireActivity().runOnUiThread(() -> {
                        updateDebugEvent("onSampleReady");
                        testSequenceManager.onEvent("onSampleReady");
                        setStatusMessage("Camera data stream active", StatusType.SUCCESS);
                        // When pulse detection is skipped, onHeartBeat and onPulseDetected
                        // won't fire — skip both steps so onCalibrationReady lands on step 8.
                        if (fibriChecker != null && fibriChecker.pulseDetectionExpiryTime == 0) {
                            testSequenceManager.skipCurrentStep(); // heartbeat
                            testSequenceManager.skipCurrentStep(); // pulse
                        }
                    });
                }
            }

            @Override
            public void onFingerDetected() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onFingerDetected");
                    int step = getCurrentStepNumber();

                    if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
                        stopAndFail("Finger detected - you should NOT place finger during this test");
                        return;
                    }

                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
                        setStatusMessage("Finger detected - waiting for pulse timeout...", StatusType.INFO);
                        return;
                    }

                    if (step == TestSequenceManager.STEP_BACKGROUNDING) {
                        setStatusMessage("Finger detected - now press the Home button to background the app", StatusType.INFO);
                        return;
                    }

                    testSequenceManager.onEvent("onFingerDetected");

                    if (step == TestSequenceManager.STEP_PLACE_FINGER) {
                        setStatusMessage("Finger detected - verifying data stream...", StatusType.SUCCESS);
                        return;
                    }
                    if (step == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
                        setStatusMessage("Finger detected - waiting for recording to start...", StatusType.INFO);
                        return;
                    }
                    setStatusMessage("Finger detected", StatusType.SUCCESS);
                });
            }

            @Override
            public void onFingerRemoved(double y, double v, double stdDevY) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onFingerRemoved");
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING_FINISHED) {
                        Log.d(TAG, "Ignoring finger removed on step " + step + " (post-recording)");
                        return;
                    }

                    if (step == TestSequenceManager.STEP_FINGER_REMOVED) {
                        // Advance first — onStepChanged for step 10 sets "Waiting for recording
                        // to start...", so we must not call setStatusMessage afterwards.
                        testSequenceManager.onEvent("onFingerRemoved");
                        return;
                    }

                    if (step >= TestSequenceManager.STEP_PLACE_FINGER) {
                        setStatusMessage("Finger removed - place finger back", StatusType.WARNING);
                    }
                });
            }

            @Override
            public void onCalibrationReady() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onCalibrationReady");
                    testSequenceManager.onEvent("onCalibrationReady");
                    int step = getCurrentStepNumber();
                    if (step == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
                        // Recording is about to start — keep the step-specific status visible.
                        setStatusMessage("Calibrating - recording starting soon...", StatusType.INFO);
                        return;
                    }
                    // When finger detection is disabled, onFingerRemoved won't fire. Skip step 9
                    // now so onMeasurementStart (enqueued next) lands on recordingStart.
                    // Step 10 (movementDetected) is handled automatically by onStepChanged.
                    if (fibriChecker != null && fibriChecker.fingerDetectionExpiryTime == 0) {
                        testSequenceManager.skipCurrentStep(); // fingerRemoved
                    }
                    setStatusMessage("Calibration complete", StatusType.SUCCESS);
                });
            }

            @Override
            public void onHeartBeat(int value) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onHeartBeat", "BPM=" + value);
                    testSequenceManager.onEvent("onHeartBeat");
                    setStatusMessage("Heartbeat detected - BPM: " + value, StatusType.SUCCESS);
                });
            }

            @Override
            public void onTimeRemaining(int seconds) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onTimeRemaining", seconds + "s");

                    int step = getCurrentStepNumber();
                    if (step >= TestSequenceManager.STEP_RECORDING_START) {
                        setStatusMessage("Recording... " + seconds + "s remaining", StatusType.INFO);
                    }
                });
            }

            @Override
            public void onMeasurementFinished(long timestamp) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementFinished");
                    int step = getCurrentStepNumber();

                    if (step == TestSequenceManager.STEP_FINGER_REMOVED) {
                        stopAndFail("Recording finished before finger was removed - retry and lift finger sooner");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
                        stopAndFail("Recording finished before movement was detected - retry and shake sooner");
                        return;
                    }
                    // Advance STEP_RECORDING (12) if still on it before completing STEP_RECORDING_FINISHED (13)
                    if (step == TestSequenceManager.STEP_RECORDING) {
                        testSequenceManager.onEvent("onTimeRemaining");
                    }
                    testSequenceManager.onEvent("onMeasurementFinished");
                    setStatusMessage("Recording finished", StatusType.SUCCESS);
                });
            }

            @Override
            public void onMeasurementStart(long timestamp) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementStart");
                    int step = getCurrentStepNumber();
                    if (step == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
                        if (textCurrentStepInstruction != null) {
                            textCurrentStepInstruction.setText("Recording in progress — shake the device now!");
                        }
                        setStatusMessage("Recording started - shake the device now!", StatusType.SUCCESS);
                        return;
                    }
                    testSequenceManager.onEvent("onMeasurementStart");
                    setStatusMessage("Recording started", StatusType.SUCCESS);
                });
            }

            @Override
            public void onFingerDetectionTimeExpired() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onFingerDetectionTimeExpired");
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING) {
                        Log.d(TAG, "Ignoring finger timeout on step " + step + " (recording in progress)");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
                        testSequenceManager.onEvent("onFingerDetectionTimeExpired");
                        autoProceedAfterTimeout("Finger timeout test passed!");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT
                            || step == TestSequenceManager.STEP_BACKGROUNDING) {
                        Log.d(TAG, "Ignoring finger timeout on step " + step);
                        return;
                    }
                    stopAndFail("Finger detection timed out");
                });
            }

            @Override
            public void onPulseDetected() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onPulseDetected");
                    int step = getCurrentStepNumber();

                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
                        stopAndFail("Pulse detected - finger should be loose for this test");
                        return;
                    }

                    testSequenceManager.onEvent("onPulseDetected");
                    setStatusMessage("Pulse pattern detected", StatusType.SUCCESS);
                });
            }

            @Override
            public void onPulseDetectionTimeExpired() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onPulseDetectionTimeExpired");
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING) {
                        Log.d(TAG, "Ignoring pulse timeout on step " + step + " (recording in progress)");
                        return;
                    }

                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
                        testSequenceManager.onEvent("onPulseDetectionTimeExpired");
                        autoProceedAfterTimeout("Pulse timeout test passed!");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_BACKGROUNDING) {
                        Log.d(TAG, "Ignoring pulse timeout on step " + step);
                        return;
                    }
                    stopAndFail("Pulse detection timed out - try holding more steady");
                });
            }

            @Override
            public void onMovementDetected() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMovementDetected");
                    int step = getCurrentStepNumber();

                    // Shaking generates many events. Once step 10 is done, all subsequent steps
                    // (STEP_RECORDING_START=11 and beyond) must be protected from lingering shake
                    // events so they can never be failed by movement from step 10.
                    if (step >= TestSequenceManager.STEP_RECORDING_START) {
                        return;
                    }

                    if (step == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
                        testSequenceManager.onEvent("onMovementDetected");
                        setStatusMessage("Movement detected - test passed!", StatusType.SUCCESS);
                        return;
                    }

                    stopAndFail("Movement detected - hold steady");
                });
            }

            @Override
            public void onMeasurementProcessed(MeasurementData measurementData) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementProcessed",
                            "HR=" + String.format("%.0f", measurementData.heartrate));
                    // Ignore early completions — can happen if sampleTime is too short and the
                    // recording finishes before the user reaches the validation step.
                    if (getCurrentStepNumber() < TestSequenceManager.STEP_PROCESSING) {
                        Log.w(TAG, "onMeasurementProcessed fired too early on step " + getCurrentStepNumber() + " — ignoring");
                        showCameraPlaceholder(true);
                        return;
                    }
                    testSequenceManager.onEvent("onMeasurementProcessed");
                    showCameraPlaceholder(true);

                    String validationError = validateMeasurement(measurementData);
                    if (validationError != null) {
                        stopAndFail("Validation failed: " + validationError);
                    } else {
                        testSequenceManager.onEvent("onMeasurementValidated");
                        lastCameraSettings = measurementData.cameraSettings;
                        if (buttonViewSettings != null) buttonViewSettings.setVisibility(View.VISIBLE);
                        Log.d(TAG, "skippedFingerDetection=" + measurementData.skippedFingerDetection
                                + ", skippedPulseDetection=" + measurementData.skippedPulseDetection
                                + ", skippedMovementDetection=" + measurementData.skippedMovementDetection);
                        setStatusMessage(
                                "skippedFingerDetection: " + measurementData.skippedFingerDetection + "\n" +
                                "skippedPulseDetection: " + measurementData.skippedPulseDetection + "\n" +
                                "skippedMovementDetection: " + measurementData.skippedMovementDetection,
                                StatusType.TEXT);
                    }
                });
            }

            @Override
            public void onMeasurementError(String message) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementError", message);
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING) {
                        Log.w(TAG, "Error received during step " + step + ": " + message);
                        setStatusMessage("Warning: " + message, StatusType.WARNING);
                        return;
                    }
                    stopAndFail("Error: " + message);
                });
            }
        });
    }

    private String validateMeasurement(MeasurementData data) {
        if (data == null) return "Measurement is null";
        if (data.quadrants == null || data.quadrants.isEmpty()) return "quadrants is missing or empty";
        if (data.time == null || data.time.isEmpty()) return "time is missing or empty";
        if (data.measurementTimestamp == null) return "measurement_timestamp is missing";

        if (isAccEnabled && (data.acc == null || data.acc.x == null || data.acc.x.isEmpty()))
            return "acc data is missing or empty — accelerometer was enabled but produced no data";
        if (isGyroEnabled && (data.gyro == null || data.gyro.x == null || data.gyro.x.isEmpty()))
            return "gyro data is missing or empty — gyroscope was enabled but produced no data";
        if (isGravEnabled && (data.grav == null || data.grav.x == null || data.grav.x.isEmpty()))
            return "grav data is missing or empty — gravitation was enabled but produced no data";
        if (isRotationEnabled && (data.rotation == null || data.rotation.x == null || data.rotation.x.isEmpty()))
            return "rotation data is missing or empty — rotation was enabled but produced no data";

        if (data.technical_details == null) return "technical_details is missing";
        Object cameraHdr = data.technical_details.get("camera_hdr");
        if (!(cameraHdr instanceof String) || ((String) cameraHdr).isEmpty()) return "technical_details.camera_hdr is missing or empty";
        Object cameraHardwareLevelObj = data.technical_details.get("camera_hardware_level");
        if (!(cameraHardwareLevelObj instanceof String) || ((String) cameraHardwareLevelObj).isEmpty()) return "technical_details.camera_hardware_level is missing or empty";
        Object cameraResolution = data.technical_details.get("camera_resolution");
        if (!(cameraResolution instanceof String) || ((String) cameraResolution).isEmpty()) return "technical_details.camera_resolution is missing or empty";

        if (data.cameraSettings == null) return "camera_settings is missing";
        if (data.cameraSettings.getExposureMode() == null || data.cameraSettings.getExposureMode().isEmpty()) return "camera_settings.exposure_mode is missing or empty";
        if (data.cameraSettings.getHdrProfile() == null || data.cameraSettings.getHdrProfile().isEmpty()) return "camera_settings.hdr_profile is missing or empty";
        if (data.cameraSettings.getHdrMode() == null || data.cameraSettings.getHdrMode().isEmpty()) return "camera_settings.hdr_mode is missing or empty";

        String cameraHardwareLevel = (String) cameraHardwareLevelObj;
        boolean isAdvancedCamera2 = cameraHardwareLevel != null
                && !cameraHardwareLevel.equals("camera2 - limited")
                && !cameraHardwareLevel.equals("camera2 - legacy");

        if (isAdvancedCamera2) {
            if (data.cameraSettings.getFocusMode() == null || data.cameraSettings.getFocusMode().isEmpty()) return "camera_settings.focus_mode is missing or empty";
            if (data.cameraSettings.getFocus() == null || data.cameraSettings.getFocus().isEmpty()) return "camera_settings.focus is missing or empty";
            if (data.cameraSettings.getWhiteBalance() == null || data.cameraSettings.getWhiteBalance().isEmpty()) return "camera_settings.white_balance is missing or empty";
        }

        return null;
    }

    private void stopAndFail(String reason) {
        // Stop the SDK first so it doesn't fire more events
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        showCameraPlaceholder(true);
        testSequenceManager.failCurrentStep(reason);
    }

    private void autoProceedAfterTimeout(String message) {
        // Stop current measurement
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        showCameraPlaceholder(true);
        setStatusMessage(message, StatusType.SUCCESS);

        // Auto-proceed after a brief delay
        binding.getRoot().postDelayed(this::proceedToNextStep, 1500);
    }

    private void proceedToNextStep() {
        // Reinitialize and restart
        initMeasurement();

        int currentStep = getCurrentStepNumber();
        if (currentStep == TestSequenceManager.STEP_FINGER_TIMEOUT && fibriChecker.fingerDetectionExpiryTime == 0) {
            setStatusMessage("Finger detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }
        if (currentStep == TestSequenceManager.STEP_PULSE_TIMEOUT && fibriChecker.pulseDetectionExpiryTime == 0) {
            setStatusMessage("Pulse detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }
        if (currentStep == TestSequenceManager.STEP_PLACE_FINGER && fibriChecker.fingerDetectionExpiryTime == 0) {
            setStatusMessage("Finger detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }

        showCameraPlaceholder(false);
        fibriChecker.start();

        setProceedButtonState("STOP", true);
        buttonProceed.setOnClickListener(v -> stopMeasurement());

        int step = getCurrentStepNumber();
        if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
            setStatusMessage("Place finger loosely - wait for pulse timeout", StatusType.INFO);
            return;
        }
        setStatusMessage("Place your finger firmly on the camera", StatusType.INFO);
    }

    private enum StatusType {
        SUCCESS, WARNING, ERROR, INFO, TEXT
    }

    private void setStatusMessage(String message, StatusType type) {
        if (textStatusMessage == null)
            return;

        String icon;
        int bgColor;
        int textColor;

        switch (type) {
            case SUCCESS:
                icon = "✓ ";
                bgColor = Color.parseColor("#E6F4F1");
                textColor = Color.parseColor("#0F676D");
                break;
            case WARNING:
                icon = "⚠ ";
                bgColor = Color.parseColor("#FFF3E0");
                textColor = Color.parseColor("#E65100");
                break;
            case ERROR:
                icon = "✗ ";
                bgColor = Color.parseColor("#FFEBEE");
                textColor = Color.parseColor("#C62828");
                break;
            case TEXT:
                icon = "";
                bgColor = Color.parseColor("#E6F4F1");
                textColor = Color.parseColor("#1E8D95");
                break;
            case INFO:
            default:
                icon = "● ";
                bgColor = Color.parseColor("#E6F4F1");
                textColor = Color.parseColor("#1E8D95");
                break;
        }

        textStatusMessage.setText(icon + message);
        textStatusMessage.setBackgroundColor(bgColor);
        textStatusMessage.setTextColor(textColor);
        textStatusMessage.setVisibility(View.VISIBLE);
    }

    private void updateDebugEvent(String eventName) {
        updateDebugEvent(eventName, null);
    }

    private void updateDebugEvent(String eventName, String extra) {
        if (textDebugEvent == null)
            return;
        String text = "Last event: " + eventName;
        if (extra != null && !extra.isEmpty()) {
            text += " (" + extra + ")";
        }
        textDebugEvent.setText(text);
        Log.d(TAG, "Event: " + eventName + (extra != null ? " - " + extra : ""));
    }

    private void populateLabelInfo(View view) {
        Map<String, String> label = FibriChecker.getLabel();

        TextView componentName = view.findViewById(R.id.text_label_component_name);
        if (componentName != null) componentName.setText(label.get("componentName"));

        TextView ce = view.findViewById(R.id.text_label_ce);
        if (ce != null) ce.setText(label.get("ceLabel"));

        TextView releaseDate = view.findViewById(R.id.text_label_release_date);
        if (releaseDate != null) releaseDate.setText(label.get("releaseDate"));

        TextView udi = view.findViewById(R.id.text_label_udi);
        if (udi != null) udi.setText(label.get("udi"));

        TextView manufacturer = view.findViewById(R.id.text_label_manufacturer);
        if (manufacturer != null) manufacturer.setText(label.get("manufacturer"));

        TextView ifu = view.findViewById(R.id.text_label_ifu);
        if (ifu != null) ifu.setText(label.get("ifu"));
    }

    private void clearStatusMessage() {
        if (textStatusMessage != null) {
            textStatusMessage.setVisibility(View.GONE);
        }
    }

    private void setProceedButtonState(String text, boolean enabled) {
        if (buttonProceed != null) {
            buttonProceed.setText(text);
            buttonProceed.setEnabled(enabled);
        }
    }

    private void startMeasurement() {
        lastCameraSettings = null;
        if (buttonViewSettings != null) buttonViewSettings.setVisibility(View.GONE);

        // Advance the step so initMeasurement() uses correct timeout values
        testSequenceManager.start();
        testSequenceManager.onEvent("START");

        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        initMeasurement();

        int step = getCurrentStepNumber();
        if (step == TestSequenceManager.STEP_FINGER_TIMEOUT && fibriChecker.fingerDetectionExpiryTime == 0) {
            setStatusMessage("Finger detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }
        if (step == TestSequenceManager.STEP_PULSE_TIMEOUT && fibriChecker.pulseDetectionExpiryTime == 0) {
            setStatusMessage("Pulse detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }
        if (step == TestSequenceManager.STEP_PLACE_FINGER && fibriChecker.fingerDetectionExpiryTime == 0) {
            setStatusMessage("Finger detection disabled — skipping step", StatusType.INFO);
            skipCurrentStep();
            return;
        }

        showCameraPlaceholder(false);
        fibriChecker.start();
        setProceedButtonState("STOP", true);
        buttonProceed.setOnClickListener(v -> stopMeasurement());

        if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
            setStatusMessage("Do NOT place finger - waiting for timeout", StatusType.INFO);
        }

        Log.i(TAG, "Start FibriCheck Measurement");
    }

    private void stopMeasurement() {
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        showCameraPlaceholder(true);
        setStatusMessage("Measurement stopped", StatusType.WARNING);
        setProceedButtonState("START", true);
        buttonProceed.setOnClickListener(v -> startMeasurement());
        Log.i(TAG, "Measurement stopped by user");
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textCurrentStepNumber = view.findViewById(R.id.text_current_step_number);
        textCurrentStepTitle = view.findViewById(R.id.text_current_step_title);
        textCurrentStepInstruction = view.findViewById(R.id.text_current_step_instruction);
        textStatusMessage = view.findViewById(R.id.text_status_message);
        textDebugEvent = view.findViewById(R.id.text_debug_event);
        cardCurrentStep = view.findViewById(R.id.card_current_step);
        layoutStepsList = view.findViewById(R.id.layout_steps_list);
        scrollSteps = view.findViewById(R.id.scroll_steps);
        buttonRestart = view.findViewById(R.id.button_restart);
        buttonProceed = view.findViewById(R.id.button_proceed);
        buttonSkip = view.findViewById(R.id.button_skip);
        buttonViewSettings = view.findViewById(R.id.button_view_settings);

        buttonRestart.setOnClickListener(v -> resetSequence());
        buttonProceed.setOnClickListener(v -> startMeasurement());
        buttonSkip.setOnClickListener(v -> skipCurrentStep());
        buttonViewSettings.setOnClickListener(v -> showCameraSettingsDialog());

        populateLabelInfo(view);

        updateStepsListUI(testSequenceManager.getSteps());
    }

    @Override
    public void onStepChanged(int currentStepIndex, TestStep currentStep) {
        if (textCurrentStepNumber != null) {
            textCurrentStepNumber
                    .setText("Step " + currentStep.getStepNumber() + " of " + testSequenceManager.getSteps().size());
        }
        if (textCurrentStepTitle != null) {
            textCurrentStepTitle.setText(currentStep.getTitle());
        }
        if (textCurrentStepInstruction != null) {
            textCurrentStepInstruction.setText(currentStep.getInstruction());
        }

        if (buttonSkip != null) {
            int stepNumber = currentStep.getStepNumber();
            boolean showSkip = stepNumber == TestSequenceManager.STEP_PULSE;
            buttonSkip.setVisibility(showSkip ? View.VISIBLE : View.GONE);
        }

        if (currentStep.getStepNumber() == TestSequenceManager.STEP_MOVEMENT_DETECTED) {
            if (fibriChecker != null && !fibriChecker.movementDetectionEnabled) {
                setStatusMessage("Movement detection disabled — skipping step", StatusType.INFO);
                testSequenceManager.skipCurrentStep();
                return;
            }
            // The finger was just removed (step 9), so the user must place it back first.
            // Overwrite the generic instruction with a phase-specific one.
            if (textCurrentStepInstruction != null) {
                textCurrentStepInstruction.setText("Please wait for recording to start before shaking the device!");
            }
            setStatusMessage("Waiting for recording to start...", StatusType.INFO);
        }

        scrollToCurrentStep(currentStepIndex);
    }

    private void scrollToCurrentStep(int stepIndex) {
        if (scrollSteps == null || layoutStepsList == null)
            return;
        if (stepIndex < 0 || stepIndex >= layoutStepsList.getChildCount())
            return;

        // Scroll to show the previous step as well (if there is one)
        int targetIndex = Math.max(0, stepIndex - 1);
        View targetView = layoutStepsList.getChildAt(targetIndex);
        if (targetView != null) {
            scrollSteps.post(() -> {
                int scrollY = targetView.getTop();
                scrollSteps.smoothScrollTo(0, Math.max(0, scrollY));
            });
        }
    }

    @Override
    public void onSequenceCompleted() {
        if (cardCurrentStep != null) {
            cardCurrentStep.setCardBackgroundColor(Color.parseColor("#E6F4F1"));
        }
        if (textCurrentStepNumber != null) {
            textCurrentStepNumber.setText("Complete!");
        }
        if (textCurrentStepTitle != null) {
            textCurrentStepTitle.setText("Test Sequence Finished");
            textCurrentStepTitle.setTextColor(Color.parseColor("#0F676D"));
        }
        if (textCurrentStepInstruction != null) {
            textCurrentStepInstruction.setText("All steps completed successfully!");
        }
        setStatusMessage("All tests passed!", StatusType.SUCCESS);
        setProceedButtonState("RESTART", true);
        buttonProceed.setOnClickListener(v -> startMeasurement());
    }

    @Override
    public void onStepFailed(int stepIndex, TestStep step, String reason) {
        setStatusMessage(reason, StatusType.ERROR);

        setProceedButtonState("RETRY", true);
        buttonProceed.setOnClickListener(v -> retryCurrentStep());

        if (cardCurrentStep != null) {
            cardCurrentStep.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
        }
    }

    private void resetSequence() {
        testSequenceManager.reset();
        lastCameraSettings = null;
        if (buttonViewSettings != null) buttonViewSettings.setVisibility(View.GONE);
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        if (cardCurrentStep != null) {
            cardCurrentStep.setCardBackgroundColor(Color.parseColor("#E6F4F1"));
        }
        if (textCurrentStepTitle != null) {
            textCurrentStepTitle.setTextColor(Color.parseColor("#0F676D"));
        }
        clearStatusMessage();
        updateStepsListUI(testSequenceManager.getSteps());
        if (textCurrentStepNumber != null) {
            textCurrentStepNumber.setText("Step 1 of " + testSequenceManager.getSteps().size());
        }
        if (textCurrentStepTitle != null) {
            textCurrentStepTitle.setText("Start Measurement");
        }
        if (textCurrentStepInstruction != null) {
            textCurrentStepInstruction.setText("Tap the START button to begin");
        }
        setProceedButtonState("START", true);
        buttonProceed.setOnClickListener(v -> startMeasurement());
        showCameraPlaceholder(true);
    }

    private void skipCurrentStep() {
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        if (buttonSkip != null) buttonSkip.setVisibility(View.GONE);
        if (cardCurrentStep != null) cardCurrentStep.setCardBackgroundColor(Color.parseColor("#E6F4F1"));
        testSequenceManager.skipCurrentStep();
        proceedToNextStep();
    }

    private void showCameraSettingsDialog() {
        if (lastCameraSettings == null || getContext() == null) return;

        Gson gson = new GsonBuilder().serializeNulls().create();
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16 * dp, 8 * dp, 16 * dp, 8 * dp);

        JsonObject obj = gson.toJsonTree(lastCameraSettings).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonArray()) {
                addSettingsCollapsableRow(container, entry.getKey(), val.getAsJsonArray(), gson, dp);
            } else {
                addSettingsScalarRow(container, entry.getKey(), val.isJsonNull() ? "null" : val.getAsString(), dp);
            }
        }

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(container);

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Camera Settings")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addSettingsScalarRow(LinearLayout parent, String key, String value, int dp) {
        TextView tv = new TextView(getContext());
        tv.setPadding(0, 6 * dp, 0, 6 * dp);
        tv.setTextSize(13f);
        String label = key + ": ";
        SpannableString span = new SpannableString(label + value);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
        tv.setText(span);
        parent.addView(tv);
        addSettingsDivider(parent);
    }

    private void addSettingsCollapsableRow(LinearLayout parent, String key, JsonArray values, Gson gson, int dp) {
        TextView indicator = new TextView(getContext());
        indicator.setText("▶");
        indicator.setTextColor(Color.parseColor("#1E8D95"));
        indicator.setTextSize(12f);
        indicator.setPadding(0, 0, 6 * dp, 0);

        TextView header = new TextView(getContext());
        header.setTextSize(13f);
        String label = key + ": ";
        String full = label + "[" + values.size() + " entries]";
        SpannableString span = new SpannableString(full);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
        header.setText(span);
        header.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setPadding(0, 6 * dp, 0, 6 * dp);
        headerRow.addView(indicator);
        headerRow.addView(header);

        TextView body = new TextView(getContext());
        body.setText(gson.toJson(values));
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(11f);
        body.setPadding(16 * dp, 4 * dp, 0, 6 * dp);
        body.setVisibility(View.GONE);

        headerRow.setOnClickListener(v -> {
            if (body.getVisibility() == View.GONE) {
                body.setVisibility(View.VISIBLE);
                indicator.setText("▼");
            } else {
                body.setVisibility(View.GONE);
                indicator.setText("▶");
            }
        });

        parent.addView(headerRow);
        parent.addView(body);
        addSettingsDivider(parent);
    }

    private void addSettingsDivider(LinearLayout parent) {
        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        parent.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void retryCurrentStep() {
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        lastCameraSettings = null;
        if (buttonViewSettings != null) buttonViewSettings.setVisibility(View.GONE);

        testSequenceManager.retryCurrentStep();
        clearStatusMessage();

        // Reset card color if it was red from failure
        if (cardCurrentStep != null) {
            cardCurrentStep.setCardBackgroundColor(Color.parseColor("#E6F4F1"));
        }

        initMeasurement();
        showCameraPlaceholder(false);
        fibriChecker.start();

        setProceedButtonState("STOP", true);
        buttonProceed.setOnClickListener(v -> stopMeasurement());

        int step = getCurrentStepNumber();
        if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
            setStatusMessage("Do NOT place finger - waiting for timeout", StatusType.INFO);
            return;
        }
        if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
            setStatusMessage("Place finger loosely - waiting for pulse timeout", StatusType.INFO);
            return;
        }
        setStatusMessage("Retrying... Place finger firmly on camera", StatusType.INFO);
    }

    @Override
    public void onStepsUpdated(List<TestStep> steps) {
        updateStepsListUI(steps);
    }

    private void updateStepsListUI(List<TestStep> steps) {
        if (layoutStepsList == null)
            return;

        layoutStepsList.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (TestStep step : steps) {
            View itemView = inflater.inflate(R.layout.item_test_step, layoutStepsList, false);

            TextView textStatus = itemView.findViewById(R.id.text_step_status);
            TextView textTitle = itemView.findViewById(R.id.text_step_title);
            TextView textEvent = itemView.findViewById(R.id.text_step_event);

            textTitle.setText(step.getTitle());
            textEvent.setText(step.getExpectedEvent());

            switch (step.getStatus()) {
                case COMPLETED:
                    textStatus.setText("\u2705");
                    textStatus.setBackgroundColor(Color.TRANSPARENT);
                    textTitle.setTextColor(Color.parseColor("#1E8D95"));
                    textTitle.setTypeface(null, Typeface.NORMAL);
                    itemView.setAlpha(0.7f);
                    break;

                case CURRENT:
                    textStatus.setText("\u25B6");
                    textStatus.setTextColor(Color.WHITE);
                    textStatus.setBackgroundColor(Color.parseColor("#1E8D95"));
                    textTitle.setTextColor(Color.parseColor("#1E8D95"));
                    textTitle.setTypeface(null, Typeface.BOLD);
                    itemView.setAlpha(1f);
                    break;

                case FAILED:
                    textStatus.setText("\u274C");
                    textStatus.setBackgroundColor(Color.TRANSPARENT);
                    textTitle.setTextColor(Color.parseColor("#C62828"));
                    textTitle.setTypeface(null, Typeface.BOLD);
                    itemView.setAlpha(1f);
                    break;

                case PENDING:
                default:
                    textStatus.setText(String.valueOf(step.getStepNumber()));
                    textStatus.setTextColor(Color.parseColor("#9E9E9E"));
                    textStatus.setBackgroundColor(Color.TRANSPARENT);
                    textTitle.setTextColor(Color.parseColor("#757575"));
                    textTitle.setTypeface(null, Typeface.NORMAL);
                    itemView.setAlpha(0.5f);
                    break;
            }

            layoutStepsList.addView(itemView);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getCurrentStepNumber() == TestSequenceManager.STEP_BACKGROUNDING) {
            pendingBackgroundingConfirm = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingBackgroundingConfirm) {
            pendingBackgroundingConfirm = false;
            if (getCurrentStepNumber() == TestSequenceManager.STEP_BACKGROUNDING) {
                if (fibriChecker != null) {
                    setStatusMessage("Back from background! SDK still active. Tap CONFIRM to proceed.", StatusType.SUCCESS);
                } else {
                    setStatusMessage("Back from background, but SDK is no longer running.", StatusType.ERROR);
                }
                setProceedButtonState("CONFIRM", true);
                buttonProceed.setOnClickListener(v -> confirmBackgroundingTest());
            }
        }
    }

    private void confirmBackgroundingTest() {
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        testSequenceManager.onEvent("onBackgroundingVerified");
        autoProceedAfterTimeout("Backgrounding test passed!");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fibriChecker != null) {
            fibriChecker.stop();
        }
        binding = null;
    }
}
