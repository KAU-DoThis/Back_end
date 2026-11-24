package com.dothis.fintris.contextquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "맥락 규칙 목록. 최근에 만든 순")
public record ContextRuleListResponse(List<ContextRuleResponse> rules) {
}
