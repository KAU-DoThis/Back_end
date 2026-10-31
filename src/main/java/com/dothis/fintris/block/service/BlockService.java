package com.dothis.fintris.block.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockCategoryRequest;
import com.dothis.fintris.block.dto.BlockCreateRequest;
import com.dothis.fintris.block.dto.BlockOrderRequest;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.dto.BlockUpdateRequest;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockCategory;
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BlockRepository blockRepository;
    private final BlockCategoryRepository blockCategoryRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public List<BlockResponse> getBlocks(Long userId, BlockType type) {
        List<Block> blocks = (type == null)
                ? blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId)
                : blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(userId, type);

        // 블록마다 카테고리를 따로 조회하지 않고 한 번에 가져와 묶는다
        Map<Long, List<BlockCategory>> categoriesByBlock = blockCategoryRepository
                .findAllByBlock_IdIn(blocks.stream().map(Block::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(bc -> bc.getBlock().getId()));

        return blocks.stream()
                .map(block -> BlockResponse.of(block, categoriesByBlock.getOrDefault(block.getId(), List.of())))
                .toList();
    }

    public BlockResponse getBlock(Long userId, Long blockId) {
        Block block = findOwnedBlock(userId, blockId);
        return toResponse(block);
    }

    @Transactional
    public BlockResponse createBlock(Long userId, BlockCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));

        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = (request.startDate() != null) ? request.startDate() : today;
        LocalDate dueDate = request.dueDate();
        // 사용형은 월 단위 기간이므로 기간 끝을 안 보내면 이번 달 말일로 둔다
        if (request.type() == BlockType.SPENDING && dueDate == null) {
            dueDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
        }

        // 생성은 전부 새 값이므로 기본값으로 채운 기한까지 검사한다
        validateNewDueDate(dueDate, today);
        validate(request.type(), request.targetAmount(), startDate, dueDate, request.rule());

        // 새 블록은 맨 뒤에 둔다
        int nextOrder = blockRepository.findTopByUser_IdAndDeletedAtIsNullOrderByDisplayOrderDesc(userId)
                .map(last -> last.getDisplayOrder() + 1)
                .orElse(0);

        Block block = Block.builder()
                .user(user)
                .type(request.type())
                .name(request.name())
                .icon(request.icon())
                .targetAmount(request.targetAmount())
                .startDate(startDate)
                .dueDate(dueDate)
                .rule(request.rule())
                .status(BlockStatus.SAFE) // 상태 판정은 S3 이후. 지금은 SAFE 고정
                .createdBy(BlockCreatedBy.USER)
                .displayOrder(nextOrder)
                .build();

        return BlockResponse.of(blockRepository.save(block), List.of());
    }

    @Transactional
    public BlockResponse updateBlock(Long userId, Long blockId, BlockUpdateRequest request) {
        Block block = findOwnedBlock(userId, blockId);

        // 바뀐 뒤의 값 기준으로 검증한다
        Long targetAmount = (request.targetAmount() != null) ? request.targetAmount() : block.getTargetAmount();
        LocalDate startDate = (request.startDate() != null) ? request.startDate() : block.getStartDate();
        LocalDate dueDate = (request.dueDate() != null) ? request.dueDate() : block.getDueDate();
        BlockRule rule = (request.rule() != null) ? request.rule() : block.getRule();
        validateNewDueDate(request.dueDate(), LocalDate.now(KST));
        validate(block.getType(), targetAmount, startDate, dueDate, rule);

        block.update(request.name(), request.icon(), request.targetAmount(),
                request.startDate(), request.dueDate(), request.rule());
        return toResponse(block);
    }

    @Transactional
    public void deleteBlock(Long userId, Long blockId) {
        Block block = findOwnedBlock(userId, blockId);
        if (block.isDefault()) {
            throw GeneralException.of(BlockErrorCode.BLOCK_DEFAULT_UNDELETABLE);
        }

        blockCategoryRepository.deleteAllByBlockId(blockId);
        long returned = block.delete(LocalDateTime.now(KST));

        // 남은 잔액은 미배분(to = null)으로 돌아간 것으로 기록한다
        if (returned > 0) {
            blockTransferRepository.save(BlockTransfer.builder()
                    .fromBlock(block)
                    .toBlock(null)
                    .amount(returned)
                    .reason(TransferReason.MANUAL)
                    .build());
        }
    }

    @Transactional
    public List<BlockResponse> changeOrder(Long userId, BlockOrderRequest request) {
        List<Block> blocks = blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);
        Map<Long, Block> blockById = blocks.stream()
                .collect(Collectors.toMap(Block::getId, Function.identity()));

        // 내 블록 전체를 중복 없이 보내야 한다
        Set<Long> requested = new HashSet<>(request.blockIds());
        if (requested.size() != request.blockIds().size() || !requested.equals(blockById.keySet())) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_ORDER);
        }

        List<Long> ids = request.blockIds();
        for (int i = 0; i < ids.size(); i++) {
            blockById.get(ids.get(i)).changeOrder(i);
        }
        return getBlocks(userId, null);
    }

    @Transactional
    public BlockResponse setCategories(Long userId, Long blockId, BlockCategoryRequest request) {
        Block block = findOwnedBlock(userId, blockId);
        Set<Long> categoryIds = new LinkedHashSet<>(request.categoryIds());

        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw GeneralException.of(BlockErrorCode.BLOCK_CATEGORY_NOT_FOUND);
        }

        // 카테고리 하나는 사용자별 블록 하나에만 연결된다
        boolean mappedToOtherBlock = blockCategoryRepository
                .findAllByUser_IdAndCategory_IdIn(userId, categoryIds)
                .stream()
                .anyMatch(bc -> !bc.getBlock().getId().equals(blockId));
        if (mappedToOtherBlock) {
            throw GeneralException.of(BlockErrorCode.BLOCK_CATEGORY_DUPLICATED);
        }

        blockCategoryRepository.deleteAllByBlockId(blockId);
        List<BlockCategory> mappings = categories.stream()
                .<BlockCategory>map(category -> BlockCategory.builder()
                        .user(block.getUser())
                        .block(block)
                        .category(category)
                        .build())
                .toList();
        List<BlockCategory> saved = blockCategoryRepository.saveAll(mappings);

        return BlockResponse.of(block, saved);
    }

    // 없으면 404, 남의 블록이면 403
    private Block findOwnedBlock(Long userId, Long blockId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));
        if (!block.isOwnedBy(userId)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_FORBIDDEN);
        }
        return block;
    }

    private BlockResponse toResponse(Block block) {
        return BlockResponse.of(block, blockCategoryRepository.findAllByBlock_Id(block.getId()));
    }

    // 기한이 오늘 이전인지는 클라이언트가 새로 보낸 기한에만 검사한다.
    // 저장된 기한까지 검사하면 기한이 지난 블록은 이름만 바꾸는 수정도 실패한다
    private void validateNewDueDate(LocalDate requestedDueDate, LocalDate today) {
        if (requestedDueDate != null && requestedDueDate.isBefore(today)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DUE_DATE);
        }
    }

    // F-04 예외 처리: 목표 금액 0 이하, 기한이 시작일 이전, 납부일 1~31 밖이면 저장하지 않는다
    private void validate(BlockType type, Long targetAmount, LocalDate startDate,
                          LocalDate dueDate, BlockRule rule) {
        if (targetAmount != null && targetAmount <= 0) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_TARGET);
        }
        if (dueDate != null && dueDate.isBefore(startDate)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DUE_DATE);
        }
        if (rule != null && rule.payDay() != null && (rule.payDay() < 1 || rule.payDay() > 31)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_PAY_DAY);
        }

        switch (type) {
            case GOAL -> {
                if (targetAmount == null) {
                    throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_TARGET);
                }
                if (dueDate == null) {
                    throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DUE_DATE);
                }
            }
            case FIXED -> {
                if (rule == null || rule.payDay() == null) {
                    throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_PAY_DAY);
                }
                if (rule.payAmount() == null || rule.payAmount() <= 0) {
                    throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_FIXED_RULE);
                }
            }
            case SPENDING -> {
            }
        }
    }
}
