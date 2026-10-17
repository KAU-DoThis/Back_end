package com.dothis.fintris.block.entity;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "blocks")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockType type;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 50)
    private String icon;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private boolean isDefault; // 기본 사용형 블록, 삭제 불가

    private Long targetAmount; // 목표형만

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate dueDate; // 사용형=기간 끝, 목표형=기한

    @JdbcTypeCode(SqlTypes.JSON)
    private BlockRule rule;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockStatus status = BlockStatus.SAFE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BlockCreatedBy createdBy = BlockCreatedBy.USER;

    @Column(nullable = false)
    private int displayOrder;

    @Version
    private Long version;

    private LocalDateTime deletedAt;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    // null인 값은 바꾸지 않는다 (PATCH)
    public void update(String name, String icon, Long targetAmount,
                       LocalDate startDate, LocalDate dueDate, BlockRule rule) {
        if (name != null) this.name = name;
        if (icon != null) this.icon = icon;
        if (targetAmount != null) this.targetAmount = targetAmount;
        if (startDate != null) this.startDate = startDate;
        if (dueDate != null) this.dueDate = dueDate;
        if (rule != null) this.rule = rule;
    }

    public void changeOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    // 소프트 삭제. 잔액은 0으로 만들고, 미배분으로 돌려보낼 금액을 반환한다
    public long delete(LocalDateTime now) {
        long returned = this.balance;
        this.balance = 0;
        this.deletedAt = now;
        return returned;
    }
}
