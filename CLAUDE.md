# FINTRIS Backend

Spring Boot 4 · Java 21 · MySQL 기반의 FINTRIS API 서버. 팀 이거해조(KAU-DoThis) 2026-2학기 산학프로젝트.

## 먼저 읽을 문서

- [docs/FINTRIS\_API명세\_v4.md](docs/FINTRIS_API명세_v4.md) — 엔드포인트, 요청·응답 형식, 에러 코드, 데이터 모델 변경(12장). API를 만들거나 고치기 전에 해당 장을 읽는다.
- [docs/FINTRIS\_기능명세서.md](docs/FINTRIS_기능명세서.md) — 기능 F-01~F-26의 처리 규칙, 계산식, 예외 처리, 데이터 모델, 수용 기준. 기능을 구현하기 전에 해당 F-번호 절을 읽는다.
- [README.md](README.md) — 설계 원칙, API·응답 형식, 브랜치·커밋·PR 컨벤션, 코드 스타일.

## 백엔드가 지켜야 할 원칙

- 금액·합계·달성률·상태·점수는 모두 백엔드가 계산한다. AI 서버는 설명 문장만 만든다.
- 금액은 원 단위 정수(`long`, `BIGINT`)로 다루고 부동소수점을 쓰지 않는다.
- 블록 배분은 앱 내부 데이터만 바꾼다. 실제 자금 이동은 없다.
- `블록 잔액 합 + 미배분 잔액 = 실제 잔액`이 항상 성립해야 한다.
- 블록 이동·배분은 `@Transactional` + 낙관적 잠금(`@Version`)으로 처리한다.
- 금융기관 인증정보는 저장하지 않는다. `connectedId`만 암호화해 저장하고 응답에 노출하지 않는다.
- CODEF 호출과 DB 접근은 Spring만 한다. 외부 Gemini에는 금액·카테고리·블록 이름만 보낸다.

## 문서끼리 다를 때

- API·데이터 모델은 **API 명세 v4 > 기능 명세서** 순으로 따른다. 기능 명세서는 계산 규칙·처리 규칙의 기준이다.
- v4에서 미배분 잔액은 출처별 **대기 자금**(`waiting_funds`)으로 바뀌었다. 불변식은 `블록 잔액 합 + 대기 자금 합 = 실제 잔액`이다.

- 명세서의 테이블명은 단수(`user`, `block`)지만, 실제 테이블은 README 코드 스타일대로 복수형 `snake_case`(`users`, `blocks`)로 만든다.
- 공통 코드는 README의 `common/`이 아니라 실제 패키지인 `com.dothis.fintris.global` 아래에 둔다.

## 명령어

```bash
./gradlew build
./gradlew bootRun
./gradlew test
```
