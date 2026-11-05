package com.dothis.fintris.waitingfund.entity;

import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

// 나눌 돈(대기 자금). 블록에 아직 안 나눈 돈을 출처별로 모아 둔다
// 불변식: 블록 잔액 합 + 대기 자금 합 = 연동 계좌 실제 잔액
@Entity
@Table(name = "waiting_funds")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingFund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiting_fund_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingFundSource source;

    @Column(nullable = false, length = 50)
    private String label; // 홈 카드 제목 (예: 절약분, 정기 입금 · 월급)

    @Column(length = 100)
    private String description; // 예: 9월 절약 확정, 10월 12일 입금

    @Column(nullable = false)
    private long amount; // 남은 금액. 0이 되면 목록에서 빠진다

    @Column(nullable = false)
    private long originalAmount;

    // 출처 기록. 거래·절약 목표·입금원 기능이 붙으면 채운다
    private Long sourceTransactionId;
    private Long savingGoalId;
    private Long incomeSourceId;

    // 절약분의 카테고리별 내역 (06 화면 sourceBreakdown)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Breakdown> breakdown;

    private LocalDateTime clearedAt; // 다 나눈 시각

    // 블록과 같이 움직이므로 동시 배분을 막는다
    @Version
    private Long version;

    public record Breakdown(String label, long amount) {
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    // 블록으로 나눈 만큼 줄인다. 모자라면 서비스에서 먼저 막는다
    public void withdraw(long value, LocalDateTime now) {
        this.amount -= value;
        if (this.amount == 0) {
            this.clearedAt = now;
        }
    }

    public void deposit(long value) {
        this.amount += value;
        this.clearedAt = null;
    }
}
