# Orbit - Spatial Personal Calendar (공간형 오프라인 캘린더)

Orbit은 서버가 없는 환경에서도 여러 개인 기기 간에 일정을 안전하게 동기화하고, 오프라인 상에서 나침반과 지도 정보를 이용해 일정을 찾아갈 수 있도록 고안된 **Kotlin Multiplatform (KMP)** 기반의 공간형 오프라인 개인 캘린더 애플리케이션입니다.

---

## 주요 특징 및 기술 스택

### 1. 플랫폼 및 프레임워크 (KMP & Compose Multiplatform)
- **Kotlin Multiplatform**: 단일 코드베이스로 Android, Linux, Windows 데스크톱 환경을 네이티브 빌드합니다.
- **Compose Multiplatform**: HSL 기반의 세련된 다크 테마(Sleek Slate Dark Theme)를 제공하여 모든 운영체제에서 미려하고 일관된 사용자 UI 환경을 보장합니다.

### 2. 오프라인 로컬 데이터베이스 (SQLDelight)
- **SQLDelight DB**: 각 플랫폼별 네이티브 SQLite 드라이버를 통해 초고속 오프라인 트랜잭션을 처리합니다.
  - **Android**: `AndroidSqliteDriver` (애플리케이션 Context 연동)
  - **Desktop (Linux/Windows)**: `JdbcSqliteDriver` (사용자 홈 디렉토리 내 `~/.orbit/orbit.db` 파일 자동 생성)
- **구조**: 일정 데이터의 세부 구조(참여 인원, 위치, 반복 규칙)는 가독성과 스키마 유연성을 위해 데이터베이스 내부에 JSON 텍스트 형식으로 직렬화되어 저장됩니다.

### 3. P2P 분산 동기화 엔진 (Vector Clock & Delta Sync)
- **벡터 클락 (Vector Clocks)**: 중계 노드나 중앙 서버 없이 기기 간 충돌을 식별하기 위해 논리적 벡터 카운터를 유지합니다.
- **증분/델타 동기화 (Delta Sync)**: 두 기기가 오프라인 상에서 만나면 서로의 벡터 클락을 교환하여 상대방 기기에 없는 변경 로그(Changelog)만 추출해 고속으로 전송합니다.
- **LWW (Last-Write-Wins) 충돌 해결**: 기기 간 동일 데이터 수정 시 물리 시간 기준 타임스탬프를 비교하여 최종 승자를 판정합니다.
- **대용량(1GB) 파일 최적화**: 대용량 오프라인 지도 타일 및 미디어 첨부파일은 가벼운 SQLite 데이터베이스 파일과 격리하여 별도 디렉토리에 관리하고 고속 통신 링크를 통해 독립적으로 직렬 전송합니다.

### 4. 수학적 일정 계산 및 경로 내비게이션 엔진
- **역연산 스케줄링**: 특정 시간대를 입력받아 해당 시각이 반복 스케줄(일, 월, 분기, 반기, 년 단위) 내에 실제로 유효한 인스턴스에 해당하는지 $O(1)$ 복잡도로 수식 기반 계산을 수행합니다. (불필요한 반복 루프 연산 배제)
- **나침반 방위각(Bearing) 수학**: 하버사인 공식(Haversine Formula)을 적용하여 구면 좌표계 상에서 두 지점 간 최단거리(Km)와 초기 방위각을 직접 계산하여 물리 나침반 센서가 없더라도 방향 지침 카디널 포인트(N/S/E/W)를 바늘 방향으로 정밀 지시합니다.

