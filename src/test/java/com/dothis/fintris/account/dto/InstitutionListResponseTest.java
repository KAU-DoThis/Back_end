package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.dto.InstitutionListResponse.InstitutionItem;
import com.dothis.fintris.account.entity.LinkLoginType;
import com.dothis.fintris.account.entity.LinkedAccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionListResponseTest {

    @Test
    @DisplayName("type을 주면 그 종류의 기관만 담는다")
    void filtersByType() {
        assertThat(InstitutionListResponse.of(LinkedAccountType.BANK).institutions())
                .isNotEmpty().allMatch(item -> item.type() == LinkedAccountType.BANK);
        assertThat(InstitutionListResponse.of(LinkedAccountType.CARD).institutions())
                .isNotEmpty().allMatch(item -> item.type() == LinkedAccountType.CARD);
    }

    @Test
    @DisplayName("type이 없으면 은행과 카드를 모두 담는다")
    void withoutTypeContainsBoth() {
        assertThat(InstitutionListResponse.of(null).institutions())
                .extracting(InstitutionItem::type).contains(LinkedAccountType.BANK, LinkedAccountType.CARD);
    }

    @Test
    @DisplayName("로그인 방식은 아이디·비밀번호만 안내한다")
    void onlyIdPasswordLoginIsOffered() {
        assertThat(InstitutionListResponse.of(null).institutions())
                .allMatch(item -> item.loginTypes().equals(java.util.List.of(LinkLoginType.ID_PASSWORD)));
    }

    @Test
    @DisplayName("우리은행은 추가 인증이 필요해 목록에 없다")
    void excludesWooriBank() {
        assertThat(InstitutionListResponse.of(LinkedAccountType.BANK).institutions())
                .extracting(InstitutionItem::institutionCode).doesNotContain("0020").contains("0004");
    }
}
