# FINTRIS API 명세 v4 (앱 시안 반영)

> 작성일: 2026-10-25 · 작성: 이건하 (Backend)
> 기준: 피그마 `FINTRIS 앱 시안` 시안 v2 > `FINTRIS_기능명세서.md` > `FINTRIS_개발_컨텍스트_v3.md`. **v3 명세는 이 문서로 대체한다.**
> P0 = 중간발표(11/13)까지 · P1 = 최종발표(12/18)까지 · **[TBD]** = 팀 결정 필요
> 이번 개정에서 손대지 않은 것(팀 결정 대기): 프리미엄 플랜 유지 여부, 알림 받는 시간 기본값(시안 9~22시 vs 명세 7~23시), 금융관리 점수 포함 여부

---

## v3 → v4 바뀐 점 요약

| 구분 | v3 | v4 | 근거 화면 |
| --- | --- | --- | --- |
| 응답 형식 | `success` / TBD | **`isSuccess` + `S200`/`S201`** (현재 코드 기준 확정) | PR #8 |
| 동의 항목 | 7개 (AI_PROCESSING, NOTIFICATION_READ, PUSH 포함) | **5개**: TERMS · PRIVACY · CODEF · AGE_14(필수) · MARKETING(선택) | 00-2 |
| 온보딩 | 연동 건너뛰기(`linkSkipped`) 가능 | **연동 필수**, 3단계(연동 → 튜토리얼 → 블록 구성) | 00-4, 04 |
| 미배분 | 잔액 − 블록 합계로 계산만 | **나눌 돈(대기 자금)** 을 출처별로 저장: 절약분 · 정기 입금 · 추가 입금 · 기타 | 01, 06, 06-B, 06-C |
| 수입 | 없음 | **입금원(수입 패턴)** 저장, 정기/추가 입금 구분, 다음 입금 예정일 | 06-B, 09 |
| 미리보기 | 이동·절약 배분 각각 따로 | **`POST /blocks/simulate` 하나로 통일**, ImpactList 형식 | 03-2, 06, 규칙 보드 |
| 이동 | 즉시 이동만 | **"다음 정기 입금 때 되돌리기" 예약** 추가 | 03-2 |
| 홈 | 미배분 금액 | 나눌 돈 목록, 계좌별 카드, 제안 배너, 블록 물 높이·D-day | 01, 01-1 |
| 블록 상세 | 공통 필드 | **유형별 상세** (소진 속도 그래프, 예상 달성일, 납부 기록) | 02, 02-1, 02-2 |
| 거래 목록 | 거래만, 페이지 | **날짜별 묶음 + 배분 기록 + 요약 + 계좌 필터** | 09, 09-2 |
| 목표 상세 | `/goals/{id}` | 블록 상세(`GOAL`)로 통합, 달성·기한 처리 선택지 변경 | 02-1, 17, 17-1 |
| 절약 배분 | `/saving-goals/{id}/allocations` | 확정 시 **절약분 대기 자금으로 이동**, 배분은 대기 자금 API로 | 05, 06 |

---

## 0. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 접두사 | `/api/v1` (아래 Endpoint에서는 생략, `WebConfig`가 자동으로 붙임) |
| 인증 | 로그인 이후 모든 API에 `Authorization: Bearer {accessToken}`. 다른 사용자의 리소스 요청 시 `403` |
| 금액 | 원 단위 정수(JSON number, Java `long`). 앱은 천 단위 쉼표와 "원"만 붙인다 |
| 날짜·시간 | ISO-8601, Asia/Seoul (`2026-10-14T09:00:00+09:00`), 날짜만은 `2026-10-14` |
| 페이지 | `?page=0&size=20` (Spring Pageable) |
| 동시성 | 블록 잔액을 바꾸는 요청은 `version`을 함께 보내고, 충돌 시 `409 BLOCK_VERSION_CONFLICT` |
| 에러 코드 | `도메인_원인` (예: `BLOCK_DEFAULT_UNDELETABLE`) |
| 상태 표시 | 색만으로 구분하지 않도록 상태 코드와 함께 `statusLabel`(안전·주의·위험)을 준다 |

응답 형식 **[확정]** — 현재 dev 코드(`ApiResponse`) 기준

```json
{ "isSuccess": true, "code": "S200", "message": "요청에 성공했습니다.", "result": { } }
{ "isSuccess": false, "code": "BLOCK_NOT_FOUND", "message": "블록을 찾을 수 없습니다.", "result": null }
```
성공 코드: 조회·수정·삭제 `S200`, 생성 `S201`, 비동기 시작 `S202`.

### 공통 객체

**블록 객체** (`BlockResponse`)
```json
{
  "blockId": 1,
  "type": "SPENDING",
  "name": "생활비",
  "icon": "wallet",
  "color": "I",
  "balance": 950000,
  "capacity": 1500000,
  "fillRate": 63,
  "isDefault": true,
  "targetAmount": null,
  "startDate": "2026-10-01",
  "dueDate": "2026-10-31",
  "dDay": null,
  "rule": { "monthlyAmount": 1500000, "startDay": 1 },
  "status": "CAUTION",
  "statusLabel": "주의",
  "statusSubLabel": "소진 빠름",
  "displayOrder": 2,
  "createdBy": "AI",
  "version": 7
}
```
- `type`: `SPENDING`(사용형) · `GOAL`(목표형) · `FIXED`(고정지출형)
- `color`: 블록 색 코드 `I`·`O`·`T`·`S`·`Z`·`J`·`L` (상태에 따른 채도 변화는 FE가 디자인 규칙으로 계산)
- `capacity`: 비커 크기 기준 금액 — 사용형 = 이번 기간 배분액, 목표형 = 목표 금액, 고정지출형 = 납부액
- `fillRate`(물 높이, %): 사용형 = 남은 돈 ÷ 배분액, 목표형 = 모은 돈 ÷ 목표, 고정지출형 = 확보액 ÷ 납부액
- `dDay`: 고정지출형 = 다음 납부일까지, 목표형 = 목표일까지 남은 일수 / 사용형 `null`
- `statusSubLabel`: 상태 칩 옆 짧은 이유 (예: `소진 빠름`, `2주 늦어짐`, `납부 준비 끝`)
- `rule` (유형별)
  - 사용형: `{ "monthlyAmount": 1500000, "startDay": 1 }` (매월 1일 ~ 말일)
  - 목표형: `{ "monthlyTarget": 200000 }`
  - 고정지출형: `{ "payDay": 25, "payAmount": 600000, "matchCondition": { "payee": "김*수", "amountMode": "EXACT", "minAmount": 600000, "maxAmount": 600000, "dayWindow": 3, "linkedAccountId": 3 }, "alerts": { "shortageBefore3Days": true, "askFillFromOther": true, "paidConfirmed": false } }`
    - `amountMode`: `EXACT`(그대로) · `RANGE`(범위, 기본 ±10%)

**대기 자금 객체** (`WaitingFundResponse`) — 홈의 "나눌 돈"
```json
{ "waitingFundId": 12, "source": "SAVING", "label": "절약분", "description": "9월 절약 확정", "amount": 87000, "originalAmount": 87000, "createdAt": "2026-10-01T06:00:00+09:00" }
```
- `source`: `SAVING`(절약분) · `REGULAR_INCOME`(정기 입금·월급·용돈) · `EXTRA_INCOME`(추가 입금, 정기 아님) · `OTHER`(초기 미배분·블록 삭제 반환 등)
- `amount`가 0이 되면 사라진다(목록에서 빠짐)

**결과 항목 객체** (`ImpactItem`) — 미리보기 결과 목록(ImpactList)
```json
{ "type": "goal_date", "severity": "warn", "blockId": 3, "title": "여행 목표가 약 2주 늦어져요", "detail": "이번 달 확보액 120,000 → 70,000 (월 목표 200,000)", "before": "2026-12-31", "after": "2027-01-14" }
```
| type | 언제 | title 문장 틀 |
| --- | --- | --- |
| `status_change` | 블록 상태가 바뀜 | `{블록} {전 상태} → {후 상태}` |
| `goal_date` | 목표형 예상 달성일이 바뀜 | `{블록} 목표가 {차이} {빨라져요/늦어져요}` |
| `monthly_secured` | 목표형 이번 달 확보액이 바뀜 | `{블록} 이번 달 확보액 {전} → {후}` (주로 detail) |
| `payment` | 고정지출형 납부 확보 여부 | `{블록} 납부 영향 없음` / `{납부일}에 {n}원 모자라요` |
| `daily_budget` | 사용형 하루 쓸 돈이 바뀜 | `하루 쓸 돈 {전} → {후}` |
| `saving_pace` | 절약 목표 지금 속도 예측 | `{카테고리} 지금 속도면 +{n}` |

- `severity`: `danger`(규칙 깨짐) · `warn`(나빠짐) · `good`(좋아지거나 지켜짐) · `info`(숫자만 바뀜)
- 서버가 `danger → warn → good → info` 순으로 정렬해서 모두 보낸다. 같은 블록의 변화가 여러 개면 한 항목으로 합치고 나머지는 `detail`로
- 문장은 위 틀만 쓰고 숫자는 서버 계산값을 그대로 넣는다(LLM 불필요). 새 문장이 필요하면 `type`을 추가한다

