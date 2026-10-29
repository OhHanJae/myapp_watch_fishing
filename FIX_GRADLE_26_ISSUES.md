# AAR metadata 26개 오류 수정 (2026-10-29)

## 원인

프로젝트는 `AGP 8.13.2` 및 `compileSdk=36`을 사용하지만 기존 의존성인 Compose BOM `2026.09.00`(Compose 1.12.x), Wear Compose `1.7.1`은 API 37 및 더 높은 AGP를 요구해 26개의 AAR metadata 오류가 발생했습니다.

## 변경 사항

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| Compose BOM | `2026.09.00` | `2026.06.00` |
| Wear Compose Foundation | `1.7.1` | `1.6.2` |
| Wear Compose Material3 | `1.7.1` | `1.6.2` |
| AGP | `8.13.2` | 유지 |
| compileSdk / targetSdk | `36` / `36` | 유지 |
| Gradle Wrapper | `8.13` | 유지 |

변경 파일은 `app/build.gradle.kts`입니다. 나머지 소스 코드와 낚시 포인트·어종 데이터는 변경하지 않았습니다.

## Android Studio 적용 방법

1. 브랜치를 체크아웃하거나 PR을 병합한 후 프로젝트를 다시 엽니다.
2. **File → Sync Project with Gradle Files**를 실행합니다.
3. 성공하면 **Build → Make Project** 또는 ▶ **Run**으로 빌드합니다.
4. 실패 시 Build Output에 표시된 **첫 번째 오류 메시지**를 확인합니다.

## 검증 범위 및 주의

- 이 패치는 제공된 AAR metadata 26건의 **버전 호환성 문제를 수정하기 위한 설정 변경**입니다.
- Android SDK가 없는 작성 환경이라 **실제 Gradle assembleDebug/워치 실기기 실행은 확인하지 못했습니다**. 다른 컴파일 문제가 있을 수 있습니다.
- 본 PR은 `master`에 자동 병합하지 않습니다.

## 근거

- [Google: Compose 1.12 릴리스의 API 37 요구](https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html)
- [Android: Wear Compose Material3 1.6.x / 1.7.x 호환성](https://developer.android.com/agents/skills/wear/wear-compose-m3/skill)
