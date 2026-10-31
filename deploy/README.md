# 개발 서버 배포 (CI/CD)

`main`에 머지하면 개발 서버(EC2 `fintris-dev`)에 자동으로 배포된다. 아키텍처 "그림 1. 개발 단계" 기준이다.

```
main 머지 ─> GitHub Actions ─> 이미지 빌드 ─> ECR(fintris-backend) ─> SSM Run Command ─> EC2에서 deploy.sh
                                                                                           └ 응답 없으면 이전 버전으로 되돌림
```

| 파일 | 역할 |
| --- | --- |
| `Dockerfile` (레포 루트) | Spring 서버 이미지 만드는 법 |
| `deploy/docker-compose.yml` | 서버에서 main·mysql·caddy를 함께 띄우는 설정 |
| `deploy/Caddyfile` | HTTPS 입구 (도메인이 생기면 사용) |
| `deploy/deploy.sh` | 서버에서 실행되는 배포 스크립트 |
| `.github/workflows/deploy.yml` | main 머지 시 배포 |
| `.github/workflows/build-check.yml` | PR마다 이미지가 빌드되는지만 확인 |

- 계정 ID `<ACCOUNT_ID>`(실제 값은 팀 노션 참고), 리전 서울 `ap-northeast-2`, 인스턴스 `fintris-dev`
- 개발 서버는 **main 기준**으로 배포한다. dev에 머지한 기능은 dev → main PR이 머지돼야 서버에 올라간다.

---

## 1. AWS 최초 설정 (관리자 1회)

### 1-1. ECR 저장소
ECR → 프라이빗 리포지토리 → 생성
- 이름: `fintris-backend`
- 태그 변경 불가능성: **Immutable**
- 생성 후 수명 주기 정책: "이미지 개수 > 10이면 만료" 규칙 1개 (저장 비용 관리)

### 1-2. GitHub OIDC 공급자
IAM → 자격 증명 공급자 → 공급자 추가
- 유형: OpenID Connect
- 공급자 URL: `https://token.actions.githubusercontent.com`
- 대상: `sts.amazonaws.com`

### 1-3. GitHub Actions용 역할 `fintris-github-deploy`
IAM → 역할 → 역할 생성 → 웹 자격 증명 → 위 공급자 선택. 만든 뒤 **신뢰 정책**을 아래로 바꾼다. (main 브랜치에서 실행된 워크플로만 이 역할을 쓸 수 있다)

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:KAU-DoThis/Back_end:ref:refs/heads/main"
      }
    }
  }]
}
```

인라인 정책 (`<INSTANCE_ID>`는 fintris-dev 인스턴스 ID):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Sid": "EcrLogin", "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    {
      "Sid": "EcrPush", "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer", "ecr:DescribeImages"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:<ACCOUNT_ID>:repository/fintris-backend"
    },
    {
      "Sid": "RunDeployOnInstance", "Effect": "Allow", "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:<ACCOUNT_ID>:instance/<INSTANCE_ID>",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    { "Sid": "ReadDeployResult", "Effect": "Allow", "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"], "Resource": "*" },
    { "Sid": "CheckInstanceState", "Effect": "Allow", "Action": "ec2:DescribeInstances", "Resource": "*" }
  ]
}
```

### 1-4. EC2 인스턴스 역할
fintris-dev에 붙은 IAM 역할에 관리형 정책 두 개가 있어야 한다.
- `AmazonSSMManagedInstanceCore` (Session Manager 접속, Run Command 수신)
- `AmazonEC2ContainerRegistryReadOnly` (ECR에서 이미지 받기)

인스턴스 메타데이터는 **IMDSv2 필수, 홉 제한 1** (컨테이너가 EC2 권한을 빌려 쓰지 못하게).
보안 그룹 인바운드는 **443만**. 22(SSH)는 열지 않는다.

---

## 2. 서버 최초 설정 (1회)

fintris-dev는 **Ubuntu 24.04**이고 Docker·Compose·스왑·AWS CLI가 이미 설치돼 있다. **`.env`만 만들면 된다.**

Session Manager로 fintris-dev에 접속해서 실행한다.

```bash
# 설치 확인 (버전과 스왑이 나오면 OK)
docker compose version && aws --version && swapon --show

# 설정 파일
sudo mkdir -p /opt/fintris
sudo touch /opt/fintris/.env && sudo chmod 600 /opt/fintris/.env
sudo nano /opt/fintris/.env      # 아래 키를 붙여 넣고 빈 값 채우기
```