**제안 카드 객체** (`SuggestionCard`) — 에이전트가 행동을 권할 때만
```json
{ "suggestionKey": "burn-rate-1", "text": "이번 주 배달을 2번만 줄이면 다시 안전으로 돌아와요", "title": "생활비가 17% 빨리 줄고 있어요", "action": { "type": "OPEN_SAVING_SUGGESTIONS", "payload": null } }
```
| 조건 (서버 규칙) | 문장 | `action.type` |
| --- | --- | --- |
| 대기 자금이 있고 목표형 블록이 이번 달 확보액보다 모자람 | "절약분 50,000원을 여행에 넣으면 12월 31일에 맞출 수 있어요" | `ALLOCATE` (payload: 대기 자금·블록·금액) |
| 사용형 블록 소진 속도가 평소보다 15% 이상 빠름 | "이번 주 배달을 2번만 줄이면 다시 안전으로 돌아와요" | `OPEN_SAVING_SUGGESTIONS` |
| 고정지출 납부 3일 전 확보 부족 | "저축에서 60,000원 옮기면 월세 납부일을 지킬 수 있어요" | `TRANSFER` (payload: from·to·금액) |
| 정기 입금이 들어왔고 남는 돈이 있음 | "남는 345,000원 중 80,000원을 여행에 더 넣으면 2주를 따라잡아요" | `ALLOCATE` |

**거래 객체** (`TransactionResponse`)
```json
{
  "transactionId": 101,
  "linkedAccount": { "linkedAccountId": 3, "institutionName": "신한카드", "last4": "12" },
  "occurredAt": "2026-10-13T19:22:00+09:00",
  "amount": 38000,
  "direction": "OUT",
  "merchantOrPayee": "네이버페이",
  "source": "CODEF",
  "status": "NEEDS_REVIEW",
  "category": { "categoryId": 7, "code": "LIVING", "name": "생활/생필품" },
  "categoryUncertain": true,
  "classifiedBy": "AUTO",
  "contextTag": null,
  "contextTagLabel": null,
  "block": { "blockId": 1, "name": "생활비" },
  "excluded": false,
  "isCanceled": false,
  "inputMethod": null
}
```
- `source`: `CODEF` · `NOTIFICATION` · `MANUAL` / `status`: `PROVISIONAL`(임시) · `CONFIRMED` · `NEEDS_REVIEW`(확인 필요)
- `categoryUncertain: true`면 FE가 `생필품?`처럼 물음표로 표시
- `contextTagLabel`: 맥락 답변 표시용 (예: `야근`)
- `inputMethod`: `MANUAL`일 때 `"빠른 입력"` 표시용

**토큰 객체** (`TokenResponse`)
```json
{ "userId": 1, "accessToken": "eyJ...", "refreshToken": "eyJ...", "accessTokenExpiresIn": 1800, "isNewUser": false, "onboardingStep": "DONE" }
```
`onboardingStep`: `CONSENT` · `LINK` · `TUTORIAL` · `BLOCKS` · `DONE` (FE가 앱 시작 시 이 단계 화면으로 보냄)

---

## 1. 회원가입 및 로그인 — F-17 · P0 · 김지현

v3와 같다. 토큰 객체의 `onboardingDone` → `onboardingStep`만 바뀜.

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 회원가입 | 00-1 회원가입 | POST | `/auth/signup` |
| 이메일 인증 | 00-1 회원가입 | GET | `/auth/verify-email?token=` |
| 이메일 로그인 | 00 로그인 | POST | `/auth/login` |
| 로그아웃 | 15 마이 | POST | `/auth/logout` |
| 액세스 토큰 재발급 | (자동) | POST | `/auth/refresh` |
| 소셜 로그인 시작 | 00 로그인 | GET | `/auth/{provider}` (google·kakao) |
| 소셜 로그인 콜백 | 00 로그인 | GET | `/auth/{provider}/callback?code=` |
| 소셜 회원가입 완료 | 00-1 회원가입 | POST | `/auth/{provider}/complete` |
| 비밀번호 변경 | 15 마이 | PATCH | `/auth/password` |
| 회원 탈퇴 | 15 마이 | DELETE | `/auth/withdraw` |

### 회원가입 `POST /auth/signup`
**Request body** `{ "email": "user@email.com", "password": "TestPassword123!" }`
**Response body** `201` — `result: { "email": "user@email.com" }` (인증 메일 발송)
에러: `AUTH_EMAIL_DUPLICATED`, `AUTH_INVALID_PASSWORD_FORMAT`
비고: 시안 00-1은 비밀번호 입력 중 "사용할 수 있는 비밀번호에요"를 표시한다. 형식 검사는 FE에서 먼저 하고 서버도 같은 규칙으로 검증한다.

### 이메일 인증 `GET /auth/verify-email?token={token}`
**Response body** `200` — `result: { "email": "user@email.com" }` / 에러: `AUTH_VERIFY_TOKEN_EXPIRED`

### 이메일 로그인 `POST /auth/login`
**Request body** `{ "email": "user@email.com", "password": "TestPassword123!" }`
**Response body** `200` — `result`에 토큰 객체
에러: `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_NOT_VERIFIED`, `AUTH_LOGIN_LOCKED` (5회 연속 실패 시 10분, `result`에 `lockedUntil`)

### 로그아웃 `POST /auth/logout`
**Request body** `{ "refreshToken": "eyJ..." }` → `200`, `result: null`

### 액세스 토큰 재발급 `POST /auth/refresh`
**Request body** `{ "refreshToken": "eyJ..." }` → `200`, `result`에 토큰 객체 (리프레시 토큰도 새로 발급)
에러: `AUTH_REFRESH_TOKEN_INVALID` → FE는 로그인 화면으로

### 소셜 로그인 `GET /auth/{provider}` · `GET /auth/{provider}/callback?code=` · `POST /auth/{provider}/complete`
- 콜백 기존 회원: `result`에 토큰 객체 (`isNewUser: false`)
- 콜백 신규 회원: `result: { "isNewUser": true, "signupToken": "eyJ...", "email": "user@gmail.com" }` (카카오는 `email` null 가능, `signupToken` 10분)
- 가입 완료 `{ "signupToken": "eyJ..." }` → `201`, 토큰 객체 (`onboardingStep: "CONSENT"`)
> **[TBD]** 서버 리다이렉트 vs 앱 SDK + 서버 검증 (FE와 합의)

### 비밀번호 변경 `PATCH /auth/password`
**Request body** `{ "currentPassword": "...", "newPassword": "..." }` → `200`
에러: `AUTH_INVALID_CREDENTIALS`, `AUTH_SOCIAL_USER_NO_PASSWORD`

### 회원 탈퇴 `DELETE /auth/withdraw`
**Request body** `{ "password": "..." }` (이메일 가입자만) → `200`
비고: 즉시 CODEF 연동 해제·토큰 전체 폐기, 거래·블록·대화 데이터는 30일 안에 삭제.

---

## 2. 동의 및 온보딩 — F-18 · P0 · 이건하

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 동의 항목 목록 | 00-2 약관 동의 | GET | `/consents/items` |
| 동의 저장 (여러 항목) | 00-2 약관 동의 | POST | `/users/me/consents` |
| 내 동의 현황 | 15 마이 · 약관 | GET | `/users/me/consents` |
| 동의 변경·철회 | 15 마이 · 약관 | PATCH | `/users/me/consents/{item}` |
| 온보딩 상태 조회 | 앱 시작 | GET | `/users/me/settings` |
| 온보딩 상태 수정 | 튜토리얼 · 블록 구성 | PATCH | `/users/me/settings` |

> 바뀐 점: 동의 항목을 시안 00-2 기준 5개로 축소. `AI_PROCESSING`·`NOTIFICATION_READ`·`PUSH`·`PRODUCT_INFO` 삭제, `AGE_14`·`MARKETING` 추가. 계좌 연동이 온보딩 필수 단계(00-4 "연동 필수")가 되어 `linkSkipped`(체험 모드) 삭제, 온보딩을 `onboardingStep` 단계로 관리.

동의 항목 코드
| item | 화면 문구 | 구분 |
| --- | --- | --- |
| `TERMS` | 서비스 이용약관 | 필수 |
| `PRIVACY` | 개인정보 수집·이용 | 필수 |
| `CODEF` | 금융거래정보 조회 (CODEF를 통해 연동한 계좌·카드의 잔액과 거래 내역) | 필수 |
| `AGE_14` | 만 14세 이상이에요 | 필수 |
| `MARKETING` | 혜택·소식 알림 받기 | 선택 |

### 동의 항목 목록 `GET /consents/items`
**Request body** 없음 (인증 불필요)
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "동의 항목 조회에 성공했습니다.",
  "result": {
    "items": [
      { "item": "TERMS", "title": "서비스 이용약관", "description": null, "required": true, "docVersion": "2026-10-01", "docUrl": "https://..." },
      { "item": "PRIVACY", "title": "개인정보 수집·이용", "description": null, "required": true, "docVersion": "2026-10-01", "docUrl": "https://..." },
      { "item": "CODEF", "title": "금융거래정보 조회", "description": "CODEF를 통해 연동한 계좌·카드의 잔액과 거래 내역", "required": true, "docVersion": "2026-10-01", "docUrl": "https://..." },
      { "item": "AGE_14", "title": "만 14세 이상이에요", "description": null, "required": true, "docVersion": "2026-10-01", "docUrl": null },
      { "item": "MARKETING", "title": "혜택·소식 알림 받기", "description": null, "required": false, "docVersion": "2026-10-01", "docUrl": "https://..." }
    ],
    "notice": "가져온 금융 정보는 블록 계산과 분석에만 써요."
  }
}
```

### 동의 저장 `POST /users/me/consents`
**Request body**
```json
{
  "consents": [
    { "item": "TERMS", "agreed": true, "docVersion": "2026-10-01" },
    { "item": "PRIVACY", "agreed": true, "docVersion": "2026-10-01" },
    { "item": "CODEF", "agreed": true, "docVersion": "2026-10-01" },
    { "item": "AGE_14", "agreed": true, "docVersion": "2026-10-01" },
    { "item": "MARKETING", "agreed": false, "docVersion": "2026-10-01" }
  ]
}
```
**Response body** `201` — `result: { "onboardingStep": "LINK" }`
에러: `CONSENT_REQUIRED_MISSING` (필수 4개 중 미동의, `result`에 빠진 항목 목록), `CONSENT_ITEM_INVALID`
비고: 저장할 때마다 이력 행 추가(덮어쓰지 않음). 저장 성공 시 온보딩 단계가 `LINK`로 넘어간다.

### 내 동의 현황 `GET /users/me/consents`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "동의 현황 조회에 성공했습니다.",
  "result": { "consents": [ { "item": "MARKETING", "agreed": false, "docVersion": "2026-10-01", "changedAt": "2026-10-13T10:00:00+09:00" } ] }
}
```
비고: item별 가장 최근 행만.

