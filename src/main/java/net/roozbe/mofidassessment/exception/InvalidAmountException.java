package net.roozbe.mofidassessment.exception;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(long amount) {
        super("Invalid amount: " + amount + ". Amount must be greater than zero.");
    }
}
