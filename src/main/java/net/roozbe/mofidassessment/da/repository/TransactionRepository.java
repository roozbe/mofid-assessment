package net.roozbe.mofidassessment.da.repository;

import net.roozbe.mofidassessment.da.entity.TransactionEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<TransactionEntity, Long> {

    boolean existsByTransactionId(String transactionId);
}
