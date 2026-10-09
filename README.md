# 🍞 빵생빵사 (Born to Bread) — Backend

> 2025 SEASONTHON TEAM 43 · 백엔드 저장소
> 내 주변 빵집을 찾고, 검색 기록을 바탕으로 취향에 맞는 빵집을 추천받고, 나만의 **빵지순례 코스**를 만들어 공유하는 서비스입니다.

프론트엔드와 백엔드를 별도 저장소로 나누어 개발했으며, 이 저장소는 **API 서버(Spring Boot)** 와 **추천 서버(FastAPI)**, **DB(PostgreSQL)** 를 포함합니다.

---

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 카카오 로그인 | 카카오 OAuth 인가 코드 → 카카오 Access Token 발급, 사용자 정보 조회 후 회원 저장 |
| 주변 빵집 조회 | 현재 위치(위도/경도) + 반경으로 카카오 로컬 API에서 빵집 목록 조회, 중복 제거 및 거리순 정렬 |
| 빵집 키워드 검색 | 카카오 로컬 키워드 검색 + **검색 이력 저장** (추천에 사용) |
| 빵집 추천 | 사용자의 최근 검색어 상위 5개를 키워드로 FastAPI 추천 서버 호출 → 반경 2km 내 빵집을 TF-IDF 유사도 + 거리순으로 Top 10 추천 |
| 빵지순례 코스 | 코스 생성/수정, 빵집(스팟) 추가, 공개 코스 목록, 내 코스 목록, 상세 조회, 조회수 증가 |

## 아키텍처

```
 ┌──────────────┐        ┌────────────────────────┐        ┌──────────────────────┐
 │  Frontend    │  HTTP  │  Spring Boot (:8080)   │  HTTP  │  FastAPI (:8000)     │
 │ (별도 저장소) │ ─────▶ │  crumb-spring-boot     │ ─────▶ │  crumb-fastapi       │
 └──────────────┘        │  - 로그인/검색/코스     │        │  - /recommend        │
                         │  - Swagger UI          │        │  - bakeries_clean.csv│
                         └──────────┬─────────────┘        └──────────┬───────────┘
                                    │ JPA                              │ 지오코딩(전처리)
                          ┌─────────▼──────────┐              ┌────────▼─────────┐
                          │ PostgreSQL (:5432) │              │ Kakao Local API  │
                          │ crumb-postgres     │              └──────────────────┘
                          └────────────────────┘
```

## 기술 스택

- **API 서버**: Java 17, Spring Boot 3.5.5 (Web, WebFlux `WebClient`, Data JPA, Validation, Thymeleaf), springdoc-openapi 2.7, jjwt 0.11.5, spring-dotenv
- **추천 서버**: Python 3.11, FastAPI, pandas, scikit-learn (char n-gram TF-IDF), httpx
- **DB**: PostgreSQL 16
- **인프라**: Docker, Docker Compose
- **외부 API**: Kakao Login, Kakao Local(키워드/주소 검색), Google Places(선택)

## 디렉터리 구조

```
.
├── be/                              # Spring Boot API 서버
│   ├── Dockerfile
│   ├── compose.yaml                 # Spring + Postgres + FastAPI 통합 실행
│   ├── .env.example                 # 환경변수 템플릿 (→ be/.env 로 복사)
│   └── src/main/java/org/crumb/be/
│       ├── client/                  # Kakao Local API 클라이언트
│       ├── common/                  # 공통 응답(ApiResponse), 예외 처리
│       ├── config/                  # CORS, Swagger, WebClient, RestTemplate
│       ├── course/                  # 빵지순례 코스
│       ├── recommend/               # 검색(이력 저장) + 추천(FastAPI 연동)
│       ├── search/                  # 주변 빵집 조회
│       └── user/                    # 카카오 로그인, 회원, JWT
└── recommend/                       # FastAPI 추천 서버
    ├── app.py                       # /health, /reload, /recommend
    ├── preprocess.py                # 원본 CSV 정제 + 카카오 지오코딩
    ├── entrypoint.sh                # 전처리 후 uvicorn 실행
    ├── data/
    │   ├── bakeries_raw.csv         # 원본 빵집 데이터 (대전 지역)
    │   └── bakeries_clean.csv       # 정제 + 좌표 포함 데이터 (추천에 사용)
    └── utils/
```

## API 요약

