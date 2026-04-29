package com.qompium.fibricheck.testsequence;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TestSequenceManagerTest {

    private TestSequenceManager manager;

    @Before
    public void setUp() {
        manager = new TestSequenceManager();
    }

    // Initialization

    @Test
    public void initStepsAreInCorrectOrder() {
        String[] expectedEvents = {
                "START",
                "onFingerDetectionTimeExpired",
                "onPulseDetectionTimeExpired",
                "onFingerDetected",
                "onSampleReady",
                "onHeartBeat",
                "onPulseDetected",
                "onCalibrationReady",
                "onFingerRemoved",
                "onMovementDetected",
                "onMeasurementStart",
                "onTimeRemaining",
                "onMeasurementFinished",
                "onMeasurementProcessed",
                "onMeasurementValidated"
        };

        List<TestStep> steps = manager.getSteps();
        for (int i = 0; i < expectedEvents.length; i++) {
            assertEquals("Step " + i + " should have event " + expectedEvents[i],
                    expectedEvents[i], steps.get(i).getExpectedEvent());
        }
    }

    @Test
    public void initAllStepsArePending() {
        for (TestStep step : manager.getSteps()) {
            assertEquals(TestStep.Status.PENDING, step.getStatus());
        }
    }

    @Test
    public void initHasNoCurrentStep() {
        assertEquals(-1, manager.getCurrentStepIndex());
    }

    @Test
    public void initEachStepHasUniqueExpectedEvent() {
        List<TestStep> steps = manager.getSteps();
        long uniqueCount = steps.stream()
                .map(TestStep::getExpectedEvent)
                .distinct()
                .count();
        assertEquals("Each step should have a unique expected event",
                steps.size(), uniqueCount);
    }

    // start()

    @Test
    public void startSetsFirstStepToCurrent() {
        manager.start();

        assertEquals(0, manager.getCurrentStepIndex());
        assertEquals(TestStep.Status.CURRENT, manager.getSteps().get(0).getStatus());
    }

    @Test
    public void startAfterProgressResetsAllStepsToPending() {
        manager.start();
        manager.onEvent("START");
        manager.onEvent("onFingerDetectionTimeExpired");

        manager.start();

        assertEquals(0, manager.getCurrentStepIndex());
        assertEquals(TestStep.Status.CURRENT, manager.getSteps().get(0).getStatus());
        for (int i = 1; i < manager.getSteps().size(); i++) {
            assertEquals("Step " + i + " should be pending after restart",
                    TestStep.Status.PENDING, manager.getSteps().get(i).getStatus());
        }
    }

    // onEvent()

    @Test
    public void onEventMatchingEventCompletesCurrentAndAdvancesToNext() {
        manager.start();
        manager.onEvent("START");

        assertEquals(1, manager.getCurrentStepIndex());
        assertEquals(TestStep.Status.COMPLETED, manager.getSteps().get(0).getStatus());
        assertEquals(TestStep.Status.CURRENT, manager.getSteps().get(1).getStatus());
    }

    @Test
    public void onEventNonMatchingEventDoesNotAdvance() {
        manager.start();
        manager.onEvent("onFingerDetected");

        assertEquals(0, manager.getCurrentStepIndex());
        assertEquals(TestStep.Status.CURRENT, manager.getSteps().get(0).getStatus());
    }

    @Test
    public void onEventBeforeStartIsIgnored() {
        manager.onEvent("START");

        assertEquals(-1, manager.getCurrentStepIndex());
    }

    @Test
    public void onEventAfterCompletionIsIgnored() {
        manager.start();
        for (TestStep step : manager.getSteps()) {
            manager.onEvent(step.getExpectedEvent());
        }

        int finalIndex = manager.getCurrentStepIndex();
        manager.onEvent("START");

        assertEquals(finalIndex, manager.getCurrentStepIndex());
    }

    @Test
    public void onEventAllEventsInOrderCompletesEntireSequence() {
        String[] expectedEvents = {
                "START",
                "onFingerDetectionTimeExpired",
                "onPulseDetectionTimeExpired",
                "onFingerDetected",
                "onSampleReady",
                "onHeartBeat",
                "onPulseDetected",
                "onCalibrationReady",
                "onFingerRemoved",
                "onMovementDetected",
                "onMeasurementStart",
                "onTimeRemaining",
                "onMeasurementFinished",
                "onMeasurementProcessed",
                "onMeasurementValidated"
        };

        manager.start();

        for (String event : expectedEvents) {
            manager.onEvent(event);
        }

        assertEquals(manager.getSteps().size(), manager.getCurrentStepIndex());
        for (TestStep step : manager.getSteps()) {
            assertEquals(TestStep.Status.COMPLETED, step.getStatus());
        }
    }

    @Test
    public void onEventEachEventAdvancesToCorrectNextStep() {
        String[][] transitions = {
                { "START", "onFingerDetectionTimeExpired" },
                { "onFingerDetectionTimeExpired", "onPulseDetectionTimeExpired" },
                { "onPulseDetectionTimeExpired", "onFingerDetected" },
                { "onFingerDetected", "onSampleReady" },
                { "onSampleReady", "onHeartBeat" },
                { "onHeartBeat", "onPulseDetected" },
                { "onPulseDetected", "onCalibrationReady" },
                { "onCalibrationReady", "onFingerRemoved" },
                { "onFingerRemoved", "onMovementDetected" },
                { "onMovementDetected", "onMeasurementStart" },
                { "onMeasurementStart", "onTimeRemaining" },
                { "onTimeRemaining", "onMeasurementFinished" },
                { "onMeasurementFinished", "onMeasurementProcessed" },
                { "onMeasurementProcessed", "onMeasurementValidated" },
        };

        manager.start();

        for (String[] transition : transitions) {
            manager.onEvent(transition[0]);
            TestStep currentStep = manager.getSteps().get(manager.getCurrentStepIndex());
            assertEquals("After " + transition[0] + ", expected event " + transition[1],
                    transition[1], currentStep.getExpectedEvent());
            assertEquals(TestStep.Status.CURRENT, currentStep.getStatus());
        }

        manager.onEvent("onMeasurementValidated");
        assertEquals(manager.getSteps().size(), manager.getCurrentStepIndex());
    }

    // failCurrentStep()

    @Test
    public void failCurrentStepSetsStatusToFailed() {
        manager.start();
        manager.failCurrentStep("Test failure");

        assertEquals(TestStep.Status.FAILED, manager.getSteps().get(0).getStatus());
    }

    @Test
    public void failCurrentStepBeforeStartIsIgnored() {
        manager.failCurrentStep("Should not apply");

        for (TestStep step : manager.getSteps()) {
            assertEquals(TestStep.Status.PENDING, step.getStatus());
        }
    }

    // retryCurrentStep()

    @Test
    public void retryCurrentStepResetsStatusToCurrent() {
        manager.start();
        manager.failCurrentStep("Test failure");
        manager.retryCurrentStep();

        assertEquals(TestStep.Status.CURRENT, manager.getSteps().get(0).getStatus());
    }

    @Test
    public void retryCurrentStepDoesNotChangeStepIndex() {
        manager.start();
        manager.failCurrentStep("Test failure");
        manager.retryCurrentStep();

        assertEquals(0, manager.getCurrentStepIndex());
    }

    // reset()

    @Test
    public void resetAfterProgressClearsAllState() {
        manager.start();
        manager.onEvent("START");
        manager.onEvent("onFingerDetectionTimeExpired");
        manager.failCurrentStep("Some error");

        manager.reset();

        assertEquals(-1, manager.getCurrentStepIndex());
        for (TestStep step : manager.getSteps()) {
            assertEquals(TestStep.Status.PENDING, step.getStatus());
        }
    }

    // Listener

    @Test
    public void listenerOnSequenceCompletedCalledWhenAllStepsDone() {
        final boolean[] completed = { false };
        manager.setListener(new TestSequenceManager.TestSequenceListener() {
            @Override
            public void onStepChanged(int currentStepIndex, TestStep currentStep) {
            }

            @Override
            public void onSequenceCompleted() {
                completed[0] = true;
            }

            @Override
            public void onStepsUpdated(List<TestStep> steps) {
            }

            @Override
            public void onStepFailed(int stepIndex, TestStep step, String reason) {
            }
        });

        manager.start();
        for (TestStep step : manager.getSteps()) {
            manager.onEvent(step.getExpectedEvent());
        }

        assertTrue("Listener should be notified of completion", completed[0]);
    }

    @Test
    public void listenerOnStepFailedCalledWithReason() {
        final String[] failedReason = { null };
        manager.setListener(new TestSequenceManager.TestSequenceListener() {
            @Override
            public void onStepChanged(int currentStepIndex, TestStep currentStep) {
            }

            @Override
            public void onSequenceCompleted() {
            }

            @Override
            public void onStepsUpdated(List<TestStep> steps) {
            }

            @Override
            public void onStepFailed(int stepIndex, TestStep step, String reason) {
                failedReason[0] = reason;
            }
        });

        manager.start();
        manager.failCurrentStep("Camera error");

        assertEquals("Camera error", failedReason[0]);
    }
}
