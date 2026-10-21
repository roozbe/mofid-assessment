package net.roozbe.mofidassessment.da.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "TB_TRANSACTION", indexes = {
        @Index(name = "idx_transaction_id", columnList = "transactionId", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    private String accountId;

    private String destinationAccountId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum TransactionType {
        CREDIT, DEBIT, TRANSFER
    }
}
