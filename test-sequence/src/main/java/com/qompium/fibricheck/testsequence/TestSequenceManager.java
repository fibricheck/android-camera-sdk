package com.qompium.fibricheck.testsequence;

import java.util.ArrayList;
import java.util.List;

public class TestSequenceManager {

    // Step indices (1-based to match step numbers)
    public static final int STEP_START = 1;
    public static final int STEP_FINGER_TIMEOUT = 2;
    public static final int STEP_PULSE_TIMEOUT = 3;
    public static final int STEP_PLACE_FINGER = 4;
    public static final int STEP_SAMPLE_READY = 5;
    public static final int STEP_HEARTBEAT = 6;
    public static final int STEP_PULSE = 7;
    public static final int STEP_CALIBRATION = 8;
    public static final int STEP_RECORDING_START = 9;
    public static final int STEP_RECORDING = 10;
    public static final int STEP_RECORDING_FINISHED = 11;
    public static final int STEP_PROCESSING = 12;

    public interface TestSequenceListener {
        void onStepChanged(int currentStepIndex, TestStep currentStep);
        void onSequenceCompleted();
        void onStepsUpdated(List<TestStep> steps);
        void onStepFailed(int stepIndex, TestStep step, String reason);
    }

    private final List<TestStep> steps;
    private int currentStepIndex;
    private TestSequenceListener listener;

    public TestSequenceManager() {
        this.steps = new ArrayList<>();
        this.currentStepIndex = -1;
        initializeSteps();
    }

    private void initializeSteps() {
        steps.add(new TestStep(STEP_START, "Start Measurement",
                "Tap the START button to begin",
                "START"));

        steps.add(new TestStep(STEP_FINGER_TIMEOUT, "Test Finger Timeout",
                "Do NOT place finger - wait for timeout",
                "onFingerDetectionTimeExpired"));

        steps.add(new TestStep(STEP_PULSE_TIMEOUT, "Test Pulse Timeout",
                "Place finger loosely - wait for pulse timeout",
                "onPulseDetectionTimeExpired"));

        steps.add(new TestStep(STEP_PLACE_FINGER, "Place Finger",
                "Now cover the camera firmly with your finger",
                "onFingerDetected"));

        steps.add(new TestStep(STEP_SAMPLE_READY, "Sample Ready",
                "Verifying camera data stream...",
                "onSampleReady"));

        steps.add(new TestStep(STEP_HEARTBEAT, "Detect Heartbeat",
                "Keep your finger steady on the camera",
                "onHeartBeat"));

        steps.add(new TestStep(STEP_PULSE, "Detect Pulse",
                "Hold still - detecting pulse pattern...",
                "onPulseDetected"));

        steps.add(new TestStep(STEP_CALIBRATION, "Calibration",
                "Calibrating camera settings...",
                "onCalibrationReady"));

        steps.add(new TestStep(STEP_RECORDING_START, "Recording Started",
                "Recording has begun!",
                "onMeasurementStart"));

        steps.add(new TestStep(STEP_RECORDING, "Recording in Progress",
                "Keep finger on camera until timer ends",
                "onTimeRemaining"));

        steps.add(new TestStep(STEP_RECORDING_FINISHED, "Recording Finished",
                "Recording complete!",
                "onMeasurementFinished"));

        steps.add(new TestStep(STEP_PROCESSING, "Processing",
                "Processing measurement data...",
                "onMeasurementProcessed"));
    }

    public void setListener(TestSequenceListener listener) {
        this.listener = listener;
    }

    public void start() {
        currentStepIndex = 0;
        for (TestStep step : steps) {
            step.setStatus(TestStep.Status.PENDING);
        }
        steps.get(0).setStatus(TestStep.Status.CURRENT);
        notifyStepsUpdated();
        notifyStepChanged();
    }

    public void reset() {
        currentStepIndex = -1;
        for (TestStep step : steps) {
            step.setStatus(TestStep.Status.PENDING);
        }
        notifyStepsUpdated();
    }

    public void onEvent(String eventName) {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return;
        }

        TestStep currentStep = steps.get(currentStepIndex);

        // Check if this event matches the expected event for current step
        if (currentStep.getExpectedEvent().equals(eventName)) {
            completeCurrentStep();
        }
    }

    public void failCurrentStep(String reason) {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return;
        }

        TestStep currentStep = steps.get(currentStepIndex);
        currentStep.setStatus(TestStep.Status.FAILED);
        notifyStepsUpdated();

        if (listener != null) {
            listener.onStepFailed(currentStepIndex, currentStep, reason);
        }
    }

    public void retryCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return;
        }

        // Reset current step to CURRENT status
        steps.get(currentStepIndex).setStatus(TestStep.Status.CURRENT);
        notifyStepsUpdated();
        notifyStepChanged();
    }

    private void completeCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return;
        }

        // Mark current step as completed
        steps.get(currentStepIndex).setStatus(TestStep.Status.COMPLETED);

        // Move to next step
        currentStepIndex++;

        if (currentStepIndex < steps.size()) {
            steps.get(currentStepIndex).setStatus(TestStep.Status.CURRENT);
            notifyStepsUpdated();
            notifyStepChanged();
        } else {
            // Sequence completed
            notifyStepsUpdated();
            if (listener != null) {
                listener.onSequenceCompleted();
            }
        }
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    private void notifyStepChanged() {
        if (listener != null && currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            listener.onStepChanged(currentStepIndex, steps.get(currentStepIndex));
        }
    }

    private void notifyStepsUpdated() {
        if (listener != null) {
            listener.onStepsUpdated(steps);
        }
    }
}
