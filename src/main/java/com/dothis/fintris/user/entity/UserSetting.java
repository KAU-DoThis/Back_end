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

    @Column(length = 30)
    private String nickname; // TBD: 필요 여부

    // PATCH: null이 아닌 값만 바꾼다
    public void update(Boolean tutorialDone, String nickname) {
        if (tutorialDone != null) this.tutorialDone = tutorialDone;
        if (nickname != null) this.nickname = nickname;
    }

    public void confirmBlocks() {
        this.blocksConfirmed = true;
    }
}
