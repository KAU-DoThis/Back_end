package com.dothis.fintris.contextquestion.entity;

import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.transaction.entity.Transaction;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

// 맥락 질문 (F-15). 거래 1건당 최대 1개
@Entity
@Table(name = "context_questions")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContextQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "context_question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    // AI questionOptions 그대로. 맨 앞이 가장 그럴듯한 답
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> options;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.PENDING;

    private LocalDateTime sentAt;

    @Column(length = 30)
    private String answerTag;

    private LocalDateTime answeredAt;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public boolean isOpen() {
        return status == QuestionStatus.PENDING || status == QuestionStatus.SENT;
    }

    public void answer(String tag, LocalDateTime now) {
        this.status = QuestionStatus.ANSWERED;
        this.answerTag = tag;
        this.answeredAt = now;
    }

    public void expire() {
        this.status = QuestionStatus.EXPIRED;
    }
}
