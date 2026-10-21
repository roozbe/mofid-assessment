package net.roozbe.mofidassessment.da.entity;

import jakarta.persistence.*;
import lombok.Getter;
import net.roozbe.mofidassessment.exception.InsufficientBalanceException;
import net.roozbe.mofidassessment.exception.InvalidAmountException;

@Entity
@Table(name = "TB_ACCOUNT")
@Getter
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    private long balance;

    @Version
    private Long version;

    protected AccountEntity() {}

    public static AccountEntity create(String name, long initialBalance) {
        AccountEntity entity = new AccountEntity();
        entity.name = name;
        entity.balance = initialBalance;
        return entity;
    }

    public void credit(long amount) {
        validateAmount(amount);
        this.balance += amount;
    }

    public void debit(long amount) {
        validateAmount(amount);
        if (this.balance < amount) {
            throw new InsufficientBalanceException(this.id, amount, this.balance);
        }
        this.balance -= amount;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException(amount);
        }
    }
}
