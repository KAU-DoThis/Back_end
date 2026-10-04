# FINTRIS Backend

> 잔액을 하나의 숫자가 아니라 목적별 **블록**으로 나눠, 배우지 않고도 자금 상태를 보고 느껴 행동하게 만드는 개인 금융 관리 서비스 **FINTRIS**의 API 서버입니다.

한국항공대학교 소프트웨어학과 · 팀 **이거해조 (KAU-DoThis)**

<br>

## 🛠 Tech Stack

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot, Spring Data JPA, Spring Security (JWT) |
| Database | MySQL (AWS RDS) |
| Build | Gradle |
| 외부 연동 | CODEF API (금융 데이터), FastAPI AI 서버 |

<br>

## 👥 Backend Team

| 이름 | 역할 |
| --- | --- |
| 김지현 | Backend, PM |
| 이건하 | Backend |

<br>

## 🧭 설계 원칙

- 금액·합계·달성률·상태 판정은 **Spring이 계산**하고, AI는 계산하지 않는다.
- **CODEF 호출과 DB 접근은 Spring만** 한다. AI 서버(FastAPI)는 Spring이 넘겨준 데이터만 받는다.
- 외부 LLM에는 **집계된 금액·카테고리·블록 이름만** 보낸다. 원본 거래내역은 보내지 않는다.
- 블록은 **가상 배분**이며 실제 이체는 하지 않는다.
- 금액은 **원 단위 정수**로 다룬다. (Java `long`, DB `BIGINT`)
- CODEF `connectedId`는 **암호화해서 저장**하고 API 응답에 노출하지 않는다.

<br>

## 🚀 Getting Started

### 요구 사항

- Java 21
- MySQL 8.x

### 실행

```bash
git clone https://github.com/KAU-DoThis/Back_end
cd Back_end
./gradlew bootRun
```

### 환경 변수

민감 정보는 저장소에 올리지 않습니다. 로컬 실행 시 아래 값을 환경 변수 또는 `application-local.yml`(git 제외)에 설정하세요.

| 변수 | 설명 |
| --- | --- |
| `DB_URL` | MySQL 접속 URL |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 |
| `CODEF_CLIENT_ID` | CODEF 클라이언트 ID |
| `CODEF_CLIENT_SECRET` | CODEF 클라이언트 시크릿 |
| `AI_SERVER_URL` | FastAPI AI 서버 주소 |

<br>

## 🗂 Folder Structure

```
src/
├── common/
│   ├── exception/    전역 예외 처리, 에러 코드
│   ├── security/     Spring Security, JWT 필터
│   ├── response/     공통 응답 형식
│   └── config/       설정
├── auth/             로그인, JWT 발급·검증
├── user/             회원, 온보딩 정보
├── connection/       금융기관 연결, 연결 상태, connectedId
├── transaction/      거래 수집·정규화·분류, 소비 통계·패턴 분석, 조정 가능 지출 후보 산출
├── block/            블록 생성·조회, 목적·규칙, 자금 배분, 블록 간 금액 이동
├── goal/             목표 금액·기간, 월 필요 금액·부족 금액, 달성률 계산
└── suggestion/       AI 서버 호출, 절약 행동 제안·설명, 수락·수정·제외
```

<br>

## 🌐 API Convention

- 경로 접두사: `/api/v1`
- RESTful 규칙을 따른다.
- 인증: `Authorization: Bearer {accessToken}`
- 날짜·시간: ISO-8601, `Asia/Seoul` (`2026-10-04T19:40:00+09:00`)

```
GET    /api/v1/{domain}
POST   /api/v1/{domain}
GET    /api/v1/{domain}/{id}
PATCH  /api/v1/{domain}/{id}
DELETE /api/v1/{domain}/{id}
```

### 응답 형식

**성공**

```json
{
  "statusCode": 200,
  "timestamp": "2026-10-04T19:40:00+09:00",
  "path": "/api/v1/blocks/summary",
  "message": "요청이 성공했습니다.",
  "data": { },
  "error": null
}
```

**실패**

```json
{
  "statusCode": 400,
  "timestamp": "2026-10-04T19:40:00+09:00",
  "path": "/api/v1/blocks/transfers",
  "message": "이동할 금액이 블록 잔액보다 큽니다.",
  "data": null,
  "error": "BLOCK_INSUFFICIENT_AMOUNT"
}
```

에러 코드는 `도메인_원인` 형식으로 작성한다. (예: `GOAL_NOT_FOUND`, `AUTH_INVALID_TOKEN`)

<br>

---

