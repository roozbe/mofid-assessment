package net.roozbe.mofidassessment.service;

import net.roozbe.mofidassessment.da.entity.AccountEntity;
import net.roozbe.mofidassessment.da.repository.AccountRepository;
import net.roozbe.mofidassessment.da.repository.TransactionRepository;
import net.roozbe.mofidassessment.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BalanceServiceImplTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private String createTestAccount(long initialBalance) {
        AccountEntity entity = AccountEntity.create("Test Account", initialBalance);
        return accountRepository.save(entity).getId();
    }

    @Nested
    @DisplayName("Credit Operations")
    class CreditTests {

        @Test
        @DisplayName("Should credit account successfully")
        void creditSuccess() {
            String accountId = createTestAccount(1000);

            balanceService.credit(accountId, 500, "TX-CREDIT-1");

            assertEquals(1500, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Should throw AccountNotFoundException for unknown account")
        void creditUnknownAccount() {
            assertThrows(AccountNotFoundException.class, () ->
                    balanceService.credit("UNKNOWN-ID", 500, "TX-CREDIT-2"));
        }

        @Test
        @DisplayName("Should throw InvalidAmountException for zero amount")
        void creditZeroAmount() {
            String accountId = createTestAccount(1000);

            assertThrows(InvalidAmountException.class, () ->
                    balanceService.credit(accountId, 0, "TX-CREDIT-3"));
        }

        @Test
        @DisplayName("Should throw InvalidAmountException for negative amount")
        void creditNegativeAmount() {
            String accountId = createTestAccount(1000);

            assertThrows(InvalidAmountException.class, () ->
                    balanceService.credit(accountId, -100, "TX-CREDIT-4"));
        }
    }

    @Nested
    @DisplayName("Debit Operations")
    class DebitTests {

        @Test
        @DisplayName("Should debit account successfully")
        void debitSuccess() {
            String accountId = createTestAccount(1000);

            balanceService.debit(accountId, 700, "TX-DEBIT-1");

            assertEquals(300, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Should debit entire balance successfully")
        void debitEntireBalance() {
            String accountId = createTestAccount(1000);

            balanceService.debit(accountId, 1000, "TX-DEBIT-FULL");

            assertEquals(0, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Should throw InsufficientBalanceException when balance is not enough")
        void debitInsufficientBalance() {
            String accountId = createTestAccount(1000);

            assertThrows(InsufficientBalanceException.class, () ->
                    balanceService.debit(accountId, 1200, "TX-DEBIT-2"));

            assertEquals(1000, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Should throw AccountNotFoundException for unknown account")
        void debitUnknownAccount() {
            assertThrows(AccountNotFoundException.class, () ->
                    balanceService.debit("UNKNOWN-ID", 500, "TX-DEBIT-3"));
        }

        @Test
        @DisplayName("Should throw InvalidAmountException for zero amount")
        void debitZeroAmount() {
            String accountId = createTestAccount(1000);

            assertThrows(InvalidAmountException.class, () ->
                    balanceService.debit(accountId, 0, "TX-DEBIT-4"));
        }
    }

    @Nested
    @DisplayName("Transfer Operations")
    class TransferTests {

        @Test
        @DisplayName("Should transfer between accounts successfully")
        void transferSuccess() {
            String accountA = createTestAccount(1000);
            String accountB = createTestAccount(500);

            balanceService.transfer(accountA, accountB, 300, "TX-TRANSFER-1");

            assertEquals(700, balanceService.getBalance(accountA));
            assertEquals(800, balanceService.getBalance(accountB));
        }

        @Test
        @DisplayName("Should throw InsufficientBalanceException when source has insufficient balance")
        void transferInsufficientBalance() {
            String accountA = createTestAccount(200);
            String accountB = createTestAccount(500);

            assertThrows(InsufficientBalanceException.class, () ->
                    balanceService.transfer(accountA, accountB, 300, "TX-TRANSFER-2"));

            assertEquals(200, balanceService.getBalance(accountA));
            assertEquals(500, balanceService.getBalance(accountB));
        }

        @Test
        @DisplayName("Should throw SameAccountTransferException for same account transfer")
        void transferSameAccount() {
            String accountA = createTestAccount(1000);

            assertThrows(SameAccountTransferException.class, () ->
                    balanceService.transfer(accountA, accountA, 100, "TX-TRANSFER-3"));

            assertEquals(1000, balanceService.getBalance(accountA));
        }

        @Test
        @DisplayName("Should throw AccountNotFoundException for unknown source account")
        void transferUnknownSource() {
            String accountB = createTestAccount(500);

            assertThrows(AccountNotFoundException.class, () ->
                    balanceService.transfer("UNKNOWN-ID", accountB, 100, "TX-TRANSFER-4"));
        }

        @Test
        @DisplayName("Should throw AccountNotFoundException for unknown destination account")
        void transferUnknownDestination() {
            String accountA = createTestAccount(1000);

            assertThrows(AccountNotFoundException.class, () ->
                    balanceService.transfer(accountA, "UNKNOWN-ID", 100, "TX-TRANSFER-5"));
        }

        @Test
        @DisplayName("Transfer should be atomic - total balance must remain constant")
        void transferAtomicity() {
            String accountA = createTestAccount(1000);
            String accountB = createTestAccount(500);

            long totalBefore = balanceService.getBalance(accountA) + balanceService.getBalance(accountB);

            balanceService.transfer(accountA, accountB, 300, "TX-TRANSFER-6");

            long totalAfter = balanceService.getBalance(accountA) + balanceService.getBalance(accountB);
            assertEquals(totalBefore, totalAfter);
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("Credit: multiple calls with same transactionId should only apply once")
        void creditIdempotency() {
            String accountId = createTestAccount(1000);

            balanceService.credit(accountId, 100, "TX-IDEM-1");
            balanceService.credit(accountId, 100, "TX-IDEM-1");
            balanceService.credit(accountId, 100, "TX-IDEM-1");

            assertEquals(1100, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Debit: multiple calls with same transactionId should only apply once")
        void debitIdempotency() {
            String accountId = createTestAccount(1000);

            balanceService.debit(accountId, 200, "TX-IDEM-2");
            balanceService.debit(accountId, 200, "TX-IDEM-2");
            balanceService.debit(accountId, 200, "TX-IDEM-2");

            assertEquals(800, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Transfer: multiple calls with same transactionId should only apply once")
        void transferIdempotency() {
            String accountA = createTestAccount(1000);
            String accountB = createTestAccount(500);

            balanceService.transfer(accountA, accountB, 300, "TX-IDEM-3");
            balanceService.transfer(accountA, accountB, 300, "TX-IDEM-3");
            balanceService.transfer(accountA, accountB, 300, "TX-IDEM-3");

            assertEquals(700, balanceService.getBalance(accountA));
            assertEquals(800, balanceService.getBalance(accountB));
        }

        @Test
        @DisplayName("Different transactionIds should each be applied")
        void differentTransactionIds() {
            String accountId = createTestAccount(1000);

            balanceService.credit(accountId, 100, "TX-DIFF-1");
            balanceService.credit(accountId, 100, "TX-DIFF-2");
            balanceService.credit(accountId, 100, "TX-DIFF-3");

            assertEquals(1300, balanceService.getBalance(accountId));
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent credits on same account should produce correct final balance")
        void concurrentCreditsOnSameAccount() throws InterruptedException {
            String accountId = createTestAccount(0);
            int threadCount = 100;
            long creditAmount = 100;

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final String txId = "TX-CONC-CREDIT-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        balanceService.credit(accountId, creditAmount, txId);
                    } catch (Exception e) {
                        // expected for some threads
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount * creditAmount, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Concurrent debits on same account - only valid ones should succeed")
        void concurrentDebitsOnSameAccount() throws InterruptedException {
            long initialBalance = 100_000;
            String accountId = createTestAccount(initialBalance);
            int threadCount = 1000;
            long debitAmount = 100;

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final String txId = "TX-CONC-DEBIT-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        balanceService.debit(accountId, debitAmount, txId);
                        successCount.incrementAndGet();
                    } catch (InsufficientBalanceException e) {
                        failedCount.incrementAndGet();
                    } catch (Exception e) {
                        // other exceptions
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            long finalBalance = balanceService.getBalance(accountId);
            assertEquals(initialBalance - (successCount.get() * debitAmount), finalBalance);
            assertTrue(finalBalance >= 0, "Balance must never go negative");
            assertEquals(threadCount, successCount.get() + failedCount.get());
        }

        @Test
        @DisplayName("Concurrent operations on different accounts should not block each other")
        void concurrentOperationsOnDifferentAccounts() throws InterruptedException {
            String accountA = createTestAccount(100_000);
            String accountB = createTestAccount(100_000);
            int threadCount = 200;

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final String txId = "TX-CONC-MULTI-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String target = (idx % 2 == 0) ? accountA : accountB;
                        balanceService.credit(target, 100, txId);
                    } catch (Exception e) {
                        // ignored
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(100_000 + (100 * 100), balanceService.getBalance(accountA));
            assertEquals(100_000 + (100 * 100), balanceService.getBalance(accountB));
        }

        @Test
        @DisplayName("Concurrent transfers should maintain total balance invariant")
        void concurrentTransfers() throws InterruptedException {
            String accountA = createTestAccount(100_000);
            String accountB = createTestAccount(100_000);
            long totalBefore = 200_000;
            int threadCount = 200;

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final String txId = "TX-CONC-TRANS-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (idx % 2 == 0) {
                            balanceService.transfer(accountA, accountB, 50, txId);
                        } else {
                            balanceService.transfer(accountB, accountA, 50, txId);
                        }
                    } catch (Exception e) {
                        // ignored
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            long totalAfter = balanceService.getBalance(accountA) + balanceService.getBalance(accountB);
            assertEquals(totalBefore, totalAfter, "Total balance must remain constant");
        }

        @Test
        @DisplayName("Concurrent idempotent credits should only apply once")
        void concurrentIdempotentCredits() throws InterruptedException {
            String accountId = createTestAccount(1000);
            int threadCount = 50;

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        balanceService.credit(accountId, 500, "TX-CONC-IDEM-SAME");
                    } catch (Exception e) {
                        // expected for duplicates
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(1500, balanceService.getBalance(accountId));
        }
    }

    @Nested
    @DisplayName("GetBalance")
    class GetBalanceTests {

        @Test
        @DisplayName("Should return correct balance")
        void getBalanceSuccess() {
            String accountId = createTestAccount(5000);

            assertEquals(5000, balanceService.getBalance(accountId));
        }

        @Test
        @DisplayName("Should throw AccountNotFoundException for unknown account")
        void getBalanceUnknownAccount() {
            assertThrows(AccountNotFoundException.class, () ->
                    balanceService.getBalance("UNKNOWN-ID"));
        }
    }
}
