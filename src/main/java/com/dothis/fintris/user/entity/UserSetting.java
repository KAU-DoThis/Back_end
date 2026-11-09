package com.dothis.fintris.user.entity;

import com.dothis.fintris.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "user_settings")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean tutorialDone;

    @Column(nullable = false)
    private boolean blocksConfirmed; // 초기 블록 확정 API가 바꾼다

    // 블록은 확정할 때의 실제 잔액으로 나눠지므로, 그 전 거래(연동 때 가져온 3개월 등)는 블록에서 빼지 않는다
    private LocalDateTime blocksConfirmedAt;

    @Column(length = 30)
    private String nickname; // TBD: 필요 여부

    // PATCH: null이 아닌 값만 바꾼다
    public void update(Boolean tutorialDone, String nickname) {
        if (tutorialDone != null) this.tutorialDone = tutorialDone;
        if (nickname != null) this.nickname = nickname;
    }

    // 다시 확정해도 처음 시각을 유지한다. 바꾸면 이미 반영한 거래와 기준이 어긋난다
    public void confirmBlocks() {
        this.blocksConfirmed = true;
        if (this.blocksConfirmedAt == null) {
            this.blocksConfirmedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        }
    }
}
