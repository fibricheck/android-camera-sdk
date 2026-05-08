package com.qompium.fibricheck.testsequence;

public class TestStep {
    public enum Status {
        PENDING,
        CURRENT,
        COMPLETED,
        FAILED
    }

    private final int stepNumber;
    private final String title;
    private final String instruction;
    private final String expectedEvent;
    private Status status;

    public TestStep(int stepNumber, String title, String instruction, String expectedEvent) {
        this.stepNumber = stepNumber;
        this.title = title;
        this.instruction = instruction;
        this.expectedEvent = expectedEvent;
        this.status = Status.PENDING;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getExpectedEvent() {
        return expectedEvent;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
