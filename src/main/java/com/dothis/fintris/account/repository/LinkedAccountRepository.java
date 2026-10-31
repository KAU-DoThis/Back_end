package com.dothis.fintris.account.repository;

import com.dothis.fintris.account.entity.LinkedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 해제된 연동(deleted_at not null)은 조회에서 제외한다
public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    List<LinkedAccount> findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(Long userId);

    // 주인 확인(403)은 서비스에서 하므로 user 조건 없이 찾는다
    Optional<LinkedAccount> findByIdAndDeletedAtIsNull(Long linkedAccountId);
}