### 동의 변경·철회 `PATCH /users/me/consents/{item}`
**Request body** `{ "agreed": false, "docVersion": "2026-10-01", "confirmed": true }`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "동의가 변경되었습니다.",
  "result": { "item": "CODEF", "agreed": false, "impact": { "unlinkAccounts": true, "dataDeletionDate": "2026-11-24" } }
}
```
비고: 필수 항목 철회를 `confirmed: false`로 보내면 변경하지 않고 `impact`만 돌려준다(재확인 단계). `TERMS`·`PRIVACY`·`AGE_14` 철회는 탈퇴로 안내(`CONSENT_WITHDRAW_REQUIRES_LEAVE`).

### 온보딩 상태 조회 `GET /users/me/settings`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "설정 조회에 성공했습니다.",
  "result": { "onboardingStep": "TUTORIAL", "hasLinkedAccount": true, "tutorialDone": false, "blocksConfirmed": false, "nickname": null, "plan": "FREE" }
}
```
- `onboardingStep` 결정: 필수 동의 없음 → `CONSENT` / 연동 계좌 없음 → `LINK` / 튜토리얼 안 봄 → `TUTORIAL` / 블록 확정 안 함 → `BLOCKS` / 나머지 → `DONE`
- `nickname` **[TBD: 필요 여부]** — 마이(15)에는 이름이 보임

### 온보딩 상태 수정 `PATCH /users/me/settings`
**Request body** (바꿀 필드만) `{ "tutorialDone": true }`
**Response body** `200` — 조회와 같은 `result`
비고: `blocksConfirmed`는 초기 블록 확정 API(6장)가 자동으로 바꾼다.

---

## 3. 계좌·카드 연동 — F-19 · P0 · 김지현

v3와 같다. 온보딩 1/3 단계(00-4)에서 은행·카드를 여러 곳 고르는 흐름을 반영해 연동 요청을 배열로 받을 수 있게 했다.

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 연동 가능 기관 목록 | 00-4 연동 | GET | `/institutions?type=BANK\|CARD` |
| 계좌·카드 연동 | 00-4 연동 | POST | `/linked-accounts` |
| 연동 목록 | 15 마이 · 연결된 계좌·카드 | GET | `/linked-accounts` |
| 연동 해제 | 15 마이 | DELETE | `/linked-accounts/{linkedAccountId}` |
| 수동 동기화 | 15 마이 · 새로 가져오기 | POST | `/linked-accounts/sync` |
| 동기화 상태 조회 | 홈 | GET | `/linked-accounts/sync-status` |

### 연동 가능 기관 목록 `GET /institutions?type=BANK`
`result: { "institutions": [ { "institutionCode": "0004", "name": "KB국민", "type": "BANK", "loginTypes": ["ID_PASSWORD", "CERTIFICATE"] } ] }`

### 계좌·카드 연동 `POST /linked-accounts`
**Request body** (인증정보는 FE가 CODEF 공개키로 암호화)
```json
{ "institutions": [ { "institutionCode": "0004", "type": "BANK", "loginType": "ID_PASSWORD", "loginId": "user01", "encryptedPassword": "RSA암호문..." } ] }
```
**Response body** `201`
```json
{
  "isSuccess": true, "code": "S201", "message": "연동에 성공했습니다. 거래를 가져오는 중입니다.",
  "result": {
    "linkedAccounts": [ { "linkedAccountId": 3, "institutionName": "KB국민", "type": "BANK", "accountName": "입출금", "accountNumberLast4": "1234", "balance": 2687000 } ],
    "failed": [],
    "initialSync": "IN_PROGRESS",
    "onboardingStep": "TUTORIAL"
  }
}
```
에러: `LINK_CONSENT_REQUIRED`, `LINK_INSTITUTION_AUTH_FAILED`(기관 메시지 포함, 일부만 실패하면 `failed`에 담고 201), `LINK_TIMEOUT`(30초 초과 → 백그라운드 재시도)
비고: 최근 3개월 거래 수집 → 분류 → **입금원 분석(정기/추가 입금)** → 초기 블록 구성안 준비. connectedId는 응답에 넣지 않는다.

### 연동 목록 `GET /linked-accounts`
`result: { "totalBalance": 4577000, "lastSyncedAt": "2026-10-14T09:00:00+09:00", "linkedAccounts": [ { "linkedAccountId": 3, "institutionName": "KB국민", "type": "BANK", "accountName": "입출금", "accountNumberLast4": "1234", "balance": 2687000, "isActive": true, "status": "ACTIVE", "lastSyncedAt": "..." } ] }`
`status`: `ACTIVE` · `EXPIRED`(재연동 안내) · `ERROR`

### 연동 해제 `DELETE /linked-accounts/{id}` · 수동 동기화 `POST /linked-accounts/sync` (`202`, `SKIPPED` 가능) · 동기화 상태 `GET /linked-accounts/sync-status`
v3와 같다. **[TBD]** connectedId 단위 테이블 분리 여부.

---

## 4. 거래 — F-19·F-20·F-21·F-25·F-26 · P0 · 김지현

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 결제 알림 임시 거래 등록 | (백그라운드) | POST | `/transactions/notifications` |
| 결제 알림 승인 취소 | (백그라운드) | POST | `/transactions/notifications/cancel` |
| 빠른 입력 미리보기 | 10 빠른 입력 | POST | `/transactions/manual/preview` |
| 빠른 입력 저장 | 10 빠른 입력 | POST | `/transactions/manual` |
| 거래 목록 (날짜별) | 09 내역, 09-2 계좌별 | GET | `/transactions` |
| 거래 상세 | 09 내역 | GET | `/transactions/{transactionId}` |
| 카테고리 수정 | 09 내역 | PATCH | `/transactions/{transactionId}/category` |
| 확인 필요 거래 처리 | 09 내역 (유지·삭제) | PATCH | `/transactions/{transactionId}/review` |
| 받을 돈 목록 / 정산 | 거래 상세 | GET / PATCH | `/receivables`, `/receivables/{receivableId}` |
| 카테고리 목록 | 09-1, 10 | GET | `/categories` |
| 결제 알림 허용 앱 목록 | (앱 시작) | GET | `/notification-apps` |

> 바뀐 점: 거래 목록을 날짜별 묶음 + 블록 배분 기록 + 상단 요약으로 변경, 계좌 필터 추가.

### 결제 알림 임시 거래 등록 · 승인 취소
v3와 같다. 응답의 `todayRemaining`은 홈 `dailyGuide.todayRemaining`과 같은 값.

### 빠른 입력 미리보기 `POST /transactions/manual/preview`
**Request body** `{ "text": "붕어빵 3000" }`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "입력 내용을 해석했습니다.",
  "result": {
    "amount": 3000, "description": "붕어빵", "occurredDate": "2026-10-14",
    "category": { "categoryId": 3, "code": "CAFE", "name": "카페/간식" },
    "block": { "blockId": 1, "name": "생활비", "isDefault": true },
    "possibleAtmDuplicate": null
  }
}
```
비고: 같은 금액 ATM 출금이 있으면 `possibleAtmDuplicate`로 이중 차감 여부를 묻는다. 파서 실패 시 `MANUAL_PARSE_FAILED`.

### 빠른 입력 저장 `POST /transactions/manual`
**Request body** `{ "amount": 3000, "description": "붕어빵", "occurredDate": "2026-10-14", "categoryId": 3, "skipAtmTransactionId": null }`
**Response body** `201` — 거래 객체(`source: MANUAL`, `status: CONFIRMED`) + `todayRemaining`

### 거래 목록 `GET /transactions?month=2026-10&linkedAccountId=&blockId=&filter=ALL&page=0&size=30`
`filter`: `ALL` · `NEEDS_REVIEW`(확인 필요) · `PROVISIONAL`(임시)
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "거래 목록 조회에 성공했습니다.",
  "result": {
    "summary": {
      "period": { "from": "2026-10-01", "to": "2026-10-14" },
      "spent": 588000, "provisionalCount": 1,
      "income": 0,
      "nextExpectedIncome": { "label": "월급", "expectedDate": "2026-10-25" },
      "account": null
    },
    "pendingQuestion": { "questionId": 55, "text": "네이버페이 38,000원, 무엇을 샀는지 알려줄래요?", "subText": "답해주면 소비 분석이 정확해져요" },
    "counts": { "needsReview": 2, "provisional": 1 },
    "days": [
      {
        "date": "2026-10-14", "dayTotal": -15300,
        "items": [
          { "kind": "TRANSACTION", "transaction": { "...거래 객체" }, "notice": "카드 내역이 들어오면 자동으로 확정돼요" }
        ]
      },
      {
        "date": "2026-10-10", "dayTotal": -30800,
        "items": [
          { "kind": "TRANSACTION", "transaction": { "...빠른 입력 거래" }, "notice": "직접 넣은 거래가 이틀째 카드 내역에 없어요. 실제로 결제한 게 맞나요?", "reviewActions": ["KEEP", "DELETE"] }
        ]
      },
      {
        "date": "2026-10-01", "dayTotal": 0,
        "items": [
          { "kind": "ALLOCATION", "allocation": { "title": "10월 블록 배분", "blockCount": 5, "note": "앱 안에서만 나눈 돈이라 쓴 돈에 안 잡혀요" } }
        ]
      }
    ],
    "hasNext": true
  }
}
```
- `linkedAccountId`를 주면(09-2) `summary.account`에 `{ "linkedAccountId": 3, "institutionName": "KB국민", "last4": "1234" }`, 금액은 그 계좌 기준
- `nextExpectedIncome`은 입금원 분석 결과(5장 홈과 같은 값). 없으면 `null`
- `pendingQuestion`은 가장 최근 미답변 맥락 질문 1개

