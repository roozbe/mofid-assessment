package net.roozbe.mofidassessment.da.entity;

import net.roozbe.mofidassessment.exception.InsufficientBalanceException;
import net.roozbe.mofidassessment.exception.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountEntityTest {

    @Nested
    @DisplayName("Factory Method")
    class CreateTests {

        @Test
        @DisplayName("Should create account with given name and balance")
        void createWithNameAndBalance() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertEquals("Alice", account.getName());
            assertEquals(1000, account.getBalance());
        }

        @Test
        @DisplayName("Should create account with zero balance")
        void createWithZeroBalance() {
            AccountEntity account = AccountEntity.create("Bob", 0);

            assertEquals(0, account.getBalance());
        }
    }

    @Nested
    @DisplayName("Credit")
    class CreditTests {

        @Test
        @DisplayName("Should increase balance by given amount")
        void creditIncreasesBalance() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            account.credit(500);

            assertEquals(1500, account.getBalance());
        }

        @Test
        @DisplayName("Should allow multiple credits")
        void multipleCredits() {
            AccountEntity account = AccountEntity.create("Alice", 0);

            account.credit(100);
            account.credit(200);
            account.credit(300);

            assertEquals(600, account.getBalance());
        }

        @Test
        @DisplayName("Should reject zero amount")
        void creditZeroAmount() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertThrows(InvalidAmountException.class, () -> account.credit(0));
            assertEquals(1000, account.getBalance());
        }

        @Test
        @DisplayName("Should reject negative amount")
        void creditNegativeAmount() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertThrows(InvalidAmountException.class, () -> account.credit(-100));
            assertEquals(1000, account.getBalance());
        }
    }

    @Nested
    @DisplayName("Debit")
    class DebitTests {

        @Test
        @DisplayName("Should decrease balance by given amount")
        void debitDecreasesBalance() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            account.debit(700);

            assertEquals(300, account.getBalance());
        }

        @Test
        @DisplayName("Should allow debit of entire balance")
        void debitEntireBalance() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            account.debit(1000);

            assertEquals(0, account.getBalance());
        }

        @Test
        @DisplayName("Should reject debit when insufficient balance")
        void debitInsufficientBalance() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertThrows(InsufficientBalanceException.class, () -> account.debit(1200));
            assertEquals(1000, account.getBalance());
        }

        @Test
        @DisplayName("Should reject debit on zero balance")
        void debitOnZeroBalance() {
            AccountEntity account = AccountEntity.create("Alice", 0);

            assertThrows(InsufficientBalanceException.class, () -> account.debit(1));
            assertEquals(0, account.getBalance());
        }

        @Test
        @DisplayName("Should reject zero amount")
        void debitZeroAmount() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertThrows(InvalidAmountException.class, () -> account.debit(0));
            assertEquals(1000, account.getBalance());
        }

        @Test
        @DisplayName("Should reject negative amount")
        void debitNegativeAmount() {
            AccountEntity account = AccountEntity.create("Alice", 1000);

            assertThrows(InvalidAmountException.class, () -> account.debit(-100));
            assertEquals(1000, account.getBalance());
        }

        @Test
        @DisplayName("Balance must never go negative after failed debit")
        void balanceNeverNegative() {
            AccountEntity account = AccountEntity.create("Alice", 500);

            assertThrows(InsufficientBalanceException.class, () -> account.debit(501));
            assertTrue(account.getBalance() >= 0);
        }
    }
}
