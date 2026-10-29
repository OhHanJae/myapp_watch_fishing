# 바다한눈 Wear OS 2.2 빠른 실행

1. Android Studio → Open → 이 프로젝트 → Gradle Sync.
2. 연결된 갤럭시 워치9 선택 → `app` Run ▶.
3. 앱 홈: 타일처럼 **방위 링 · 바람 방향/움직임 · 파도 연출 · 기온 · 파고 · 수온 · 물때 · 추천 어종** 확인.
4. **추천 어종 TOP 3** 또는 **12시간 예보 그래프**로 이동. 별도 나침반 메뉴는 없고 홈 링이 실제 방위를 표시합니다.
5. **포인트명** 또는 **지역 · 포인트** → 지역 선택 → 포인트 선택.
6. 자동 모드로 복귀: **내 위치로** → GPS 다시 확인, 수동 선택 해제.
7. 워치 타일 추가 → **바다한눈 요약** 선택. 타일에는 마지막 측정 방위·바람과 파도 정적 효과가 표시됩니다. **실시간 움직임은 앱 홈에서** 확인하세요.
8. 12시간 그래프와 시간별 목록은 절대시각(KST), 1순위 어종 색상 그래프를 추가로 표시합니다.

> `물때*`와 안전 지표는 간이 추정/예보입니다. 공식 물때표·기상특보 대체 불가.
> SDK 없는 환경에서 APK 실행은 미검증이며, PR의 GitHub Actions 빌드 결과를 확인해 주세요.

## 장치 선택에서 'missing uses-feature watch' 경고가 뜨면

1. `git switch master` / `git pull --ff-only origin master` 실행 후 Android Studio에서 Gradle Sync.
2. ▶ 옆 실행 구성 `app` 대신 **Wear OS - 바다한눈** 선택. **Run → Edit Configurations**의 모듈이 **`:app`**인지 확인.
3. Build → Rebuild Project. 지속되면 `app/src/main/AndroidManifest.xml`의 **Merged Manifest** 탭을 확인.
4. 최종 APK 확인은 `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check-wear-apk.ps1` 명령 한 번. 결과에 필수 watch 기능이 있으면 IDE 로컬 구성/캐시 문제입니다.
