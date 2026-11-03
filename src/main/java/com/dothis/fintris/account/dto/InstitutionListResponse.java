package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.entity.LinkLoginType;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.entity.SupportedInstitution;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;

@Schema(description = "연동 가능 기관 목록 응답")
public record InstitutionListResponse(List<InstitutionItem> institutions) {

    // type이 null이면 은행과 카드를 모두 담는다
    public static InstitutionListResponse of(LinkedAccountType type) {
        return new InstitutionListResponse(Arrays.stream(SupportedInstitution.values())
                .filter(institution -> type == null || institution.getType() == type)
                .map(InstitutionItem::from)
                .toList());
    }

    public record InstitutionItem(
            @Schema(description = "CODEF 기관코드", example = "0004") String institutionCode,
            @Schema(example = "국민은행") String name,
            @Schema(example = "BANK") LinkedAccountType type,
            @Schema(description = "지원하는 로그인 방식") List<LinkLoginType> loginTypes
    ) {

        static InstitutionItem from(SupportedInstitution institution) {
            return new InstitutionItem(institution.getCode(), institution.getDisplayName(),
                    institution.getType(), List.of(LinkLoginType.ID_PASSWORD));
        }
    }
}
