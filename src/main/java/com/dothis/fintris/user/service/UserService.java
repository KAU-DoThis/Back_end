package com.dothis.fintris.user.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.ConsentErrorCode;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.dto.ConsentChangeRequest;
import com.dothis.fintris.user.dto.ConsentChangeResponse;
import com.dothis.fintris.user.dto.ConsentItemListResponse;
import com.dothis.fintris.user.dto.ConsentItemResponse;
import com.dothis.fintris.user.dto.ConsentSaveRequest;
import com.dothis.fintris.user.dto.ConsentSaveResponse;
import com.dothis.fintris.user.dto.ConsentStatusListResponse;
import com.dothis.fintris.user.dto.ConsentStatusResponse;
import com.dothis.fintris.user.dto.UserSettingResponse;
import com.dothis.fintris.user.dto.UserSettingUpdateRequest;
import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import com.dothis.fintris.user.entity.OnboardingStep;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.ConsentRepository;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DATA_DELETION_DAYS = 30;
    private static final String CONSENT_NOTICE = "가져온 금융 정보는 블록 계산과 분석에만 써요.";

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final UserSettingRepository userSettingRepository;

    public ConsentItemListResponse getConsentItems() {
        List<ConsentItemResponse> items = Arrays.stream(ConsentItem.values())
                .map(ConsentItemResponse::from)
                .toList();
        return new ConsentItemListResponse(items, CONSENT_NOTICE);
    }

    // 동의는 덮어쓰지 않고 저장할 때마다 이력 행을 추가한다
    @Transactional
    public ConsentSaveResponse saveConsents(Long userId, ConsentSaveRequest request) {
        User user = findUser(userId);

        Map<ConsentItem, Boolean> requested = new EnumMap<>(ConsentItem.class);
        for (ConsentSaveRequest.ConsentEntry entry : request.consents()) {
            validateDocVersion(entry.docVersion());
            if (requested.put(entry.item(), entry.agreed()) != null) {
                throw GeneralException.of(ConsentErrorCode.CONSENT_ITEM_DUPLICATED);
            }
        }

        // 이미 동의한 항목은 다시 보내지 않아도 되므로, 기존 최신 상태에 이번 요청을 얹어서 판단한다
        Map<ConsentItem, Boolean> merged = latestAgreed(userId);
        merged.putAll(requested);
        List<ConsentItem> missing = missingRequired(merged);
        if (!missing.isEmpty()) {
            throw GeneralException.withResult(ConsentErrorCode.CONSENT_REQUIRED_MISSING, missing);
        }

        List<Consent> consents = requested.entrySet().stream()
                .map(e -> toConsent(user, e.getKey(), e.getValue()))
                .toList();
        consentRepository.saveAll(consents);

        UserSetting setting = userSettingRepository.findByUser_Id(userId).orElse(null);
        return new ConsentSaveResponse(onboardingStep(merged, hasLinkedAccount(userId), setting));
    }

    public ConsentStatusListResponse getConsents(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw GeneralException.of(UserErrorCode.USER_NOT_FOUND);
        }
        List<ConsentStatusResponse> consents = latestConsents(userId).values().stream()
                .map(ConsentStatusResponse::from)
                .toList();
        return new ConsentStatusListResponse(consents);
    }

    @Transactional
    public ConsentChangeResponse changeConsent(Long userId, String itemValue, ConsentChangeRequest request) {
        ConsentItem item = ConsentItem.from(itemValue);
        validateDocVersion(request.docVersion());
        User user = findUser(userId);

        boolean withdrawing = !request.agreed();
        // TERMS·PRIVACY·AGE_14는 철회 대신 탈퇴로 안내한다
        if (withdrawing && item.isLeaveOnWithdraw()) {
            throw GeneralException.of(ConsentErrorCode.CONSENT_WITHDRAW_REQUIRES_LEAVE);
        }

        // 남은 필수 항목(CODEF) 철회는 영향을 먼저 안내하고, 재확인을 받은 뒤에만 저장한다
        boolean withdrawingRequired = withdrawing && item.isRequired();
        ConsentChangeResponse.Impact impact = withdrawingRequired
                ? new ConsentChangeResponse.Impact(true, LocalDate.now(KST).plusDays(DATA_DELETION_DAYS))
                : null;
        if (withdrawingRequired && !Boolean.TRUE.equals(request.confirmed())) {
            boolean current = consentRepository.findFirstByUser_IdAndItemOrderByIdDesc(userId, item)
                    .map(Consent::isAgreed)
                    .orElse(false);
            return new ConsentChangeResponse(item, current, impact);
        }

        // TODO: CODEF 철회 시 실제 연동 해제·데이터 삭제 예약은 계좌 연동(F-19)이 붙으면 처리한다
        consentRepository.save(toConsent(user, item, request.agreed()));
        return new ConsentChangeResponse(item, request.agreed(), impact);
    }

    // 첫 조회 때 설정이 없으면 기본값으로 만든다 (회원가입 API가 붙으면 가입 시 생성으로 옮긴다)
    @Transactional
    public UserSettingResponse getSettings(Long userId) {
        User user = findUser(userId);
        return toSettingResponse(user, findOrCreateSetting(user));
    }

    @Transactional
    public UserSettingResponse updateSettings(Long userId, UserSettingUpdateRequest request) {
        User user = findUser(userId);
        UserSetting setting = findOrCreateSetting(user);
        setting.update(request.tutorialDone(), request.nickname());
        return toSettingResponse(user, setting);
    }

    // 로그인 토큰 응답(F-17)에서도 같은 단계를 내려준다
    public OnboardingStep getOnboardingStep(Long userId) {
        UserSetting setting = userSettingRepository.findByUser_Id(userId).orElse(null);
        return onboardingStep(latestAgreed(userId), hasLinkedAccount(userId), setting);
    }

    private UserSettingResponse toSettingResponse(User user, UserSetting setting) {
        boolean hasLinkedAccount = hasLinkedAccount(user.getId());
        OnboardingStep step = onboardingStep(latestAgreed(user.getId()), hasLinkedAccount, setting);
        return UserSettingResponse.of(user, setting, step, hasLinkedAccount);
    }

    // API 명세 v4 2장: 필수 동의 → 연동 → 튜토리얼 → 블록 확정 순으로 처음 걸리는 단계
    private OnboardingStep onboardingStep(Map<ConsentItem, Boolean> agreed, boolean hasLinkedAccount,
                                          UserSetting setting) {
        if (!missingRequired(agreed).isEmpty()) {
            return OnboardingStep.CONSENT;
        }
        if (!hasLinkedAccount) {
            return OnboardingStep.LINK;
        }
        if (setting == null || !setting.isTutorialDone()) {
            return OnboardingStep.TUTORIAL;
        }
        if (!setting.isBlocksConfirmed()) {
            return OnboardingStep.BLOCKS;
        }
        return OnboardingStep.DONE;
    }

    private List<ConsentItem> missingRequired(Map<ConsentItem, Boolean> agreed) {
        return Arrays.stream(ConsentItem.values())
                .filter(ConsentItem::isRequired)
                .filter(item -> !Boolean.TRUE.equals(agreed.get(item)))
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));
    }

    private UserSetting findOrCreateSetting(User user) {
        return userSettingRepository.findByUser_Id(user.getId())
                .orElseGet(() -> userSettingRepository.save(UserSetting.builder()
                        .user(user)
                        .tutorialDone(false)
                        .blocksConfirmed(false)
                        .build()));
    }

    // TODO: 계좌 연동(F-19)이 머지되면 활성 연동 계좌가 있는지 확인한다. 지금은 false 고정
    private boolean hasLinkedAccount(Long userId) {
        return false;
    }

    // 항목별 가장 최근 행. EnumMap이라 항목 정의 순서대로 나온다
    private Map<ConsentItem, Consent> latestConsents(Long userId) {
        Map<ConsentItem, Consent> latest = new EnumMap<>(ConsentItem.class);
        for (Consent consent : consentRepository.findAllByUser_IdOrderByIdDesc(userId)) {
            latest.putIfAbsent(consent.getItem(), consent);
        }
        return latest;
    }

    private Map<ConsentItem, Boolean> latestAgreed(Long userId) {
        Map<ConsentItem, Boolean> agreed = new EnumMap<>(ConsentItem.class);
        latestConsents(userId).forEach((item, consent) -> agreed.put(item, consent.isAgreed()));
        return agreed;
    }

    private void validateDocVersion(String docVersion) {
        if (!ConsentItem.CURRENT_DOC_VERSION.equals(docVersion)) {
            throw GeneralException.of(ConsentErrorCode.CONSENT_DOC_VERSION_MISMATCH);
        }
    }

    private Consent toConsent(User user, ConsentItem item, boolean agreed) {
        return Consent.builder()
                .user(user)
                .item(item)
                .agreed(agreed)
                .docVersion(ConsentItem.CURRENT_DOC_VERSION)
                .build();
    }
}
