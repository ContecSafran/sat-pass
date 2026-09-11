# sat-pass

스마트폰 GPS 위치를 기준으로 관심 위성의 통과(pass) 스케줄을 계산해 보여 주는 안드로이드 앱.

궤도 계산과 TLE 수집 로직은 사내 **LCAM**(`add-lcam-api`) 서버와 동일한 방식을 따른다.

## 기능

| 화면 | 내용 |
| --- | --- |
| 스케줄 | 선택된 위성의 통과를 AOS 시간순으로 표시. 진행 중인 패스는 카드 안에 진행도가 채워진다. 카드를 누르면 스카이 플롯과 상세 정보가 열린다. 목록을 아래로 당기면 TLE 를 갱신한다. |
| 위성 | TLE 캐시에서 이름 / NORAD ID 로 검색해 관심 위성을 추가·삭제. 스위치로 스케줄에 포함할 위성을 고른다. |
| 설정 | TLE 를 받아올 주소(기본 / 대체), 최소 갱신 간격, 예측 기간, 최소 고각 |

## 기술 스택

- Kotlin, Jetpack Compose (Material 3)
- Room (관심 위성 + TLE 캐시)
- Retrofit / OkHttp (TLE 수집)
- DataStore Preferences (설정)
- FusedLocationProviderClient (위치)
- predict4java (SGP4 궤도 계산) — 아래 참고

## 궤도 계산 (predict4java)

predict4java 는 Maven Central 에 없고 JitPack 에만 올라가 있다
(`com.github.g4dpz:predict4java` — `1.2.0` 만 빌드 성공, `1.2.2` 이후는 빌드 실패 상태).

그래서 의존성으로 받는 대신 **LCAM 이 쓰는 소스를 그대로 앱에 이식**했다
(`app/src/main/java/kr/contec/satpass/orbit/`). 이유는 세 가지다.

1. LCAM 버전에는 Alpha-5 카탈로그 번호 파싱(`parseAlpha5CatNum`) 등 업스트림 1.2.0 에 없는
   수정이 들어 있어, 그대로 써야 서버와 계산 결과가 같다.
2. 원본은 Guava / commons-lang3 / commons-logging 에 의존하는데, 실제 사용처는
   `Preconditions.checkArgument` 1곳, `StringUtils.strip` 4곳, 로거 1곳뿐이었다.
   이식하면서 순수 JDK 코드와 `android.util.Log` 로 대체해 **추가 의존성이 없다.**
3. JitPack 빌드 가용성에 앱 빌드가 묶이지 않는다.

### LCAM 과 동일하게 맞춘 부분

| 항목 | 구현 위치 |
| --- | --- |
| 패스 예측 (`getPasses(오늘 00:00 UTC, 24 × 기간, false)`) | `PredictPassesUseCase` |
| 최대 고각 시각 = AOS~LOS 중간 시점 | `PredictPassesUseCase` |
| 최대 고각 = 그 시점 ±1분을 1초 간격으로 훑은 최댓값 | `PredictPassesUseCase.maxElevationAt` |
| 중간 방위각 = 최대 고각 시점의 방위각 | `PredictPassesUseCase.centerAzimuthAt` |
| TLE 3줄 묶음 파싱, 깨진 건만 건너뛰기 | `TleTextParser` |
| TLE epoch(`YYDDD.DDDD`) → UTC 변환 | `OrbitTime.tleEpochToInstant` |
| 최소 갱신 간격 안에서는 재요청 안 함 (기본 6시간) | `TleRepository.sync` |
| 궤적(방위각/고각) 계산 | `GetPassTrackUseCase` |

### LCAM 과 다르게 한 부분

- **자동 주기 갱신 대신 당겨서 새로고침.** 모바일에서는 백그라운드 주기 작업이 제약을 받으므로,
  사용자가 목록을 당길 때만 갱신하고 최소 간격으로 과요청을 막는다.
- **TLE 주소를 설정에서 바꿀 수 있다.** 기본 주소가 실패하면 대체 주소를 시도한다.
- **위성 1기당 계산 제한 시간이 15초** (LCAM 은 2초). 휴대폰 성능을 고려한 값.

## TLE 출처

| 구분 | 기본값 |
| --- | --- |
| 기본 | `https://celestrak.org/NORAD/elements/gp.php?GROUP=active&FORMAT=tle` |
| 대체 | `https://one.contec.kr/active_satellites.txt` |

둘 다 설정 화면에서 바꿀 수 있다. 네트워크가 모두 실패하면 기존 캐시를 그대로 쓴다.

## 빌드

```bash
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # 단위 테스트
```

### 요구 환경

| 항목 | 버전 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.7.1 (wrapper 포함) |
| AGP | 9.4.0 |
| Kotlin | 2.2.10 (AGP 내장) |
| compileSdk | 37 |
| minSdk | 26 (`java.time` 을 desugaring 없이 사용) |
| targetSdk | 36 |

Gradle 데몬이 쓸 JDK 는 `gradle/gradle-daemon-jvm.properties` 의 `toolchainVersion=21` 로
정해진다. 특정 경로를 박아 넣지 않으므로 개발 PC 와 CI 가 각자 설치된 JDK 21 을 찾아 쓴다.
(JDK 21 이 없으면 데몬이 뜨지 않으므로 설치가 필요하다.)

AGP 9 는 Kotlin 을 내장(built-in Kotlin)하므로 `org.jetbrains.kotlin.android` 플러그인을
따로 적용하지 않는다. KSP 가 `kotlin.sourceSets` 를 쓰기 때문에
`android.disallowKotlinSourceSets=false` 가 필요하다.

## 배포 (휴대폰에 설치)

GitHub 저장소의 **Actions 탭 → "배포 (APK 릴리스)" → Run workflow** 를 누르면
APK 를 빌드해서 Releases 에 붙여 준다. 실행이 끝나면 요약(Summary)에 릴리스 링크가 뜬다.

휴대폰 브라우저로 그 링크를 열어 `sat-pass-*.apk` 를 받으면 바로 설치된다.
처음 설치할 때는 브라우저에 "출처를 알 수 없는 앱 설치" 권한을 허용해야 한다.

저장소가 비공개이므로 휴대폰 브라우저에서도 GitHub 에 로그인되어 있어야 받을 수 있다.

`v1.0` 같은 태그를 푸시해도 같은 워크플로가 돈다.

### 서명에 대해

현재 릴리스 APK 는 **디버그 키로 서명**된다. 설치와 동작에는 문제가 없지만,
Play 스토어 배포나 기기 간 업데이트 서명 일치가 필요해지면 릴리스 키스토어를
GitHub Secrets 로 등록하고 `signingConfigs` 를 추가해야 한다.
