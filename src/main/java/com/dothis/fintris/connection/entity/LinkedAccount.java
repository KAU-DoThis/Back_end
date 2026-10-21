package com.dothis.fintris.connection.entity;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

// CODEF 연동(F-19)은 connection 패키지에서 다룬다. 지금은 잔액 계산에 필요한 매핑만 둔다
// connectedId는 암호화해 저장하고 응답에 절대 넣지 않는다
@Entity
@Table(name = "linked_accounts")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkedAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "linked_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String institutionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    @Column(nullable = false, length = 512)
    private String connectedId;

    @Column(length = 100)
    private String accountName;

    @Column(columnDefinition = "char(4)")
    private String accountNumberLast4; // 계좌번호는 뒤 4자리만

    private Long balance; // 카드는 null

    private LocalDateTime balanceUpdatedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true; // 미배분 잔액 계산 포함 여부

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LinkedAccountStatus status = LinkedAccountStatus.ACTIVE;

    private LocalDateTime lastSyncedAt;

    private LocalDateTime deletedAt;
}