### 거래 상세 · 카테고리 수정 · 확인 필요 처리 · 받을 돈 · 카테고리 목록 · 허용 앱 목록
v3와 같다. 확인 필요 처리 `action`: `KEEP`(유지) · `DELETE`(삭제, 블록 잔액 복구).

---

## 5. 홈 — F-10·F-11 · P0 · 이건하

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 홈 조회 | 01 홈, 01-1 은행 카드 | GET | `/home` |

> 바뀐 점: `unallocatedAmount` → **나눌 돈 목록(`waitingFunds`)**. 계좌별 카드(`accounts`), 제안 배너(`suggestion`), 블록 물 높이·D-day 추가. 오늘 쓸 수 있는 돈 계산은 **[설계 중]** 불규칙 수입 로직(다음 입금까지 버틸 기간 기준)으로 바뀔 예정이며, 응답 형식은 유지한다.

### 홈 조회 `GET /home`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "홈 조회에 성공했습니다.",
  "result": {
    "asOf": "2026-10-14",
    "totalBalance": 4577000,
    "linkedAccountCount": 2,
    "dailyGuide": {
      "blockId": 1, "blockName": "생활비", "blockBalance": 950000,
      "todayAvailable": 52700, "todaySpent": 12300, "todayRemaining": 40400,
      "remainingDays": 18,
      "basis": "BLOCK_PERIOD"
    },
    "accounts": [
      { "linkedAccountId": 3, "institutionName": "KB국민", "accountName": "입출금", "last4": "1234", "balance": 2687000, "monthSpent": 115900 }
    ],
    "suggestion": { "suggestionKey": "burn-rate-1", "title": "생활비가 17% 빨리 줄고 있어요", "text": "이번 주 배달을 2번만 줄이면 다시 안전으로 돌아와요", "action": { "type": "OPEN_SAVING_SUGGESTIONS", "payload": null } },
    "waitingFunds": [
      { "waitingFundId": 12, "source": "SAVING", "label": "절약분", "description": "9월 절약 확정", "amount": 87000, "originalAmount": 87000, "createdAt": "..." },
      { "waitingFundId": 13, "source": "EXTRA_INCOME", "label": "추가 입금", "description": "10월 12일 입금", "amount": 120000, "originalAmount": 120000, "createdAt": "..." }
    ],
    "overAllocated": false,
    "blocks": [ { "...블록 객체" } ],
    "lastSyncedAt": "2026-10-14T09:00:00+09:00",
    "unreadNotificationCount": 2,
    "pendingQuestionCount": 1
  }
}
```
- `dailyGuide.basis`: `BLOCK_PERIOD`(현재: 기본 블록 잔액 ÷ 기간 남은 일수, 100원 내림) · `NEXT_INCOME`(불규칙 수입 로직 적용 후) — FE 표시 문구 "생활비 950,000원 남은 18일 기준"에 사용
- `accounts`: 홈 상단 카드를 넘기면 나오는 은행 카드(01-1). 첫 카드는 총 자산, 그다음부터 계좌별. 블록은 계좌와 무관하게 그대로
- `suggestion`: 제안 카드 객체, 없으면 `null`
- `waitingFunds`: 금액이 0보다 큰 것만. 비어 있으면 "나눌 돈" 영역을 숨긴다
- `overAllocated: true`(블록 합계 + 나눌 돈 > 실제 잔액)이면 경고 + 줄일 블록 선택

---

## 6. 블록 — F-04·F-06·F-11 · P0 · 이건하

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 블록 목록 | 블록 관리 | GET | `/blocks?type=` |
| 블록 상세 (유형별) | 02, 02-1, 02-2 | GET | `/blocks/{blockId}` |
| 블록 기록 (들어온 돈·납부 기록) | 02-1, 02-2 "전체 보기" | GET | `/blocks/{blockId}/history` |
| 블록 거래 목록 | 02 "생활비 거래 전체 보기" | GET | `/transactions?blockId=` (4장) |
| 초기 블록 구성안 | 04 블록 구성 | GET | `/blocks/proposals` |
| 초기 블록 확정 | 04 블록 구성 | POST | `/blocks/proposals/confirm` |
| 블록 생성 | 블록 추가 | POST | `/blocks` |
| 블록 수정 (규칙·납부 조건·알림 포함) | 블록 상세 | PATCH | `/blocks/{blockId}` |
| 블록 삭제 | 블록 상세 | DELETE | `/blocks/{blockId}` |
| 블록 순서 변경 | 01 홈 (끌어서 변경) | PATCH | `/blocks/order` |
| 카테고리 → 블록 연결 설정 | 블록 상세 | PUT | `/blocks/{blockId}/categories` |
| **이동·배분 미리보기** | 03-2, 06, 06-B, 06-C | POST | `/blocks/simulate` |
| 블록 간 이동 확정 | 03-2 | POST | `/blocks/transfers` |
| 상태 변경 이력 | — | GET | `/blocks/{blockId}/status-logs` (P1) |

> 바뀐 점: 블록 상세를 유형별로 확장(소진 속도·예상 달성일·납부 기록·상태 이유). `/blocks/transfers/preview`와 절약 배분 미리보기를 **`/blocks/simulate`로 통합**. 미배분 → 블록 배분(`/blocks/{id}/allocations`)은 **대기 자금 배분(6-1장)** 으로 대체. 이동에 "되돌리기 예약" 추가.

### 블록 목록 `GET /blocks?type=GOAL`
`result: { "blocks": [ 블록 객체 ] }`

### 블록 상세 `GET /blocks/{blockId}`
공통 응답: `block`(블록 객체), `statusReason`, `actions`, `ruleText`, 그리고 유형별 `detail`.

**사용형 (02 생활비)**
```json
{
  "isSuccess": true, "code": "S200", "message": "블록 조회에 성공했습니다.",
  "result": {
    "block": { "...블록 객체 (status: CAUTION, statusSubLabel: 소진 빠름)" },
    "statusReason": "배달·카페 지출이 지난달 같은 기간보다 9만 원 늘었어요. 이대로면 28일쯤 생활비가 바닥나요.",
    "actions": ["OPEN_SAVING_SUGGESTIONS", "FILL_FROM_OTHER"],
    "ruleText": "월 1,500,000원 · 매월 1일 시작",
    "detail": {
      "period": { "from": "2026-10-01", "to": "2026-10-31" },
      "remainingRate": 63, "remainingDays": 18,
      "today": { "available": 52700, "spent": 12300, "remaining": 40400 },
      "burn": {
        "paceDiffRate": 17,
        "paceLabel": "계획보다 17% 빠름",
        "actualSpent": 550000,
        "series": [ { "date": "2026-10-01", "planned": 48400, "actual": 51000 }, { "date": "2026-10-14", "planned": 677400, "actual": 550000 } ],
        "expectedDepletionDate": "2026-10-28"
      },
      "recentTransactions": [ { "...거래 객체 (최근 5건)" } ]
    }
  }
}
```

**목표형 (02-1 여행)**
```json
{
  "result": {
    "block": { "...블록 객체 (status: CAUTION, statusSubLabel: 2주 늦어짐, dDay: 78)" },
    "statusReason": "이번 달에 모은 돈이 월 목표보다 80,000원 적어요. 이대로면 1월 중순에 다 모여요. 9월 절약분 50,000원을 넣으면 12월 31일에 맞출 수 있어요.",
    "actions": ["POSTPONE_DUE_DATE", "PUT_WAITING_FUND"],
    "ruleText": "목표 800,000 · 12월 31일 · 매달 200,000",
    "detail": {
      "achievementRate": 40,
      "thisMonth": { "target": 200000, "secured": 120000, "needed": 80000 },
      "projection": {
        "onTargetDate": "2026-12-31",
        "currentPaceDate": "2027-01-15",
        "delayDays": 15,
        "label": "목표보다 2주 늦음",
        "series": [ { "month": "2026-08", "cumulative": 100000, "projected": false }, { "month": "2026-10", "cumulative": 320000, "projected": false }, { "month": "2027-01", "cumulative": 800000, "projected": true } ]
      },
      "recentInflows": [ { "date": "2026-10-01", "title": "10월 블록 배분", "subTitle": "10월 1일 월급에서", "amount": 120000 } ],
      "resolutionNeeded": null
    }
  }
}
```
`resolutionNeeded`: `ACHIEVED`(17 목표 달성 시트) · `EXPIRED`(17-1 기한 지남 시트) · `null`

**고정지출형 (02-2 월세)**
```json
{
  "result": {
    "block": { "...블록 객체 (status: SAFE, statusSubLabel: 납부 준비 끝, dDay: 11)" },
    "statusReason": null,
    "actions": [],
    "ruleText": "매월 25일 · 600,000원",
    "detail": {
      "securedRate": 100,
      "nextPayDate": "2026-10-25",
      "payAmount": 600000,
      "shortage": 0,
      "matchCondition": { "payee": "김*수", "amountMode": "EXACT", "minAmount": 600000, "maxAmount": 600000, "dayWindow": 3, "linkedAccount": { "linkedAccountId": 3, "institutionName": "국민", "last4": "34" } },
      "alerts": { "shortageBefore3Days": true, "askFillFromOther": true, "paidConfirmed": false },
      "onTimeStreak": 6,
      "recentPayments": [ { "date": "2026-09-25", "payee": "김*수", "account": "국민 ••34", "amount": 600000, "onTime": true } ]
    }
  }
}
```
비고: 납부 확인 조건에 맞는 출금이 나가면 '납부 완료'로 바꾸고, 블록은 다음 정기 입금 배분 때 다시 채운다.

### 블록 기록 `GET /blocks/{blockId}/history?page=0&size=20`
목표형은 들어온 돈(배분·이동·절약), 고정지출형은 납부 기록.
`result: { "items": [ { "date": "2026-10-01", "kind": "ALLOCATION", "title": "10월 블록 배분", "subTitle": "10월 1일 월급에서", "amount": 120000 } ], "hasNext": false }`
`kind`: `ALLOCATION` · `TRANSFER_IN` · `TRANSFER_OUT` · `SAVING` · `PAYMENT` · `CREATED`

### 초기 블록 구성안 `GET /blocks/proposals` (04, 온보딩 3/3)
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "블록 구성안을 만들었습니다.",
  "result": {
    "summary": { "monthlyAvgIncome": 3500000, "recurringCount": 2, "linkedBalance": 4920000, "allocated": 4800000, "unallocated": 120000 },
    "proposals": [
      {
        "proposalKey": "fixed-1", "type": "FIXED", "name": "월세", "icon": "home", "color": "L", "balance": 600000,
        "rule": { "payDay": 25, "payAmount": 600000, "matchCondition": { "payee": "김*수", "amountMode": "RANGE", "minAmount": 540000, "maxAmount": 660000, "dayWindow": 3, "linkedAccountId": 3 }, "alerts": { "shortageBefore3Days": true, "askFillFromOther": true, "paidConfirmed": false } },
        "foundFrom": "TRANSACTIONS",
        "reason": "지난 3개월 매월 25일 전후 김OO님께 60만 원을 보냈어요"
      },
      {
        "proposalKey": "spending-default", "type": "SPENDING", "name": "생활비", "icon": "wallet", "color": "I", "isDefault": true,
        "balance": 1500000, "rule": { "monthlyAmount": 1500000, "startDay": 1 },
        "aiSuggestedAmount": 1500000, "recent3mAvg": 1620000, "dailyApprox": 45100,
        "reason": "최근 3달 평균 1,620,000원 썼어요. 월세·통신비는 따로 뺐어요."
      }
    ],
    "addablePresets": ["SAVING", "EMERGENCY", "TRAVEL"],
    "aiAvailable": true
  }
}
```
비고: AI는 고정지출·생활비만 제안(시안 04 제목). 고정지출 탐지는 서버(3회 이상 + 금액 편차 10% + 날짜 편차 3일), `reason` 문장만 AI(실패 시 템플릿). 생활비 금액을 3달 평균보다 낮게 고치면 FE가 "최근 3달 평균보다 220,000원 적어요. 소진 주의"를 표시(값은 `recent3mAvg`로 계산). "확정하기 전엔 아무것도 저장되지 않아요".

