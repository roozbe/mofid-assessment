package net.roozbe.mofidassessment.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String accountId, long requested, long available) {
        super("Insufficient balance on account " + accountId + ": requested=" + requested + ", available=" + available);
    }
}
