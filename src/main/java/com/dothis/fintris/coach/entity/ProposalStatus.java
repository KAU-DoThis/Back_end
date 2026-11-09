package com.dothis.fintris.coach.entity;

// EXPIRED는 저장하지 않고 expires_at으로 계산한다 (CoachProposal.statusAt)
public enum ProposalStatus {
    PENDING,  // 수락 전
    ACCEPTED, // 수락해서 옮김
    REJECTED, // 거절
    EXPIRED,  // 수락하지 않고 만료 시간이 지남
    UNDONE    // 옮긴 뒤 되돌림
}
