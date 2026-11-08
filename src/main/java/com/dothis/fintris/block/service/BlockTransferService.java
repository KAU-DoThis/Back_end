package com.dothis.fintris.block.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.code.TransferErrorCode;
import com.dothis.fintris.block.dto.TransferRequest;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.SimulateRequest;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.service.DailyGuideCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

// 블록 간 이동 확정. 미리보기와 같은 계산(BlockSimulationService)을 거친 뒤 반영한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockTransferService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BlockSimulationService blockSimulationService;
    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;

    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request) {
        if (request.fromBlockId().equals(request.toBlockId())) {
            throw GeneralException.of(TransferErrorCode.TRANSFER_SAME_BLOCK);
        }

        BlockSimulationService.Simulation simulation = blockSimulationService.simulate(userId, List.of(
                new SimulateRequest.Change(request.fromBlockId(), null, request.toBlockId(), request.amount())));
        Block from = simulation.blocks().get(request.fromBlockId());
        Block to = simulation.blocks().get(request.toBlockId());

        // 미리보기 뒤에 다른 곳에서 바뀌었으면 덮어쓰지 않는다
        checkVersion(from, request.fromVersion());
        checkVersion(to, request.toVersion());
        if (simulation.response().blocked()) {
            throw GeneralException.of(TransferErrorCode.TRANSFER_INSUFFICIENT_BALANCE);
        }
        // 고정지출 납부 부족처럼 규칙이 깨지는 이동은 한 번 더 확인받는다
        if (simulation.response().requiresConfirm() && !Boolean.TRUE.equals(request.confirmed())) {
            throw GeneralException.of(TransferErrorCode.TRANSFER_CONFIRM_REQUIRED);
        }

        from.withdraw(request.amount());
        to.deposit(request.amount());
        BlockTransfer transfer = blockTransferRepository.save(BlockTransfer.builder()
                .fromBlock(from)
                .toBlock(to)
                .amount(request.amount())
                .reason(Objects.requireNonNullElse(request.reason(), TransferReason.MANUAL))
                .build());
        // 응답에 올라간 version을 담으려면 먼저 저장해야 한다
        blockRepository.flush();

        return new TransferResponse(transfer.getId(), TransferResponse.BlockBalance.from(from),
                TransferResponse.BlockBalance.from(to), dailyGuide(userId), null);
    }

    private void checkVersion(Block block, Long expected) {
        if (expected != null && !expected.equals(block.getVersion())) {
            throw GeneralException.of(BlockErrorCode.BLOCK_VERSION_CONFLICT);
        }
    }

    // 홈 dailyGuide와 같은 계산 (기본 블록 잔액 ÷ 남은 일수, 100원 내림)
    public TransferResponse.DailyGuide dailyGuide(Long userId) {
        return blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                .filter(block -> block.getDueDate() != null)
                .map(block -> {
                    long days = DailyGuideCalculator.remainingDays(LocalDate.now(KST), block.getDueDate());
                    long available = DailyGuideCalculator.todayAvailable(block.getBalance(), days);
                    return days == 0 ? null : new TransferResponse.DailyGuide(available, available);
                })
                .orElse(null);
    }
}
