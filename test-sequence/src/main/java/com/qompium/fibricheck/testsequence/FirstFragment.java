package com.qompium.fibricheck.testsequence;

import android.Manifest;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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

import com.qompium.fibricheck.camerasdk.FibriChecker;
import com.qompium.fibricheck.camerasdk.listeners.FibriListener;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData;
import com.qompium.fibricheck.testsequence.databinding.FragmentFirstBinding;

import java.util.List;

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

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

        testSequenceManager = new TestSequenceManager();
        testSequenceManager.setListener(this);

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

        fibriChecker.sampleTime = 10;

        // vary timeouts based on current step
        int step = getCurrentStepNumber();
        if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
            fibriChecker.fingerDetectionExpiryTime = 3000;
            fibriChecker.pulseDetectionExpiryTime = 10000;
        } else if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
            fibriChecker.fingerDetectionExpiryTime = -1; // No timeout - wait for user to place finger
            fibriChecker.pulseDetectionExpiryTime = 1000; // 1 second for quick pulse timeout test
        } else {
            // No timeout - wait for user to place finger
            fibriChecker.fingerDetectionExpiryTime = -1;
            // 30 seconds for pulse detection, this can take a while
            fibriChecker.pulseDetectionExpiryTime = 30000;
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

                    testSequenceManager.onEvent("onFingerDetected");

                    if (step == TestSequenceManager.STEP_PLACE_FINGER) {
                        setStatusMessage("Finger detected - verifying data stream...", StatusType.SUCCESS);
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

                    testSequenceManager.onEvent("onFingerRemoved");

                    if (step >= TestSequenceManager.STEP_PLACE_FINGER && step <= TestSequenceManager.STEP_RECORDING) {
                        setStatusMessage("Finger removed - place finger back", StatusType.WARNING);
                    }
                });
            }

            @Override
            public void onCalibrationReady() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onCalibrationReady");
                    testSequenceManager.onEvent("onCalibrationReady");
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
                    // Complete step 9 (Recording in Progress) first if still on it
                    int step = getCurrentStepNumber();
                    if (step == TestSequenceManager.STEP_RECORDING) {
                        testSequenceManager.onEvent("onTimeRemaining");
                    }
                    // Then complete step 10 (Recording Finished)
                    testSequenceManager.onEvent("onMeasurementFinished");
                    setStatusMessage("Recording finished", StatusType.SUCCESS);
                });
            }

            @Override
            public void onMeasurementStart(long timestamp) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementStart");
                    testSequenceManager.onEvent("onMeasurementStart");
                    setStatusMessage("Recording started", StatusType.SUCCESS);
                });
            }

            @Override
            public void onFingerDetectionTimeExpired() {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onFingerDetectionTimeExpired");
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING_FINISHED) {
                        Log.d(TAG, "Ignoring finger timeout on step " + step + " (post-recording)");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_FINGER_TIMEOUT) {
                        testSequenceManager.onEvent("onFingerDetectionTimeExpired");
                        autoProceedAfterTimeout("Finger timeout test passed!");
                        return;
                    }
                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
                        Log.d(TAG, "Ignoring finger timeout on step 3");
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

                    if (step >= TestSequenceManager.STEP_RECORDING_FINISHED) {
                        Log.d(TAG, "Ignoring pulse timeout on step " + step + " (post-recording)");
                        return;
                    }

                    if (step == TestSequenceManager.STEP_PULSE_TIMEOUT) {
                        testSequenceManager.onEvent("onPulseDetectionTimeExpired");
                        autoProceedAfterTimeout("Pulse timeout test passed!");
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

                    if (step >= TestSequenceManager.STEP_RECORDING_FINISHED) {
                        Log.d(TAG, "Ignoring movement on step " + step + " (post-recording)");
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
                    testSequenceManager.onEvent("onMeasurementProcessed");
                    double hr = measurementData.heartrate;
                    setStatusMessage("Complete - HR: " + String.format("%.0f", hr) + " BPM", StatusType.SUCCESS);
                    showCameraPlaceholder(true);
                });
            }

            @Override
            public void onMeasurementError(String message) {
                requireActivity().runOnUiThread(() -> {
                    updateDebugEvent("onMeasurementError", message);
                    int step = getCurrentStepNumber();

                    if (step >= TestSequenceManager.STEP_RECORDING_FINISHED) {
                        Log.w(TAG, "Error received during step " + step + ": " + message);
                        setStatusMessage("Warning: " + message, StatusType.WARNING);
                        return;
                    }
                    stopAndFail("Error: " + message);
                });
            }
        });
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
        showCameraPlaceholder(false);
        fibriChecker.record();

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
        SUCCESS, WARNING, ERROR, INFO
    }

    private void setStatusMessage(String message, StatusType type) {
        if (textStatusMessage == null)
            return;

        String icon;
        int bgColor;
        int textColor;

        switch (type) {
            case SUCCESS:
                icon = "✓";
                bgColor = Color.parseColor("#E6F4F1");
                textColor = Color.parseColor("#0F676D");
                break;
            case WARNING:
                icon = "⚠";
                bgColor = Color.parseColor("#FFF3E0");
                textColor = Color.parseColor("#E65100");
                break;
            case ERROR:
                icon = "✗";
                bgColor = Color.parseColor("#FFEBEE");
                textColor = Color.parseColor("#C62828");
                break;
            case INFO:
            default:
                icon = "●";
                bgColor = Color.parseColor("#E6F4F1");
                textColor = Color.parseColor("#1E8D95");
                break;
        }

        textStatusMessage.setText(icon + " " + message);
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
        // Advance the step so initMeasurement() uses correct timeout values
        testSequenceManager.start();
        testSequenceManager.onEvent("START");

        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }
        initMeasurement();

        showCameraPlaceholder(false);
        fibriChecker.record();
        setProceedButtonState("STOP", true);
        buttonProceed.setOnClickListener(v -> stopMeasurement());

        int step = getCurrentStepNumber();
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

        buttonRestart.setOnClickListener(v -> resetSequence());
        buttonProceed.setOnClickListener(v -> startMeasurement());

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
        setProceedButtonState("START", true);
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

    private void retryCurrentStep() {
        if (fibriChecker != null) {
            fibriChecker.stop();
            fibriChecker = null;
        }

        testSequenceManager.retryCurrentStep();
        clearStatusMessage();

        // Reset card color if it was red from failure
        if (cardCurrentStep != null) {
            cardCurrentStep.setCardBackgroundColor(Color.parseColor("#E6F4F1"));
        }

        initMeasurement();
        showCameraPlaceholder(false);
        fibriChecker.record();

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
    public void onDestroyView() {
        super.onDestroyView();
        if (fibriChecker != null) {
            fibriChecker.stop();
        }
        binding = null;
    }
}
