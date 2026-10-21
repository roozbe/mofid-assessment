package net.roozbe.mofidassessment.service;

import net.roozbe.mofidassessment.service.model.AccountModel;

public interface BalanceService {
    void credit(String accountId, long amount, String transactionId);

    void debit(String accountId, long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId);

    long getBalance(String accountId);

    AccountModel createAccount(AccountModel model);
}