전체 명세는 서버 실행 후 **Swagger UI** `http://localhost:8080/swagger-ui/index.html` 에서 확인할 수 있습니다.
모든 응답(코스/검색/추천)은 `{ "success": true, "code": "OK", "data": ... }` 형태입니다.

| Method | Path | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/` | - | 카카오 로그인 테스트 페이지 |
| GET | `/api/kakao/login?code=` | - | 인가 코드 → 카카오 Access Token |
| POST | `/api/kakao/login-bread` | `Authorization: Bearer {카카오 토큰}` | 회원 조회/가입 후 백엔드 JWT 발급 |
| POST | `/api/kakao/logout` | `Authorization: Bearer {카카오 토큰}` | 카카오 로그아웃 |
| GET | `/api/bakery/list?lat=&lng=&radius=&size=` | - | 주변 빵집 조회 |
| GET | `/api/search/bakeries?query=&lat=&lng=&radius=` | `Authorization: Bearer {카카오 토큰}` | 키워드 검색 + 검색 이력 저장 |
| GET | `/api/recommend/bakeries?lat=&lng=` | `Authorization: Bearer {카카오 토큰}` | 검색 이력 기반 추천 |
| POST | `/api/courses` | `X-User-Id` | 코스 생성 |
| PUT | `/api/courses/{courseId}` | `X-User-Id` | 코스 수정 |
| POST | `/api/courses/{courseId}/spots` | `X-User-Id` | 코스에 빵집 추가 |
| GET | `/api/courses/public` | `X-User-Id`(선택) | 공개 코스 목록 (내 코스 제외) |
| GET | `/api/courses/me` | `X-User-Id` | 내 코스 목록 |
| GET | `/api/courses/{courseId}` | `X-User-Id`(선택) | 코스 상세 |
| POST | `/api/courses/{courseId}/views` | `X-User-Id`(선택) | 조회수 증가 |

> ⚠️ 인증 방식 참고: 검색/추천 API는 **카카오 Access Token**을 그대로 받아 카카오에서 사용자 ID를 조회하고, 코스 API는 임시로 `X-User-Id` 헤더를 사용합니다. `/login-bread`에서 발급하는 백엔드 JWT는 아직 다른 API의 인증에 쓰이지 않습니다.

FastAPI 추천 서버(내부용):

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/health` | 상태 + 로드된 빵집 수 |
| POST | `/reload` | CSV 다시 로드 |
| GET | `/recommend?lat=&lng=&keywords=a,b&exclude=1,2` | 반경 내 추천 목록 |

---

## 실행 방법

### 0. 사전 준비

