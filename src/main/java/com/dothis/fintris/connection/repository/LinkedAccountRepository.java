package com.dothis.fintris.connection.repository;

import com.dothis.fintris.connection.entity.AccountType;
import com.dothis.fintris.connection.entity.LinkedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    // 미배분 잔액 계산에 포함되는 계좌 (is_active = 1, 삭제 안 됨)
    List<LinkedAccount> findAllByUser_IdAndTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(Long userId, AccountType type);
}
