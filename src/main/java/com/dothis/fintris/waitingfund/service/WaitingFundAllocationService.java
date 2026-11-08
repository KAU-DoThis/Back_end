package com.dothis.fintris.waitingfund.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.service.BlockTransferService;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.SimulateRequest;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.waitingfund.code.WaitingFundErrorCode;
import com.dothis.fintris.waitingfund.dto.AllocationRequest;
import com.dothis.fintris.waitingfund.dto.AllocationResponse;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 나눌 돈 → 블록 배분 확정. 미리보기와 같은 계산(BlockSimulationService)을 거친 뒤 반영한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingFundAllocationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BlockSimulationService blockSimulationService;
    private final BlockTransferService blockTransferService;
    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;

    @Transactional
    public AllocationResponse allocate(Long userId, Long waitingFundId, AllocationRequest request) {
        List<SimulateRequest.Change> changes = new ArrayList<>();
        for (AllocationRequest.Allocation allocation : request.allocations()) {
            changes.add(new SimulateRequest.Change(null, waitingFundId, allocation.blockId(), allocation.amount()));
        }
        if (changes.isEmpty() && request.leftoverTo() != AllocationRequest.LeftoverTo.DEFAULT_BLOCK) {
            throw GeneralException.of(WaitingFundErrorCode.WAITING_FUND_INSUFFICIENT);
        }

        // "생활비에 그냥 두기"(06): 나누고 남은 돈을 기본 블록에 넣는다
        if (request.leftoverTo() == AllocationRequest.LeftoverTo.DEFAULT_BLOCK) {
            long requested = request.allocations().stream().mapToLong(AllocationRequest.Allocation::amount).sum();
            long leftover = currentAmount(userId, waitingFundId) - requested;
            if (leftover > 0) {
                Block defaultBlock = blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_INVALID_DEFAULT));
                changes.add(new SimulateRequest.Change(null, waitingFundId, defaultBlock.getId(), leftover));
            }
        }

        BlockSimulationService.Simulation simulation = blockSimulationService.simulate(userId, changes);
        if (simulation.response().blocked()) {
            throw GeneralException.of(WaitingFundErrorCode.WAITING_FUND_INSUFFICIENT);
        }
        for (AllocationRequest.Allocation allocation : request.allocations()) {
            Long version = simulation.blocks().get(allocation.blockId()).getVersion();
            if (allocation.version() != null && !allocation.version().equals(version)) {
                throw GeneralException.of(BlockErrorCode.BLOCK_VERSION_CONFLICT);
            }
        }

        WaitingFund fund = simulation.funds().get(waitingFundId);
        TransferReason reason = fund.getSource() == WaitingFundSource.SAVING ? TransferReason.SAVING : TransferReason.ALLOCATION;
        List<BlockTransfer> transfers = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : simulation.blockDeltas().entrySet()) {
            Block block = simulation.blocks().get(entry.getKey());
            block.deposit(entry.getValue());
            transfers.add(BlockTransfer.builder()
                    .fromWaitingFund(fund)
                    .toBlock(block)
                    .amount(entry.getValue())
                    .reason(reason)
                    .build());
        }
        fund.withdraw(-simulation.fundDeltas().get(waitingFundId), LocalDateTime.now(KST));
        blockTransferRepository.saveAll(transfers);
        // 응답에 올라간 version을 담으려면 먼저 저장해야 한다
        blockRepository.flush();

        return new AllocationResponse(
                new AllocationResponse.FundBalance(fund.getId(), fund.getAmount()),
                simulation.blockDeltas().keySet().stream()
                        .map(id -> TransferResponse.BlockBalance.from(simulation.blocks().get(id)))
                        .toList(),
                blockTransferService.dailyGuide(userId));
    }

    // 주인 확인(404·403)은 미리보기와 같은 방식으로 한다
    private long currentAmount(Long userId, Long waitingFundId) {
        return blockSimulationService.findOwnedFund(userId, waitingFundId).getAmount();
    }
}
