package com.dothis.fintris.account.repository;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 해제된 연동(deleted_at not null)은 조회에서 제외한다
public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    List<LinkedAccount> findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(Long userId);

    // 주인 확인(403)은 서비스에서 하므로 user 조건 없이 찾는다
    Optional<LinkedAccount> findByIdAndDeletedAtIsNull(Long linkedAccountId);

    // 정기 동기화 대상: 해제되지 않은 해당 종류 계좌가 하나라도 있는 사용자
    @Query("select distinct a.user.id from LinkedAccount a where a.type = :type and a.deletedAt is null")
    List<Long> findUserIdsWithActive(@Param("type") LinkedAccountType type);
}
