package com.dothis.fintris.block.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockHistoryResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 블록 상세 화면 (앱 시안 02, 02-1, 02-2 / API 명세 v4 6장)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockDetailService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;

    // TODO: 고정지출 납부 기록(PAYMENT)은 거래 반영(F-21)이 붙으면 같은 목록에 넣는다
    public BlockHistoryResponse getHistory(Long userId, Long blockId, int page, int size) {
        Block block = findOwnedBlock(userId, blockId);
        Slice<BlockTransfer> slice = blockTransferRepository.findHistory(blockId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE)));
        return new BlockHistoryResponse(
                slice.getContent().stream().map(transfer -> BlockHistoryResponse.Item.of(block, transfer)).toList(),
                slice.hasNext());
    }

    // 없으면 404, 남의 블록이면 403
    Block findOwnedBlock(Long userId, Long blockId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));
        if (!block.isOwnedBy(userId)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_FORBIDDEN);
        }
        return block;
    }
}
