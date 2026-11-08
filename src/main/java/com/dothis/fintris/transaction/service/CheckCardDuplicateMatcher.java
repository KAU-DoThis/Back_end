package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 체크카드 결제는 카드 승인내역과 은행 출금에 둘 다 찍힌다. 카드 쪽을 남기고 짝이 되는 은행 출금을 골라낸다
// 잘못 묶으면 실제로 쓴 돈이 사라지므로 엄격하게 본다: 같은 날·같은 금액·1분 이내, 카드 승인 하나에 은행 출금 하나
// TODO: 은행이 체크카드 출금의 거래 설명에 표시를 붙여 주는지 실제 응답으로 확인되면 그 표시로 바꾼다
@Component
public class CheckCardDuplicateMatcher {

    static final Duration MAX_GAP = Duration.ofMinutes(1);

    // 취소되지 않은 출금 목록(은행·카드 섞여 있음)을 받아, 새로 소비 제외할 은행 출금을 돌려준다
    public List<Transaction> findBankDuplicates(List<Transaction> outgoing) {
        List<Transaction> cards = new ArrayList<>();
        List<Transaction> banks = new ArrayList<>();
        for (Transaction transaction : outgoing) {
            if (transaction.getLinkedAccount() == null) {
                continue; // 현금 빠른 입력
            }
            (transaction.getLinkedAccount().getType() == LinkedAccountType.CARD ? cards : banks).add(transaction);
        }
        banks.sort(Comparator.comparing(Transaction::getOccurredAt));

        Set<Transaction> usedCards = new HashSet<>();
        // 이전 동기화에서 이미 빠진 은행 출금이 쓴 카드 승인은 다시 쓰지 않는다
        for (Transaction bank : banks) {
            if (bank.isExcluded()) {
                closestCard(bank, cards, usedCards).ifPresent(usedCards::add);
            }
        }

        List<Transaction> duplicates = new ArrayList<>();
        for (Transaction bank : banks) {
            if (bank.isExcluded()) {
                continue;
            }
            Optional<Transaction> card = isMidnight(bank)
                    ? onlyCardOfDay(bank, banks, cards, usedCards)
                    : closestCard(bank, cards, usedCards);
            card.ifPresent(matched -> {
                usedCards.add(matched);
                duplicates.add(bank);
            });
        }
        return duplicates;
    }

    // 같은 날·같은 금액이고 1분 이내인 카드 승인 중 가장 가까운 것
    private Optional<Transaction> closestCard(Transaction bank, List<Transaction> cards, Set<Transaction> usedCards) {
        return cards.stream()
                .filter(card -> !usedCards.contains(card) && sameDayAndAmount(bank, card))
                .filter(card -> gap(bank, card).compareTo(MAX_GAP) <= 0)
                .min(Comparator.comparing(card -> gap(bank, card)));
    }

    // 거래 시각을 안 주는 은행(자정으로 저장)은 그날 같은 금액이 카드에도 은행에도 딱 한 건일 때만 묶는다
    private Optional<Transaction> onlyCardOfDay(Transaction bank, List<Transaction> banks, List<Transaction> cards,
                                                Set<Transaction> usedCards) {
        long sameBanks = banks.stream().filter(other -> isMidnight(other) && sameDayAndAmount(bank, other)).count();
        List<Transaction> candidates = cards.stream()
                .filter(card -> !usedCards.contains(card) && sameDayAndAmount(bank, card))
                .toList();
        return sameBanks == 1 && candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private boolean sameDayAndAmount(Transaction a, Transaction b) {
        return a.getAmount() == b.getAmount()
                && a.getOccurredAt().toLocalDate().equals(b.getOccurredAt().toLocalDate());
    }

    private boolean isMidnight(Transaction transaction) {
        return transaction.getOccurredAt().toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    private Duration gap(Transaction a, Transaction b) {
        return Duration.between(a.getOccurredAt(), b.getOccurredAt()).abs();
    }
}