- Docker / Docker Compose v2
- (로컬 개발 시) JDK 17, Python 3.11
- [Kakao Developers](https://developers.kakao.com) 애플리케이션
  - **앱 키 > REST API 키** → `KAKAO_CLIENT_ID`, `KAKAO_REST_API_KEY`
  - **카카오 로그인 활성화**, **Redirect URI** 등록 → `KAKAO_REDIRECT_URI`
  - **카카오 로그인 > 보안 > Client Secret** 발급 → `KAKAO_CLIENT_SECRET`
  - **동의항목**: 닉네임, **카카오계정(이메일)** — 회원 식별에 이메일을 사용하므로 필수
  - **플랫폼 > Web** 에 프론트엔드/백엔드 도메인 등록

### 1. 환경변수 설정

```bash
cd be
cp .env.example .env   # 값 채우기
```

| 변수 | 예시 | 설명 |
| --- | --- | --- |
| `POSTGRES_HOST` | `crumb-postgres` | Compose 실행 시 서비스명, 로컬 실행 시 `localhost` |
| `POSTGRES_PORT` | `5432` | 호스트에 노출할 포트 (Spring은 항상 5432로 접속) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `crumb` | DB 이름/계정 |
| `SPRING_PORT` | `8080` | 호스트에 노출할 Spring 포트 |
| `KAKAO_CLIENT_ID` | REST API 키 | OAuth client_id |
| `KAKAO_CLIENT_SECRET` | | 카카오 Client Secret |
| `KAKAO_REDIRECT_URI` | `http://localhost:3000/oauth/kakao` | 카카오 콘솔에 등록한 값과 동일해야 함 |
| `KAKAO_REST_API_KEY` | REST API 키 | 카카오 로컬 검색, 지오코딩 |
| `GOOGLE_PLACES_API_KEY` | (선택) | 현재 추천 로직에서는 사용하지 않음 |
| `FASTAPI_HOST` | `http://crumb-fastapi` | **스킴 포함, 포트 제외** (`:8000`은 자동으로 붙음) |
| `FASTAPI_PORT` | `8000` | 호스트에 노출할 FastAPI 포트 |
| `RECO_RADIUS_M` / `RECO_LIMIT` | `2000` / `10` | 추천 반경(m) / 개수 |
| `RECO_INPUT` / `RECO_OUTPUT` | `/app/data/bakeries_raw.csv` / `/app/data/bakeries_clean.csv` | 전처리 입·출력 경로 |

> ⚠️ **전처리 주의**: FastAPI 컨테이너는 시작할 때마다 `RECO_INPUT` 파일이 있으면 카카오 API로 좌표를 다시 구해 `RECO_OUTPUT`을 **덮어씁니다**. `recommend/` 폴더가 볼륨으로 마운트되어 있어 저장소의 `bakeries_clean.csv`도 함께 바뀝니다. 카카오 키가 잘못되었으면 좌표를 못 구해 **빈 파일로 덮어쓰게 되므로**, 이미 정제된 CSV를 그대로 쓰려면 `RECO_INPUT=/app/data/__skip__.csv` 처럼 없는 경로를 지정하세요.

### 2. Docker Compose로 실행 (권장)

```bash
cd be
docker compose up -d --build
docker compose ps
```

| 확인 | 주소 |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| 카카오 로그인 테스트 페이지 | http://localhost:8080/ |
| 추천 서버 상태 | http://localhost:8000/health → `{"ok":true,"rows":183}` |
| 추천 직접 호출 | http://localhost:8000/recommend?lat=36.35&lng=127.38 |

```bash
docker compose logs -f crumb-spring-boot   # 로그 확인
docker compose down                         # 종료 (DB 데이터 유지)
docker compose down -v                      # 종료 + DB 볼륨 삭제
```

### 3. 로컬에서 직접 실행 (개발용)

```bash
# DB + 추천 서버만 컨테이너로
cd be
docker compose up -d crumb-postgres crumb-fastapi

# be/.env 에서 POSTGRES_HOST=localhost, FASTAPI_HOST=http://localhost 로 변경 후
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

- `.env`는 `spring-dotenv`가 `be/` 디렉터리에서 자동으로 읽습니다.
- `spring-boot-docker-compose` 의존성 때문에 `bootRun` 시 `compose.yaml`을 자동으로 띄우려 할 수 있습니다. Spring 컨테이너와 포트(8080)가 겹치면 `SPRING_DOCKER_COMPOSE_ENABLED=false`로 끄세요.
- 테스트(`contextLoads`)는 실제 DB 연결이 필요합니다. DB가 떠 있지 않으면 `./gradlew build -x test`로 빌드하세요.

---

## 배포 (단일 서버 / EC2 등)

1. 서버에 Docker, Docker Compose 설치
2. 저장소 clone 후 `be/.env` 작성 (운영 값으로)
   - `KAKAO_REDIRECT_URI`는 **운영 프론트 도메인**으로 바꾸고 카카오 콘솔에도 등록
3. `cd be && docker compose up -d --build`
4. 보안그룹/방화벽: **80/443(또는 8080)만 개방**. 5432, 8000은 외부에 열지 마세요. (compose가 호스트 포트로 노출하므로 필요하면 `ports`를 `127.0.0.1:5432:5432`처럼 바꾸세요.)
5. (권장) Nginx 리버스 프록시 + HTTPS(Let's Encrypt). `server.forward-headers-strategy: framework`가 설정되어 있어 프록시 뒤에서 Swagger 주소가 올바르게 잡힙니다.
6. 업데이트: `git pull && docker compose up -d --build`

운영 전 체크리스트:

- [ ] `CorsConfig`의 `allowedOrigins("*")`를 프론트 도메인으로 제한
- [ ] `application.yml`의 `ddl-auto: update` → `validate`, `show-sql`/`org.hibernate.SQL: DEBUG` 끄기
- [ ] `KakaoMapClient`의 API 키 콘솔 출력(`System.out.println`) 제거
- [ ] `JwtService`의 서명 키가 재시작마다 새로 생성됨 → 환경변수 기반 고정 키로 변경 필요
- [ ] 코스 API의 `X-User-Id` 임시 인증을 실제 토큰 인증으로 교체

## 팀

2025 SEASONTHON TEAM 43