### 초기 블록 확정 `POST /blocks/proposals/confirm`
**Request body** (사용자가 지우거나 고친 최종본, 순서 = `displayOrder`)
```json
{ "blocks": [ { "type": "FIXED", "name": "월세", "icon": "home", "color": "L", "balance": 600000, "rule": { "...": "..." }, "createdBy": "AI" } ] }
```
**Response body** `201` — `result: { "blocks": [ 블록 객체 ], "waitingFunds": [ 대기 자금 객체 (남은 금액, source: OTHER) ], "onboardingStep": "DONE" }`
에러: `BLOCK_INSUFFICIENT_FUNDS` (블록 합계 > 연동 잔액)

### 블록 생성 `POST /blocks`
**Request body** (목표형 예시)
```json
{ "type": "GOAL", "name": "여행", "icon": "plane", "color": "O", "targetAmount": 800000, "dueDate": "2026-12-31", "rule": { "monthlyTarget": null }, "initialFunding": { "waitingFundId": 13, "amount": 100000 } }
```
**Response body** `201` — `result: { "block": 블록 객체, "waitingFunds": [ 갱신된 대기 자금 ] }`
비고: 목표형 `monthlyTarget`을 비우면 권장값(남은 금액 ÷ 남은 개월, 올림). `initialFunding`은 선택(대기 자금에서 가져옴).
에러: `BLOCK_INVALID_TARGET_AMOUNT`, `BLOCK_INVALID_DUE_DATE`, `BLOCK_INVALID_PAY_DAY`, `WAITING_FUND_INSUFFICIENT`

### 블록 수정 `PATCH /blocks/{blockId}`
**Request body** (바꿀 필드만, 잔액은 이동·배분 API로만)
```json
{ "name": "월세", "rule": { "matchCondition": { "payee": "김*수", "amountMode": "EXACT", "minAmount": 600000, "maxAmount": 600000, "dayWindow": 3, "linkedAccountId": 3 }, "alerts": { "paidConfirmed": true } }, "version": 7 }
```
**Response body** `200` — 블록 객체 / 에러: `BLOCK_VERSION_CONFLICT` + 생성과 같은 검증 에러
비고: 02-2 "납부 확인 조건 고치기"와 알림 토글, 02-1 "목표일 미루기"(`dueDate`)가 이 API를 쓴다. `rule`은 보낸 키만 병합.

### 블록 삭제 `DELETE /blocks/{blockId}`
`result: { "blockId": 3, "returnedAmount": 240000, "waitingFund": { 대기 자금 객체 (source: OTHER) } }` / 에러: `BLOCK_DEFAULT_UNDELETABLE`

### 블록 순서 변경 `PATCH /blocks/order` · 카테고리 연결 `PUT /blocks/{blockId}/categories`
v3와 같다.

### 이동·배분 미리보기 `POST /blocks/simulate`
블록 간 이동(03-2)과 대기 자금 배분(06, 06-B, 06-C)이 같이 쓴다. 금액을 바꿀 때마다 FE가 300ms 쉬었다가 다시 요청한다.

**Request body**
```json
{
  "changes": [
    { "fromBlockId": 3, "toBlockId": 1, "amount": 50000 }
  ]
}
```
대기 자금에서 배분할 때: `{ "fromWaitingFundId": 12, "toBlockId": 3, "amount": 50000 }` (여러 개 가능)

