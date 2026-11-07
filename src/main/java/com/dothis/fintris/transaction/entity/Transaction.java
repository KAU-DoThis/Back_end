package com.dothis.fintris.transaction.entity;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.category.entity.Category;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// classify_stage는 AI 내부 단계라 BE에서 쓰게 되면 추가한다
@Entity
@Table(name = "transactions")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id") // 현금 빠른 입력은 null
    private LinkedAccount linkedAccount;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private TransactionDirection direction;

    private String merchantOrPayee;

    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category; // 분류 전 null

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence; // 분류 신뢰도 0~1

    @Column(length = 30)
    private String contextTag; // 맥락 질문 답변 태그 (F-15)

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassifiedBy classifiedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block; // 블록 반영 전 null

    @Column(nullable = false)
    private boolean excluded; // 자기 계좌 간 이동 등 소비 집계 제외

    @Column(nullable = false)
    private boolean isCanceled;

    // CODEF 거래에는 고유 ID가 없어 계좌·일시·방향·금액·잔액으로 직접 만든 중복 판정 키
    @Column(unique = true)
    private String externalKey;
}
