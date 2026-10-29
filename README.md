# 바다한눈 | Galaxy Watch9 40mm Wear OS 낚시 가이드

Android Studio에서 바로 열 수 있도록 구성한 **워치 전용 Kotlin / Jetpack Compose 프로젝트**입니다.
일반 스마트폰 앱이나 HTML WebView가 아닌 **독립형 Wear OS 앱**입니다.

## 화면

| 탭 | 표시 정보 |
|---|---|
| **현황** | 날씨·기온·수온(SST)·파고·파주기·풍속·돌풍·풍향·강수확률·습도·물때·조위 흐름·다음 만/간조 추정·일출/일몰·안전 참고 |
| **TOP 3** | 현재 GPS와 가장 가까운 등록 낚시 포인트, 계절·시간·예보에 따른 대상어 TOP 3, 적합도, 메인 낚싯대, 추천 루어, 상세 공략 |
| **나침반** | 센서 실시간 방위각, GPS 위치가 있을 때 자기편각을 반영한 진북 |
| **포인트 변경** | 포항 4 · 경주 5 · 울산 5 · 부산 6, 총 20개 포인트 수동 선택 또는 GPS 재선택 |

HTML의 **15종 대상어 / 20포인트 / 3세트 장비** 데이터를
`app/src/main/assets/guide_data.json`으로 추출했습니다.
추출 데이터는 기존 `lure_guide.html`의 어종 시즌·대표 루어·장비·포인트 특성을 유지합니다.
원본 HTML도 `source/lure_guide.html`에 포함했습니다. 나중에 HTML의 어종·포인트를 수정하면
다음 명령으로 앱용 JSON을 다시 만들 수 있습니다(PC에 Node.js 설치 필요).

```bash
node tools/extract_guide_data.js
node tests/catalog_sanity.js
```

## 실행 방법 (Android Studio)

1. ZIP 압축을 풀고 Android Studio에서 **Open** → `BadaHannun_WearOS` 폴더를 선택합니다.
2. Android SDK **API 36**, **JDK 17 이상(권장 JDK 17)** 및 Google Maven 리포지터리 연결을 확인합니다.
3. Gradle Sync를 실행합니다. 프로젝트는 **AGP 8.13.2 / Kotlin 2.2.21 / Gradle 8.13 / Wear Compose Material3 1.7.1**을 사용합니다.
4. 기기 관리에서 **원형 Wear OS 에뮬레이터**를 선택하거나, 갤럭시 워치9에서 개발자 모드 → ADB 디버깅과 무선 디버깅을 켜고 워치를 연결합니다.
5. Run Configuration의 `app`을 선택하고 **Run ▶**을 누릅니다.
6. 앱 최초 실행 시 **위치 권한을 허용**하면 가까운 등록 포인트를 자동 선택합니다. 권한을 거부해도 포인트 수동 선택과 오프라인 어종 가이드를 사용할 수 있습니다.

### Gradle Wrapper

이 소스 ZIP에는 `gradle/wrapper/gradle-wrapper.properties`와 `gradlew` 및 `gradlew.bat`이 있습니다.
최초 `gradlew` 실행 시 공식 **Gradle 8.13 Wrapper JAR**를 Gradle 배포 서버에서 받도록 설정되어 있습니다.
Android Studio가 Wrapper JAR를 찾지 못하는 경우 프로젝트 폴더에서 먼저 다음을 실행하세요.

Windows:
```bat
gradlew.bat --version
```
macOS/Linux:
```bash
./gradlew --version
```
첫 다운로드에는 인터넷 연결이 필요합니다. Gradle 자체 배포 파일 및 Android 의존성도 최초에 다운로드됩니다.

## 실시간 데이터 및 한계

- **날씨·기온·풍속·돌풍·습도·강수확률:** `api.open-meteo.com`
- **수온·파고·파주기·예측 조위·다음 만/간조:** `marine-api.open-meteo.com`
- **기온과 풍속은 GPS 지점**, **해양 정보는 GPS에서 가장 가까운 등록 연안 포인트**의 예보입니다. 포인트를 수동 지정한 경우 기상·해양 모두 해당 포인트를 사용합니다.
- 현재 자료는 **실측 기상 관측이나 워치 내부 온도 센서가 아니라, 수치예보 모델 값**입니다.
- 수온은 해수면 온도 **SST**이며 해수욕장·항만 내 실제 수온과 다를 수 있습니다.
- **물때**는 HTML의 15일 주기 8물때식 계산을 가져온 **음력 기반 간이 표기**입니다. Android ICU 음력 달력과 지역 공식 물때표는 다를 수 있습니다.
- **만/간조**는 Open-Meteo의 `sea_level_height_msl` 시간별 데이터에서 극값을 잡은 **모델 추정 시각**입니다. 예측 조위는 평균해수면(MSL) 기준이고, 해도 기준면 수위나 항해용 조위가 아닙니다.
- **출조 안전 문구**는 HTML의 풍속·파고 임계값을 바탕으로 한 보조 지표입니다. 풍랑주의보, 너울, 현장 통제구역, 바닥 미끄럼, 낙뢰 위험은 별도 확인해야 합니다.
- **추천 TOP 3는 어획 확률이 아닙니다.** 계절·시간·해당 포인트의 알려진 대상어·모델 예보를 점수화한 가이드 적합도입니다.

