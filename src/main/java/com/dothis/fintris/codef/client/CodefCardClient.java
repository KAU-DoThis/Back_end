package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CodefCardClient {

    private static final String CARD_LIST_PATH = "/v1/kr/card/p/account/card-list";

    private final CodefApiClient codefApiClient;

    public CardsResult fetchCards(String connectedId, String organization, String birthDate) {
        CodefResponse response = codefApiClient.post(CARD_LIST_PATH,
                Map.of("connectedId", connectedId, "organization", organization, "birthDate", birthDate));
        if (!response.isSuccess()) {
            return CardsResult.failure(response.result().code(), response.result().message());
        }

        List<Card> cards = new ArrayList<>();
        JsonNode data = response.data();
        // 카드가 하나면 배열이 아니라 객체로 올 수 있어 둘 다 받는다
        if (data != null && data.isObject()) {
            addIfValid(cards, data);
        } else if (data != null && data.isArray()) {
            for (JsonNode card : data) {
                addIfValid(cards, card);
            }
        }
        return CardsResult.success(cards);
    }

    private void addIfValid(List<Card> cards, JsonNode card) {
        String cardNumber = text(card, "resCardNo");
        if (cardNumber == null) {
            return;
        }
        cards.add(new Card(cardNumber, text(card, "resCardName"), text(card, "resCardType")));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    // 카드번호는 CODEF가 가운데를 가려서(6056********0000) 내려준다
    public record Card(String cardNumber, String cardName, String cardType) {
    }

    public record CardsResult(List<Card> cards, String failureCode, String failureMessage) {

        static CardsResult success(List<Card> cards) {
            return new CardsResult(cards, null, null);
        }

        static CardsResult failure(String code, String message) {
            return new CardsResult(List.of(), code, message);
        }

        public boolean isSuccess() {
            return failureCode == null;
        }
    }
}
