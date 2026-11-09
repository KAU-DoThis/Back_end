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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// classify_stage는 AI 내부 단계라 BE에서 쓰게 되면 추가한다
@Entity
@Table(name = "transactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_transactions_external_key", columnNames = {"user_id", "external_key"}))
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

    @Column(length = 100)
    private String merchantType; // 카드 가맹점 업종. 은행·현금은 null

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

    private Long blockAmount; // 반영 블록에서 실제로 뺀 금액. 블록이 0에서 멈추면 거래 금액보다 작다

    @Column(nullable = false)
    private boolean excluded; // 자기 계좌 간 이동 등 소비 집계 제외

    @Column(nullable = false)
    private boolean isCanceled;

    // CODEF 중복 판정 키 (v5). 사용자 안에서 유일하다
    private String externalKey;

    // 블록 잔액은 BlockReflector가 바꾸고, 거래에는 어디서 얼마를 뺐는지만 남긴다
    public void reflectTo(Block block, long deducted) {
        this.block = block;
        this.blockAmount = deducted;
    }

    public void releaseBlock() {
        this.block = null;
        this.blockAmount = null;
    }

    // 취소·금액 변경 뒤 블록 잔액은 BlockReflector의 정리 단계에서 다시 맞춘다
    public void cancel() {
        this.isCanceled = true;
    }

    // 체크카드 중복 등 쓴 돈으로 세지 않을 거래
    public void excludeFromSpending() {
        this.excluded = true;
    }

    // 부분 취소로 남은 금액만 남긴다
    public void changeAmount(long amount) {
        this.amount = amount;
    }

    // 사용자가 고친 분류는 자동 분류보다 우선한다 (F-20)
    public void changeCategoryByUser(Category category) {
        this.category = category;
        this.classifiedBy = ClassifiedBy.USER;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
