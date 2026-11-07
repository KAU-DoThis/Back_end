package com.dothis.fintris.transaction.repository;

import com.dothis.fintris.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t.externalKey from Transaction t where t.externalKey in :keys")
    List<String> findExistingExternalKeys(@Param("keys") Collection<String> keys);
}