`/opt/fintris/.env`에 들어갈 키 (실제 값은 git에 올리지 않는다)

```bash
# ---------- 배포 ----------
AWS_REGION=ap-northeast-2
ECR_REGISTRY=<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com
ECR_REPOSITORY=fintris-backend
# 도메인이 생기면 넣는다. 비워두면 Caddy(HTTPS)를 띄우지 않는다.
SITE_ADDRESS=

# ---------- DB (MySQL 컨테이너 + main이 같이 사용) ----------
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=fintris
DB_USERNAME=fintris
DB_PASSWORD=
# 빈 DB에서 처음 띄울 때는 update여야 테이블이 만들어진다.
JPA_DDL_AUTO=update

# ---------- 앱 ----------
# 32자 이상의 임의 문자열 (예: openssl rand -base64 48)
JWT_SECRET=
CODEF_BASE_URL=https://development.codef.io
CODEF_CLIENT_ID=
CODEF_CLIENT_SECRET=
CODEF_PUBLIC_KEY=
# connectedId 암호화 키. Base64로 된 32바이트 (비어 있으면 앱이 시작되지 않는다)
CODEF_CONNECTED_ID_KEY=
```

`JWT_SECRET`, DB 비밀번호는 `openssl rand -base64 48`, `CODEF_CONNECTED_ID_KEY`는 `openssl rand -base64 32`로 만든다. 이 값들은 디스코드·카톡에 올리지 않는다.
`CODEF_CONNECTED_ID_KEY`는 한 번 정하면 바꾸지 않는다. 바꾸면 이미 저장된 connectedId를 풀 수 없다.

---

## 3. GitHub 설정 (1회)

레포 Settings → Secrets and variables → Actions → **Variables** 탭 → New repository variable

| 이름 | 값 |
| --- | --- |
| `AWS_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/fintris-github-deploy` |
| `EC2_INSTANCE_ID` | fintris-dev 인스턴스 ID (`i-`로 시작) |

AWS 키(Access Key)는 저장하지 않는다. OIDC로 매번 임시 자격증명을 받는다.

---

## 4. 배포하기

1. **서버를 켠다.** 꺼져 있으면 워크플로가 "서버가 꺼져 있습니다"로 실패한다.
2. dev → main PR을 머지한다. (또는 Actions → Deploy to EC2 → Run workflow)
3. Actions 탭에서 초록 체크를 확인한다. 실패하면 "EC2에 배포" 단계의 서버 출력을 본다.
4. 다 쓰면 **서버를 중지한다.** 켜져 있는 시간만큼 요금이 나간다.

### 도메인 없이 Swagger 보기 (SSM 포트 포워딩)
내 PC에 AWS CLI와 Session Manager 플러그인이 있어야 한다.

```bash
aws ssm start-session --target <INSTANCE_ID> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8080"],"localPortNumber":["8080"]}'
```
켜둔 채로 브라우저에서 http://localhost:8080/swagger-ui.html

### 도메인이 생기면
1. 도메인 A 레코드 → 탄력적 IP
2. 서버 `.env`에 `SITE_ADDRESS=api.도메인` 추가
3. 다시 배포하면 Caddy가 HTTPS 인증서를 받아 443으로 연다.

---

## 5. 문제가 생기면

```bash
cd /opt/fintris
sudo docker compose ps                 # 컨테이너 상태
sudo docker compose logs --tail 100 main
cat .current_tag                        # 지금 떠 있는 커밋
sudo bash deploy.sh <이전 커밋 SHA>      # 손으로 되돌리기
```

| 증상 | 원인 |
| --- | --- |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | main이 아닌 브랜치에서 실행했거나, 신뢰 정책의 레포 이름이 다름 |
| `서버가 꺼져 있습니다` | 콘솔에서 서버 시작 후 Re-run |
| SSM 상태 `Undeliverable`/`Failed` 직후 | 인스턴스 역할에 `AmazonSSMManagedInstanceCore`가 없거나 서버가 막 켜짐 |
| `.env 가 없습니다` | 2번 서버 최초 설정을 안 함 |
| 서버 응답 없음 → 롤백 | `docker compose logs main`에서 DB 접속·환경변수 오류 확인. 빈 DB면 `JPA_DDL_AUTO=update` |

DB 백업은 EBS 스냅샷(Data Lifecycle Manager, 매일)으로 한다. `docker compose down -v`는 DB 볼륨까지 지우므로 쓰지 않는다.