### 5. 관리용 AI 연동 소켓 (Local Loopback WebSocket)
- **Ktor WebSocket Server**: 로컬호스트 주소인 `127.0.0.1:9090`에 바인딩되어 동작하며, 로컬에서 실행 중인 AI가 애플리케이션의 핵심 기능을 직접 제어할 수 있도록 돕습니다.
- **JSON-RPC 2.0 표준**: AI 에이전트와 통신 시 다음 표준 메서드를 지원합니다:
  - `getSchedules`: 활성화된 일정 정보를 목록으로 조회
  - `upsertSchedule`: 일정을 생성하거나 업데이트
  - `deleteSchedule`: 지정한 일정을 소프트 삭제 처리
  - `getTravelEstimate`: 출발지와 목적지 간 오프라인 이동 예상 시간(ETA) 및 거리 반환

---

## 프로젝트 디렉토리 구조

```text
Orbit/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/              # 공통 비즈니스 로직 및 UI 코드
│   │   │   ├── kotlin/com/orbit/app/
│   │   │   │   ├── engine/          # 역연산 반복 계산, 하버사인 나침반 수학
│   │   │   │   ├── model/           # 일정, 참여자, 지리 정보 모델 정의
│   │   │   │   ├── socket/          # Ktor Netty 기반 Local AI 소켓 서버
│   │   │   │   ├── sync/            # 벡터 클락 계산 및 델타 동기화 엔진
│   │   │   │   └── ui/              # Compose Multiplatform 다크 테마 대시보드
│   │   │   └── sqldelight/          # SQLDelight 데이터베이스 스키마 및 쿼리 파일
│   │   ├── androidMain/             # Android 네이티브 드라이버 구성 및 매니페스트
│   │   └── desktopMain/             # 데스크톱(Linux/Windows) 네이티브 메인 윈도우 진입점
│   └── build.gradle.kts             # Compose Multiplatform 타겟 빌드 설정
├── gradle/                          # Gradle Wrapper 설정 정보
├── build.gradle.kts                 # 프로젝트 루트 빌드 스크립트
└── settings.gradle.kts              # 빌드에 포함할 모듈 선언
```

---

## 빌드 및 실행 방법

### 사전 요구 사항
- **JDK 17** 이상이 시스템에 설치 및 환경 변수로 잡혀있어야 합니다.
- Gradle 빌드 도구는 포함된 `gradlew` 스크립트를 통해 자동으로 다운로드 및 캐싱됩니다.

### 1. 테스트 실행 (Common 단위 테스트)
공통 도메인 및 동기화/역연산 로직의 이상 유무를 판별하는 테스트 세트를 컴파일 및 실행합니다.
```bash
./gradlew test
```

### 2. 데스크톱(Desktop - Linux / Windows) 빌드 및 실행
데스크톱 타겟 애플리케이션을 빌드하여 다이렉트 실행합니다.
```bash
# 데스크톱용 로컬 실시간 실행
./gradlew run

# 데스크톱 배포용 독립 실행 파일(JAR) 빌드
./gradlew packageDistributionForCurrentOS
```
*주의: 빌드 완료 후 데스크톱 실행 파일 및 리소스 아티팩트는 `composeApp/build/compose/binaries/` 경로에 패키징됩니다.*

### 3. 안드로이드(Android) 빌드 및 설치
에뮬레이터 또는 USB 연결 기기에 디버그 APK를 배포합니다.
```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 연결된 디바이스에 앱 즉시 설치 및 실행
./gradlew installDebug
```

---

## AI 소켓 서버 연동 예시
AI 에이전트가 로컬에서 실행 시 다음과 같이 WebSocket 클라이언트를 이용하여 Orbit 앱을 조작할 수 있습니다.

- **연동 주소**: `ws://127.0.0.1:9090/control`
- **전송 메시지 샘플 (JSON-RPC 2.0)**:
  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "getTravelEstimate",
    "params": {
      "start": { "name": "Seoul City Hall", "latitude": 37.5665, "longitude": 126.9780 },
      "end": { "name": "Incheon Airport", "latitude": 37.4602, "longitude": 126.4407 }
    }
  }
  ```
- **응답 메시지 샘플**:
  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
      "distanceKm": 49.12456482121345,
      "estimatedDurationMinutes": 58.94947778545614
    }
  }
  ```