**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "결과를 계산했습니다.",
  "result": {
    "blocks": [
      { "blockId": 3, "name": "여행", "balanceBefore": 320000, "balanceAfter": 270000, "version": 4 },
      { "blockId": 1, "name": "생활비", "balanceBefore": 950000, "balanceAfter": 1000000, "version": 7 }
    ],
    "items": [
      { "type": "goal_date", "severity": "warn", "blockId": 3, "title": "여행 목표가 약 2주 늦어져요", "detail": "이번 달 확보액 120,000 → 70,000 (월 목표 200,000)", "before": "2026-12-31", "after": "2027-01-14" },
      { "type": "status_change", "severity": "good", "blockId": 1, "title": "생활비 주의 → 안전", "detail": "하루 쓸 돈 52,700원 → 55,500원", "before": "CAUTION", "after": "SAFE" },
      { "type": "payment", "severity": "good", "blockId": 2, "title": "월세 납부 영향 없음", "detail": "10월 25일 600,000원 확보 유지" }
    ],
    "hiddenCount": 0,
    "shortfall": { "blockId": 1, "amount": 0 },
    "remainingWaiting": null,
    "requiresConfirm": false,
    "blocked": false,
    "blockedReason": null
  }
}
```
- `items`는 정렬된 전체 목록, FE는 3개까지 보여주고 `hiddenCount`(= 전체 − 3, 0 이상)로 "나머지 n개 자세히"를 만든다
- `shortfall`: 받는 블록이 모자란 금액 — 시트의 **[부족한 만큼]** 버튼 값
- `remainingWaiting`: 대기 자금 배분 시 "절약분 블록에 남는 돈"
- `blocked: true`: 보내는 블록·대기 자금보다 큰 금액 등 확정 불가(`blockedReason`)
- `requiresConfirm: true`: `danger` 항목이 있음(고정지출 납부 부족 등) → 한 번 더 확인
- 미리보기 값과 확정 값은 같아야 한다

### 블록 간 이동 확정 `POST /blocks/transfers`
**Request body**
```json
{
  "fromBlockId": 3, "toBlockId": 1, "amount": 50000, "reason": "MANUAL",
  "fromVersion": 4, "toVersion": 7, "confirmed": true,
  "autoReturn": { "enabled": true, "on": "NEXT_REGULAR_INCOME" }
}
```
`reason`: `MANUAL` · `SUGGESTION` · `AI` / `autoReturn`: 03-2 "다음 월급날(25일)에 여행 블록으로 50,000원 자동 되돌리기"
**Response body** `201`
```json
{
  "isSuccess": true, "code": "S201", "message": "블록 간 이동이 완료되었습니다.",
  "result": {
    "transferId": 34,
    "fromBlock": { "blockId": 3, "balance": 270000, "status": "CAUTION", "version": 5 },
    "toBlock": { "blockId": 1, "balance": 1000000, "status": "SAFE", "version": 8 },
    "dailyGuide": { "todayAvailable": 55500, "todayRemaining": 43200 },
    "scheduledReturn": { "scheduledTransferId": 7, "expectedDate": "2026-10-25", "amount": 50000 }
  }
}
```
에러: `TRANSFER_INSUFFICIENT_BALANCE`, `TRANSFER_SAME_BLOCK`, `TRANSFER_CONFIRM_REQUIRED`, `BLOCK_VERSION_CONFLICT`
비고: `@Transactional` + `@Version`. 되돌리기는 다음 정기 입금이 확인되면 실행하고, 그때 보내는 블록 잔액이 모자라면 가능한 만큼만 옮기고 알림.
되돌리기 취소: `DELETE /blocks/scheduled-transfers/{scheduledTransferId}`

---

## 6-1. 나눌 돈 (대기 자금) — F-05·F-21 · P0 · 이건하 **[신규]**

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 대기 자금 목록 | 01 홈 | GET | `/waiting-funds` |
| 배분 화면 데이터 | 06, 06-B, 06-C | GET | `/waiting-funds/{waitingFundId}/allocation-plan` |
| 배분 실행 | 06, 06-B, 06-C | POST | `/waiting-funds/{waitingFundId}/allocations` |
| 정기 입금 자동 배분 설정 | 06-B | PUT | `/income-sources/{incomeSourceId}/auto-allocation` |

> 개념: 블록에 아직 안 나눈 돈을 출처별로 모아둔 것. 항상 **블록 잔액 합 + 대기 자금 합 = 실제 잔액**이 성립해야 한다(차이가 나면 `OTHER`로 보정).
> 생기는 시점: 절약 목표 확정(생활비에서 꺼내 `SAVING`) / 정기 입금 확인(`REGULAR_INCOME`) / 정기가 아닌 입금(`EXTRA_INCOME`) / 초기 구성 남은 돈·블록 삭제 반환(`OTHER`).

### 대기 자금 목록 `GET /waiting-funds`
`result: { "waitingFunds": [ 대기 자금 객체 ], "total": 207000 }`

### 배분 화면 데이터 `GET /waiting-funds/{waitingFundId}/allocation-plan?mode=MANUAL`
`mode`: `MANUAL`(직접 고르기) · `AI`(AI 제안)
**Response body** `200` (06-B 정기 입금 예시)
```json
{
  "isSuccess": true, "code": "S200", "message": "배분안을 만들었습니다.",
  "result": {
    "waitingFund": { "waitingFundId": 20, "source": "REGULAR_INCOME", "label": "정기 입금 · 월급", "description": "10월 25일 회사명 입금", "amount": 3500000 },
    "sourceBreakdown": null,
    "autoAllocationEnabled": false,
    "rows": [
      { "blockId": 1, "name": "생활비", "type": "SPENDING", "hint": "사용형 · 월 1,500,000", "status": "SAFE", "prefilled": 1500000, "needed": true },
      { "blockId": 2, "name": "월세", "type": "FIXED", "hint": "고정지출 · 11월 25일 납부", "status": "SAFE", "prefilled": 600000, "needed": true },
      { "blockId": 4, "name": "여행", "type": "GOAL", "hint": "매달 200,000", "status": "CAUTION", "prefilled": 200000, "needed": true },
      { "blockId": 5, "name": "비상금", "type": "GOAL", "hint": "목표를 다 채웠어요", "status": "SAFE", "prefilled": 0, "needed": false }
    ],
    "leftover": 345000,
    "suggestion": { "suggestionKey": "income-extra", "text": "남는 345,000원 중 80,000원을 여행에 더 넣으면 늦어진 2주를 따라잡아요.", "action": { "type": "ALLOCATE", "payload": { "blockId": 4, "amount": 80000 } } }
  }
}
```
- 정기 입금: 블록마다 정해둔 다음 달 몫(사용형 `monthlyAmount`, 고정지출형 `payAmount`, 목표형 `monthlyTarget`)을 `prefilled`로 미리 채운다
- 절약분(06): `sourceBreakdown: [ { "label": "배달", "amount": 52000 }, { "label": "카페", "amount": 35000 } ]`, 안내 "아낀 돈은 생활비에서 꺼내서 나눠요"
- 추가 입금(06-C): 안내 "매달 들어오는 입금이 아니라서 절약분·월급과 따로 모아뒀어요"
- 결과 목록(배분하면 이렇게 바뀌어요)은 `/blocks/simulate`로 따로 요청

### 배분 실행 `POST /waiting-funds/{waitingFundId}/allocations`
**Request body**
```json
{
  "allocations": [ { "blockId": 4, "amount": 50000, "version": 4 }, { "blockId": 6, "amount": 37000, "version": 2 } ],
  "leftoverTo": "KEEP",
  "autoAllocateNextTime": false
}
```
- `leftoverTo`: `KEEP`(대기 자금에 남김) · `DEFAULT_BLOCK`(생활비에 넣기, 06 "생활비에 그냥 두기")
- `autoAllocateNextTime`: 정기 입금(06-B) "다음 정기 입금부터 이대로 자동으로 나누기"
**Response body** `201`
```json
{
  "isSuccess": true, "code": "S201", "message": "배분했습니다.",
  "result": { "waitingFund": { "waitingFundId": 12, "amount": 0 }, "blocks": [ { "blockId": 4, "balance": 370000, "status": "SAFE", "version": 5 } ], "dailyGuide": { "todayAvailable": 52700, "todayRemaining": 40400 } }
}
```
에러: `WAITING_FUND_INSUFFICIENT`, `BLOCK_VERSION_CONFLICT`
비고: 각 배분은 `block_transfers`에 `from_waiting_fund_id`로 기록(사유 `ALLOCATION`, 절약분이면 `SAVING`). 금액이 0이 되면 홈의 해당 카드가 사라진다.

### 정기 입금 자동 배분 설정 `PUT /income-sources/{incomeSourceId}/auto-allocation`
**Request body** `{ "enabled": true, "allocations": [ { "blockId": 1, "amount": 1500000 } ] }` → `200`

---

## 7. 금융 목표 (목표형 블록) — F-07·F-08 · P0 · 이건하

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 목표 목록 | 05 절약 · 진행 중 목표 | GET | `/goals` |
| 목표 상세 | 02-1 | GET | `/blocks/{blockId}` (6장, `GOAL`) |
| 목표 생성 · 수정 | 블록 추가 · 상세 | POST / PATCH | `/blocks`, `/blocks/{blockId}` |
| 달성·기한 경과 처리 | 17, 17-1 | POST | `/goals/{blockId}/resolution` |
| 목표 지키기 대안 | 16 | GET | `/goals/{blockId}/alternatives` (P1) |

> 바뀐 점: `/goals/{blockId}` 상세는 블록 상세로 통합. 달성·기한 경과 처리 선택지를 시안 17·17-1 기준으로 변경.

### 목표 목록 `GET /goals`
`result: { "goals": [ { "blockId": 4, "name": "여행", "balance": 320000, "targetAmount": 800000, "dueDate": "2026-12-31", "achievementRate": 40, "currentPaceDate": "2027-01-15", "delayDays": 15, "status": "CAUTION", "statusLabel": "주의" } ] }`

### 달성·기한 경과 처리 `POST /goals/{blockId}/resolution`
**Request body**
```json
{ "action": "EXTEND", "newDueDate": "2027-01-31", "newTargetAmount": null, "moveTo": null }
```
| 상황 | action | 동작 |
| --- | --- | --- |
| 달성 (17) | `KEEP` | 그대로 지키기 — 쓰지 않고 두고 매달 넣는 건 멈춤 |
| 달성 | `RAISE_TARGET` | 목표 올리기 (`newTargetAmount`) |
| 달성 | `MOVE` | 다른 블록으로 옮기기 (`moveTo: [ { blockId, amount } ]`) |
| 기한 지남 (17-1) | `EXTEND` | 기한 늘리기 (`newDueDate`), 이번 달 더 확보할 금액을 다시 계산 |
| 기한 지남 | `FINISH_AT_CURRENT` | 모은 만큼으로 끝내기 — 목표를 현재 잔액으로 바꾸고 달성 처리 |
| 기한 지남 | `CLEANUP` | 블록 정리 — 잔액을 다른 블록으로 옮기고(`moveTo`) 블록 삭제 |
| 둘 다 | `REMIND_LATER` | 지금 정하지 않음 — 3일 뒤 다시 알림 |

**Response body** `200` — `result: { "block": 블록 객체 또는 null(삭제), "movedBlocks": [ ... ] }`

---

## 8. 소비 분석 및 절약 — F-01·F-02·F-03·F-05 · P0

| API명 | 화면 | Method | Endpoint | 담당 |
| --- | --- | --- | --- | --- |
| 소비 분석 | 11 소비 분석 | GET | `/analysis/spending?month=` | 김지현 |
| 절약 화면 요약 | 05 절약 | GET | `/savings/overview` | 이건하 |
| 절약 후보 목록 | 05 줄일 수 있는 지출 | GET | `/savings/suggestions` | 김지현 |
| 절약 후보 관심 없음 | 05 | PATCH | `/savings/suggestions/{suggestionId}` | 김지현 |
| 제외된 지출 다시 포함 | 05 후보에서 뺀 지출 | PATCH | `/transactions/{transactionId}/saving-exclusion` | 김지현 |
| 절약 목표 설정 화면 데이터 | 12 절약 목표 설정 | GET | `/saving-goals/draft?suggestionId=` | 이건하 |
| 절약 목표 생성 | 12 | POST | `/saving-goals` | 이건하 |
| 절약 목표 목록 · 상세 | 05 진행 중인 목표 | GET | `/saving-goals`, `/saving-goals/{savingGoalId}` | 이건하 |
| 절약 목표 취소 | 05 | DELETE | `/saving-goals/{savingGoalId}` | 이건하 |

> 바뀐 점: 확정된 절약액은 **생활비 블록에서 꺼내 절약분 대기 자금(`SAVING`)으로** 옮긴다. 절약 배분 API는 대기 자금 배분(6-1장)으로 대체. 소비 분석에 같은 기간 비교·시간대·반복 지출 유형 추가.

### 소비 분석 `GET /analysis/spending?month=2026-10`
**Response body** `200`
```json
{
  "isSuccess": true, "code": "S200", "message": "소비 분석 조회에 성공했습니다.",
  "result": {
    "period": { "from": "2026-10-01", "to": "2026-10-14" },
    "compareTo": { "from": "2026-09-01", "to": "2026-09-14", "label": "9월 같은 기간" },
    "totalSpent": 588000, "previousSpent": 524000, "changeAmount": 64000, "changeRate": 12,
    "categories": [
      { "categoryId": 2, "code": "DELIVERY_DINING", "name": "배달", "count": 13, "amount": 182000, "changeAmount": 50000 },
      { "categoryId": 11, "code": "SUBSCRIPTION", "name": "구독", "count": 2, "amount": 27000, "changeAmount": 0 }
    ],
    "biggestIncrease": {
      "categoryId": 2, "name": "배달", "changeAmount": 50000, "changeRate": 38, "previousAmount": 132000,
      "explanation": "횟수가 9번에서 13번으로 늘었고, 한 번에 쓰는 돈은 비슷해요.",
      "timeOfDay": [ { "slot": "MORNING", "label": "아침 6~11시", "count": 0 }, { "slot": "LUNCH", "label": "점심 11~15시", "count": 2 }, { "slot": "EVENING", "label": "저녁 15~22시", "count": 2 }, { "slot": "NIGHT", "label": "밤 22시~", "count": 9 } ],
      "timeSummary": "13번 중 9번이 밤 10시 이후였어요",
      "excludedNote": "야근 식대로 표시한 2건(46,000원)은 절약 후보에서 뺐어요"
    },
    "recurring": [
      { "kind": "REPEAT", "label": "카페", "summary": "15회 · 스타벅스 9회, 그 외 6회", "amount": 76500 },
      { "kind": "DUPLICATE_SUBSCRIPTION", "label": "OTT 구독", "summary": "넷플릭스 · 티빙 같이 구독 중", "amount": 27000 },
      { "kind": "FIXED", "label": "통신비", "summary": "매월 15일 자동이체 · 고정지출 블록", "amount": 55000 }
    ],
    "savingPotential": { "monthlyAmount": 98000, "text": "배달이랑 카페만 조금 줄여도 한 달에 약 98,000원을 아낄 수 있어요. 여행 목표도 3주 빨라져요." },
    "previousAvailable": true
  }
}
```
비고: 금액·횟수는 서버 계산, `explanation`·`savingPotential.text`만 AI(실패 시 템플릿). 거래가 없으면 빈 상태 화면용 `"empty": true`.

### 절약 화면 요약 `GET /savings/overview` (05)
```json
{
  "result": {
    "confirmed": { "waitingFundId": 12, "month": "2026-09", "amount": 87000, "text": "생활비에서 꺼내 홈의 절약분 블록에 모아뒀어요" },
    "activeGoals": {
      "confirmDate": "2026-11-01", "expectedSaving": 86000, "limitSaving": 98000,
      "goals": [
        { "savingGoalId": 9, "category": "배달", "paceLabel": "지금 속도면 +41,000", "paceSaving": 41000, "spent": 41000, "limit": 72000, "spentUntilTodayPlan": 32400, "baseAmount": 132000, "endDate": "2026-10-31", "onPlan": false },
        { "savingGoalId": 10, "category": "카페", "paceLabel": "지금 속도면 +45,000", "paceSaving": 45000, "spent": 12000, "limit": 34000, "spentUntilTodayPlan": 15300, "baseAmount": 72000, "endDate": "2026-10-31", "onPlan": true }
      ]
    },
    "suggestionCount": 3, "suggestionTotal": 79500
  }
}
```
비고: `expectedSaving`은 지금까지 쓴 속도로 월말까지 쓴다고 본 예측, `limitSaving`은 한도대로 쓸 때.

### 절약 후보 목록 `GET /savings/suggestions`
```json
{
  "result": {
    "suggestions": [
      { "suggestionId": 21, "category": { "categoryId": 4, "code": "TRANSPORT", "name": "택시" }, "reason": "INCREASED", "reasonLabel": "3달 평균보다 +25%", "action": "월 6회 → 월 3회로 줄이기", "baseAmount": 84000, "baseCount": "월 6회", "expectedSaving": 42000, "status": "PENDING" },
      { "suggestionId": 22, "category": { "...": "편의점" }, "reason": "REPEATED", "reasonLabel": "반복 소비", "action": "주 5회 → 주 3회로 줄이기", "baseAmount": 61000, "baseCount": "주 5회", "expectedSaving": 24000, "status": "PENDING" },
      { "suggestionId": 23, "category": { "...": "OTT 구독" }, "reason": "DUPLICATE", "reasonLabel": "2개 중 1개만 유지하기", "action": "2개 중 1개만 유지하기", "baseAmount": 27000, "baseCount": null, "expectedSaving": 13500, "status": "PENDING" }
    ],
    "excluded": [ { "label": "배달 2건", "amount": 46000, "reason": "야근·업무로 답해서 줄일 수 없는 지출로 봤어요", "transactionIds": [77, 81] } ]
  }
}
```
`reason`: `INCREASED` · `REPEATED` · `DUPLICATE`

### 절약 목표 설정 화면 데이터 `GET /saving-goals/draft?suggestionId=21` (12)
```json
{
  "result": {
    "title": "택시 절약 목표", "action": "월 6회 → 월 3회로 줄이기",
    "baseAmount": 84000, "suggestedTarget": 42000, "expectedSaving": 42000,
    "period": { "from": "2026-10-15", "to": "2026-11-14" },
    "blockOptions": [ { "blockId": 4, "name": "여행", "hint": "2주 늦어지는 중" }, { "blockId": 6, "name": "저축", "hint": null } ],
    "notice": "목표를 넘겨도 기준(84,000원)보다 적게 쓰면 그만큼 절약으로 인정해요. 기간이 끝난 다음 날 실제 쓴 돈으로 확정하고, 그때 홈의 절약분 블록에 모아둬요."
  }
}
```

### 절약 목표 생성 `POST /saving-goals`
**Request body** `{ "categoryId": 4, "targetAmount": 42000, "startDate": "2026-10-15", "endDate": "2026-11-14", "linkedBlockId": 4, "suggestionId": 21 }`
(`linkedBlockId`는 선택 — "나중에"면 `null`)
**Response body** `201` — 절약 목표 객체
에러: `SAVING_GOAL_NO_EXPECTED_SAVING`(목표 ≥ 기준, 안내만), `SAVING_GOAL_INVALID_PERIOD`, `SAVING_GOAL_INVALID_AMOUNT`, `SAVING_GOAL_DUPLICATE_CATEGORY`
비고: `baseAmount`(직전 3개월 평균)는 서버 계산.

### 절약 목표 확정 (서버 스케줄러, API 없음)
기간이 끝난 다음 날 정기 동기화 후: 확정 절약액 = 기준 − 실제(0 미만이면 0) → 생활비 블록에서 그 금액을 꺼내 `SAVING` 대기 자금 생성(카테고리별 내역은 `sourceBreakdown`) → 알림 `SAVING_CONFIRMED`. `linkedBlockId`가 있으면 배분 화면에서 그 블록을 먼저 채워 보여준다.

### 절약 목표 목록·상세·취소
v3와 같다. 상태: `ACTIVE` → `CONFIRMED`(대기 자금으로 이동) → `ALLOCATED`(대기 자금이 모두 배분됨).

---

## 9. 맥락 질문 · 이상 징후 · 알림 — F-15·F-09·F-22 · P0 · 김지현

| API명 | 화면 | Method | Endpoint |
| --- | --- | --- | --- |
| 맥락 질문 목록 | 13 알림함 (질문) | GET | `/context-questions?status=PENDING` |
| 맥락 질문 답변 | 09-1, 07 | POST | `/context-questions/{questionId}/answer` |
| 규칙 저장 / 목록 / 삭제 | 09-1, 15 마이 | POST / GET / DELETE | `/context-rules`, `/context-rules/{ruleId}` |
| 알림함 | 13 알림함 | GET | `/notifications?category=` |
| 이상 징후 상세 | 13-1 | GET | `/notifications/{notificationId}` |
| 알림 읽음 처리 | 13 | PATCH | `/notifications/{notificationId}/read` |
| 알림 설정 조회 · 수정 | 14 알림 설정 | GET / PUT | `/notifications/settings` |
| 푸시 토큰 등록 · 삭제 | (앱 시작 · 로그아웃) | POST / DELETE | `/push-tokens`, `/push-tokens/{token}` |

> 바뀐 점: 알림함 분류(경고·질문·소식), 이상 징후 상세(최근 7일 추이·바닥나는 날·많이 쓴 곳), 질문 빈도 설정, 블록 경고 종류별 토글.

### 맥락 질문 목록·답변
v3와 같다. 09-1 기준 응답 예시:
```json
{ "questionId": 56, "transactionId": 120, "type": "UNKNOWN_ITEM", "text": "어제 결제한 네이버페이 38,000원, 무엇을 샀어요?", "meta": "10월 13일 화 19:22 · 신한카드 ••12", "options": [ { "tag": "LIVING", "label": "생필품" }, { "tag": "FOOD", "label": "식비" }, { "tag": "SHOPPING", "label": "의류" }, { "tag": "ETC", "label": "기타" }, { "tag": "DUTCH_PAY", "label": "나눠서 냄 (더치페이)" } ], "allowFreeText": true, "freeTextNotice": "적은 내용은 분류에만 쓰고 저장하지 않아요" }
```
답변 응답의 `resultText`: "생활/생필품으로 바꾸고 생활비 블록에 그대로 둬요", 반복이면 `suggestRule.message`: "지난번에도 이 시간대 네이버페이를 생필품으로 답했어요. 다음부터 묻지 않고 자동으로 적용할까요?"

### 알림함 `GET /notifications?category=ALL&page=0&size=20`
`category`: `ALL` · `WARNING`(경고) · `QUESTION`(질문) · `NEWS`(소식)
```json
{
  "result": {
    "counts": { "WARNING": 2, "QUESTION": 1 },
    "groups": [
      { "label": "오늘", "items": [ { "notificationId": 301, "category": "WARNING", "type": "BURN_RATE", "urgent": false, "title": "생활비가 평소보다 17% 빨리 줄고 있어요", "body": "최근 7일 하루 평균 68,000원. 이 속도면 28일쯤 바닥나요", "badge": "확인 필요", "read": false, "createdAt": "2026-10-14T09:00:00+09:00" } ] },
      { "label": "이번 주", "items": [ { "notificationId": 290, "category": "NEWS", "type": "EXTRA_INCOME", "title": "120,000원이 들어왔어요", "body": "정기 입금이 아니라서 추가 입금 블록에 따로 모아뒀어요", "read": true, "createdAt": "..." } ] }
    ],
    "retentionDays": 30,
    "hasNext": false
  }
}
```
`type`: `CONTEXT_QUESTION` · `DAILY_GUIDE` · `FIXED_SHORTAGE` · `BURN_RATE` · `MONTH_END_SHORTAGE` · `GOAL_BEHIND` · `OVER_ALLOCATED` · `SAVING_CONFIRMED` · `GOAL_ACHIEVED` · `GOAL_EXPIRED` · `REGULAR_INCOME` · `EXTRA_INCOME` · `REPORT`
비고: 30일이 지난 알림은 자동 삭제.

### 이상 징후 상세 `GET /notifications/{notificationId}` (13-1 생활비 소진 속도)
```json
{
  "result": {
    "notificationId": 301, "type": "BURN_RATE", "urgent": false,
    "block": { "blockId": 1, "name": "생활비", "balance": 950000, "remainingDays": 18 },
    "headline": "평소보다 17% 빨리 줄고 있어요",
    "recent7Days": { "dailyAvg": 68000, "usualDailyAvg": 58000, "series": [ { "date": "2026-10-08", "amount": 52000, "aboveUsual": false }, { "date": "2026-10-14", "amount": 91000, "aboveUsual": true } ] },
    "depletion": { "date": "2026-10-28", "shortDays": 3 },
    "safeDaily": 52700,
    "topIncreases": [ { "label": "배달·외식", "summary": "4건 · 밤 10시 이후 3건", "extraAmount": 62000 }, { "label": "카페", "summary": "6건", "extraAmount": 18000 } ],
    "advice": "이번 주에 배달을 2번만 줄이면 다시 안전으로 돌아와요. 야근 식대라면 알려주세요, 절약 후보에서 뺄게요.",
    "actions": ["FILL_FROM_OTHER", "CREATE_SAVING_GOAL"],
    "calculatedAt": "2026-10-14T09:00:00+09:00"
  }
}
```
고정지출 부족(`FIXED_SHORTAGE`)이면 `suggestedTransfer: { fromBlockId, toBlockId, amount }` → `/blocks/simulate` 호출. 자동 배분하지 않는다.

### 알림 설정 조회·수정 `GET / PUT /notifications/settings` (14)
```json
{
  "pushEnabled": true,
  "contextQuestion": { "enabled": true, "frequency": "MAX_3_PER_DAY" },
  "quietHours": { "start": "22:00", "end": "09:00" },
  "blockWarnings": { "BURN_RATE": true, "FIXED_SHORTAGE": true, "GOAL_BEHIND": true },
  "news": { "SAVING_AND_REPORT": true, "MARKETING": false },
  "dailyGuideTime": "08:00",
  "lockScreenHideAmount": false,
  "urgentOffConfirmed": false
}
```
- `frequency`: `EACH_SYNC`(가져올 때마다) · `MAX_3_PER_DAY`(하루 3번까지) · `DAILY_DIGEST`(하루 1번 저녁 9시에 모아서)
- `news.MARKETING`은 동의 항목 `MARKETING`과 연동(동의 안 했으면 켤 수 없음)
- `quietHours` 기본값 **[TBD: 시안 9~22시 vs 기능명세서 7~23시]**
에러: `NOTIFICATION_URGENT_OFF_CONFIRM_REQUIRED`, `NOTIFICATION_MARKETING_CONSENT_REQUIRED`

### 푸시 토큰 등록 `POST /push-tokens`
v3와 같다.

---

## 10. AI 내부 API (Spring → FastAPI, FE 비공개) · 김지현

v3와 같다. 아래 하나 추가.

| API명 | 호출 시점 | Method | Endpoint (AI 서버) | 처리 위치 |
| --- | --- | --- | --- | --- |
| 소비 분석 설명 문장 | 소비 분석 | POST | `/internal/ai/spending-comment` | 자체 sLM (`explanation`, `savingPotential.text`) |

ImpactList(`/blocks/simulate`)와 제안 카드(`SuggestionCard`) 문장은 **문장 틀에 숫자를 채워 서버가 만든다. AI 호출 없음.**

---

## 11. P1 엔드포인트 (요약)

| 기능 | API명 | Method | Endpoint |
| --- | --- | --- | --- |
| F-08 | 목표 지키기 대안 (16) | GET | `/goals/{blockId}/alternatives` |
| F-11 | 블록 상태 변경 이력 | GET | `/blocks/{blockId}/status-logs` |
| F-12·F-14 | 월간 리포트 (08) | GET | `/reports/monthly?month=2026-09` |
| F-16 | 에이전트 대화 (07) | POST | `/coach/sessions`, `/coach/sessions/{id}/messages`, `/coach/proposals/{id}/accept` |
| F-23 | 플랜 비교 / 구독 검증 (18) | GET / POST | `/plans`, `/subscriptions/verify` |
| F-24 | 블록 연계 상품 비교 (19) | GET | `/blocks/{blockId}/products?type=SAVINGS\|PARKING` |

F-16 대화 화면(07)의 제안 카드는 `SuggestionCard`, 미리보기는 `/blocks/simulate`를 그대로 쓴다.

---

## 12. 데이터 모델 변경 (ERD v3 → v4)

| 테이블 | 변경 |
| --- | --- |
| `consents` | `item` 값: `TERMS` · `PRIVACY` · `CODEF` · `AGE_14` · `MARKETING` |
| `user_settings` | `link_skipped` 삭제, `tutorial_done`·`blocks_confirmed` 추가 |
| `blocks` | `color` 추가. `rule` JSON에 사용형 `monthlyAmount`·`startDay`, 고정지출형 `matchCondition.amountMode`·`dayWindow`·`linkedAccountId`, `alerts` 추가 |
| `block_transfers` | `from_waiting_fund_id` · `to_waiting_fund_id` 추가(NULL 허용), `reason`에 `ALLOCATION` · `SUGGESTION` 추가 |
| **`waiting_funds`** (신규) | waiting_fund_id · user_id · source(SAVING/REGULAR_INCOME/EXTRA_INCOME/OTHER) · label · description · amount · original_amount · source_transaction_id NULL · saving_goal_id NULL · income_source_id NULL · breakdown JSON NULL · cleared_at NULL · created_at · updated_at |
| **`income_sources`** (신규) | income_source_id · user_id · payer_masked · label(월급·용돈 등) · is_regular · expected_day · avg_amount · min_amount · occurrence_count · last_received_at · next_expected_date · auto_allocate · auto_allocation JSON NULL · created_at · updated_at |
| **`scheduled_transfers`** (신규) | scheduled_transfer_id · user_id · from_block_id · to_block_id · amount · trigger(NEXT_REGULAR_INCOME) · status(PENDING/DONE/CANCELED/PARTIAL) · origin_transfer_id · executed_at NULL · created_at |
| `notification_settings` | 질문 빈도(`context_frequency`), 블록 경고 종류별 토글로 컬럼 정리 (구조는 김지현 결정) |

불변식: **블록 잔액 합 + 대기 자금 합 = 연동 계좌 실제 잔액**.

---

## 13. 남은 결정 사항 [TBD]

| 항목 | 영향 API |
| --- | --- |
| 오늘 쓸 수 있는 돈 불규칙 수입 로직 (`dailyGuide.basis: NEXT_INCOME`) 계산식 | 5장, 6장 사용형 상세 |
| 입금원 분석 기준 (몇 회 반복이면 정기 입금으로 볼지, 금액 편차) | 6-1장, 4장 `nextExpectedIncome` |
| 소셜 로그인 방식 (리다이렉트 vs 앱 SDK) | 1장 |
| 닉네임 필요 여부 | 2장 |
| connectedId 단위 테이블 분리 | 3장 |
| 절약 후보 저장 테이블 | 8장 `DISMISSED` 저장 위치 |
| 남은 개월 계산 방식 (달력 월 / 일수) | 6장 목표형, 7장 |
| 연동 이전 3개월 거래를 블록에서 차감하지 않는 규칙 | 3·4장 |
| 알림 받는 시간 기본값, 프리미엄 유지, 금융관리 점수 | 9장, 11장 (팀 결정) |