## GPS 및 블루투스 기종 참고

- 갤럭시 워치9 블루투스 모델도 자체 위치 기술 및 지자기 센서를 지원합니다.
- 블루투스로 휴대전화에 연결되어 있으면 Wear OS가 보통 휴대전화의 네트워크를 프록시합니다. 휴대전화가 없을 때는 **워치 Wi-Fi 연결**이 필요합니다(LTE가 없는 모델).
- 위치가 확인되지 않으면 **기본값은 울산 정자항**이며, 화면에서 “선택 포인트 기준 · 현위치 아님”으로 알립니다.
- 30 km 이상 먼 포인트가 선택될 때는 추천 거리 한계 문구를 보여줍니다.
- 위치는 사용자가 허용할 때만 읽고, 연속 백그라운드 GPS 추적은 하지 않습니다. 워치와 예보 API 사이에서 위치 좌표가 전송될 수 있습니다.

## 전력 및 네트워크

- 앱이 열려 있는 동안 15분 간격으로 재조회합니다. `새로고침`으로 즉시 재요청할 수 있습니다.
- 위치는 앱 시작과 사용자의 `GPS 재검색` 시점에 획득합니다. 나침반 센서는 **나침반 화면에서만** 켭니다.
- 마지막 응답을 워치 로컬 캐시에 저장하고, 예보가 실패하면 **이전 예보 / 네트워크 오류**를 구분해 표시합니다. 없는 데이터를 `0 m/s`, `0 m`로 처리하지 않습니다.
- 별도 휴대전화 앱·서버·API 키는 필요하지 않습니다. 상업적 사용 시 API 라이선스·요청 제한은 별도 확인해야 합니다.
- 현재 버전은 **실행 중 갱신**이며, 백그라운드 알림·타일·워치페이스 컴플리케이션은 구현하지 않았습니다.

## 소스 구조

```
app/src/main/
  AndroidManifest.xml                # Wear OS / INTERNET / 위치 권한
  assets/guide_data.json             # HTML에서 추출한 20곳·15종·3장비
  java/com/eastsea/badahannun/
    MainActivity.kt                  # GPS, 권한, 네트워크 갱신, 화면 상태
    data/GuideCatalog.kt             # 원본 HTML 데이터 파싱
    data/ForecastRepository.kt       # Open-Meteo HTTPS, 캐시, 만조/간조 추정
    domain/FishingLogic.kt           # TOP3 및 안전등급, 거리, 시간대
    domain/TideCalculator.kt         # 음력 기반 간이 물때
    domain/CompassTracker.kt         # 회전벡터 / 가속도+지자기 센서
    ui/WearFishingApp.kt             # 40mm 원형 워치 UI
```

단위 테스트: `./gradlew :app:testDebugUnitTest`

## 데이터 제공자 및 출처 표시

- [Open-Meteo Weather Forecast API](https://open-meteo.com/en/docs)
- [Open-Meteo Marine Forecast API](https://open-meteo.com/en/docs/marine-weather-api)
- 해양 모델 제공자: **Météo-France MFWAM/SMOC/SST, ECMWF WAM, NOAA NCEP GFS Wave, DWD EWAM/GWAM**, Copernicus Marine Service 등의 모델. 지점·변수·일자에 따라 소스가 달라집니다.
- [Android Developers – Wear OS Compose](https://developer.android.com/training/wearables/compose)
- [Android Developers – Wear OS 네트워크](https://developer.android.com/training/wearables/data/network-communication)

## 빌드 검증 상태

이 패키지는 Kotlin 소스·Gradle 프로젝트 구조 및 JSON 카탈로그 데이터 검사를 완료한 개발용 원본입니다.
제작 환경에 **Android SDK / Gradle 빌드 런타임이 설치되어 있지 않아 실제 워치 APK 빌드와 실기기 검증은 수행하지 못했습니다.**
Android Studio에서 Sync / Run 시 환경에 따른 추가 호환성 조정이 필요할 수 있습니다.