# 📌 Convention

## 기본 원칙

- 모든 작업은 **브랜치 기반으로 진행**하며, `main` 브랜치에 직접 push 하지 않는다.
- 작업 시작 전 **최신 `dev` 브랜치를 pull** 한다.
- 기능 단위로 작업을 나누고 **PR을 통해 코드 리뷰 후 병합**한다.
- 기술적 의견이 다를 경우 **근거 기반으로 논의 후 다수결로 결정**한다.

<br>

## 🌿 Branch Strategy

```
main
dev        ← default
feature/*
refactor/*
fix/*
```

| 브랜치 | 설명 |
| --- | --- |
| `main` | 배포 가능한 안정 버전 유지, 직접 push 금지 |
| `dev` | 개발 통합 브랜치, feature 브랜치 병합 대상 |
| `feature/*` | 새로운 기능 개발 |
| `refactor/*` | 기능 변화 없는 코드 개선 |
| `fix/*` | 버그 수정 |

### 브랜치 네이밍

```
feature/{domainName}-{detail}
refactor/{domainName}-{detail}
fix/{domainName}-{detail}
```

예시: `feature/block-transfer`, `fix/auth-refresh-token`

<br>

## 🔄 Workflow

**1. 최신 `dev` 받기** `현재 브랜치: dev`

```bash
git checkout dev
git pull origin dev
```

**2. 작업 브랜치 생성** `현재 브랜치: dev`

```bash
git checkout -b feature/{domainName}-{detail}
```

**3. 작업 후 커밋** `현재 브랜치: feature/*`

```bash
git add .
git commit -m "type: 작업 내용"
```

**4. 원격 브랜치에 push** `현재 브랜치: feature/*`

```bash
git push -u origin feature/{domainName}-{detail}
```

**5. GitHub에서 `dev` ← `feature/*` PR 생성 → 리뷰 후 merge**

**6. 다음 작업은 1번부터 반복**

> 작업 내역은 원격 `dev` 브랜치에 누적됩니다.
> 배포 시 `main` ← `dev` PR을 생성해 `main`에 merge 합니다.

<br>

## 📝 Commit Convention

```
type: 작업 내용
```

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 (코드 변경 없음) |
| `style` | 코드 포맷팅 등 스타일 변경 (논리 변경 없음) |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 의존성, 설정 등 기타 작업 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 혹은 폴더명을 수정하거나 옮기는 작업만 한 경우 |
| `remove` | 파일을 삭제하는 작업만 한 경우 |
| `!HOTFIX` | 급하게 치명적인 버그를 고쳐야 하는 경우 |

예시: `feat: 블록 간 금액 이동 API 구현`

<br>

## 🔀 Pull Request

### PR 생성 기준

- 기능 단위 작업 완료 시 PR 생성
- `dev` 브랜치 기준으로 PR 생성

### PR 제목

```
[타입] #이슈번호 제목
```

예시: `[feat] #10 블록 생성 API 구현`

### PR 본문

```markdown
## 📌 관련 이슈
- close #이슈번호

## ✨ 작업 내용
- Goal 엔티티와 Block 엔티티 간 1:1 관계 설정
- 목표 생성 시 목표형 블록을 함께 생성하는 로직 추가
- 요청 DTO에 Bean Validation 적용

## 📸 스크린샷 / 테스트 결과
- `POST /api/v1/goals` 성공 응답 확인 완료 (Postman/Swagger 캡처 첨부)

## 🔍 리뷰 포인트
- 목표와 블록 생성이 하나의 트랜잭션으로 묶여 있는지 확인 부탁드립니다.

## ✅ 체크리스트
- [ ] 커밋 메시지 컨벤션을 준수했는가?
- [ ] 로컬에서 빌드 및 테스트가 성공했는가?
- [ ] 불필요한 주석이나 로그를 제거했는가?
- [ ] 민감 정보(키, 비밀번호)가 커밋에 포함되지 않았는가?
```

### PR 리뷰 규칙

- 최소 **1명 이상 리뷰 후 merge**
- 리뷰 코멘트 반영 후 merge 진행

<br>

## 🎨 Code Style

- 들여쓰기 4칸
- 클래스는 `PascalCase`, 메서드·변수는 `camelCase`, 상수는 `UPPER_SNAKE_CASE`
- DB 테이블·컬럼은 `snake_case`, 테이블명은 복수형 (`users`, `blocks`)
- Enum은 DB에 문자열로 저장 (`@Enumerated(EnumType.STRING)`)
- 금액 필드는 `long` 타입 사용
