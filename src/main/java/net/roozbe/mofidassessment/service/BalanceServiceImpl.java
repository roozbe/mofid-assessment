package net.roozbe.mofidassessment.service;

import lombok.AllArgsConstructor;
import net.roozbe.mofidassessment.da.entity.AccountEntity;
import net.roozbe.mofidassessment.da.entity.TransactionEntity;
import net.roozbe.mofidassessment.da.repository.AccountRepository;
import net.roozbe.mofidassessment.da.repository.TransactionRepository;
import net.roozbe.mofidassessment.exception.AccountNotFoundException;
import net.roozbe.mofidassessment.exception.SameAccountTransferException;
import net.roozbe.mofidassessment.mapper.AccountMapper;
import net.roozbe.mofidassessment.service.model.AccountModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class BalanceServiceImpl implements BalanceService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper mapper;

    @Override
    @Transactional
    public void credit(String accountId, long amount, String transactionId) {
        if (transactionRepository.existsByTransactionId(transactionId)) {
            return;
        }

        AccountEntity account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.credit(amount);
        accountRepository.save(account);

        recordTransaction(transactionId, TransactionEntity.TransactionType.CREDIT, accountId, null, amount);
    }

    @Override
    @Transactional
    public void debit(String accountId, long amount, String transactionId) {
        if (transactionRepository.existsByTransactionId(transactionId)) {
            return;
        }

        AccountEntity account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.debit(amount);
        accountRepository.save(account);

        recordTransaction(transactionId, TransactionEntity.TransactionType.DEBIT, accountId, null, amount);
    }

    @Override
    @Transactional
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new SameAccountTransferException(sourceAccountId);
        }

        if (transactionRepository.existsByTransactionId(transactionId)) {
            return;
        }

        String firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
        String secondId = sourceAccountId.compareTo(destinationAccountId) < 0 ? destinationAccountId : sourceAccountId;

        AccountEntity firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        AccountEntity secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        AccountEntity source = firstId.equals(sourceAccountId) ? firstAccount : secondAccount;
        AccountEntity destination = firstId.equals(destinationAccountId) ? firstAccount : secondAccount;

        source.debit(amount);
        destination.credit(amount);

        accountRepository.save(source);
        accountRepository.save(destination);

        recordTransaction(transactionId, TransactionEntity.TransactionType.TRANSFER, sourceAccountId, destinationAccountId, amount);
    }

    @Override
    @Transactional(readOnly = true)
    public long getBalance(String accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return account.getBalance();
    }

    @Override
    @Transactional
    public AccountModel createAccount(AccountModel model) {
        AccountEntity entity = AccountEntity.create(model.getName(), model.getBalance());
        entity = accountRepository.save(entity);
        return mapper.toAccountModel(entity);
    }

    private void recordTransaction(String transactionId, TransactionEntity.TransactionType type,
                                   String accountId, String destinationAccountId, long amount) {
        TransactionEntity tx = TransactionEntity.builder()
                .transactionId(transactionId)
                .type(type)
                .accountId(accountId)
                .destinationAccountId(destinationAccountId)
                .amount(amount)
                        .build();
        transactionRepository.save(tx);
    }
}
