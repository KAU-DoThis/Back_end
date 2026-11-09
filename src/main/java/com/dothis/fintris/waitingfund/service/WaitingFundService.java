package com.dothis.fintris.waitingfund.service;

import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingFundService {

    private final WaitingFundRepository waitingFundRepository;

    // 남은 금액이 있는 것만. 홈 "나눌 돈" 영역과 같은 목록이다
    public List<WaitingFundResponse> getWaitingFunds(Long userId) {
        return waitingFundRepository.findAllByUser_IdAndAmountGreaterThanOrderByCreatedAtAsc(userId, 0).stream()
                .map(WaitingFundResponse::from)
                .toList();
    }
}
