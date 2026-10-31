package com.dothis.fintris.transaction.repository;

import com.dothis.fintris.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
