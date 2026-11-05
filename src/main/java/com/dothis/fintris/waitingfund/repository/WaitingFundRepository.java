package com.dothis.fintris.waitingfund.repository;

import com.dothis.fintris.waitingfund.entity.WaitingFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WaitingFundRepository extends JpaRepository<WaitingFund, Long> {

    // 홈 "나눌 돈"은 남은 금액이 있는 것만, 먼저 생긴 순서로 보여 준다
    List<WaitingFund> findAllByUser_IdAndAmountGreaterThanOrderByCreatedAtAsc(Long userId, long amount);

    // 불변식 확인용: 블록 잔액 합 + 대기 자금 합 = 실제 잔액
    @Query("select coalesce(sum(w.amount), 0) from WaitingFund w where w.user.id = :userId")
    long sumAmountByUserId(@Param("userId") Long userId);
}
