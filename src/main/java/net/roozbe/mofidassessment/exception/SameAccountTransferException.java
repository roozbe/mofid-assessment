package net.roozbe.mofidassessment.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException(String accountId) {
        super("Cannot transfer to the same account: " + accountId);
    }
}
