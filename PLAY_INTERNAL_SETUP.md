# 바다한눈 — Google Play 내부 테스트 자동 배포

저장소: `OhHanJae/myapp_watch_fishing` / Wear OS 전용 패키지명: `com.eastsea.badahannun`.

## 구성 개요

- PR: 기존 `Wear OS CI`가 빌드, Kotlin 테스트, 설정 검사. PR에서는 Play 비밀키에 접근하지 않습니다.
- 최초 1회: Play Console 앱 등록, **업로드 서명키 생성**, 테스트 계정 초대, 첫 AAB 업로드 필요.
- 자동화 설정 후: GitHub `master` 반영 → Actions에서 **서명된 AAB** 빌드 → Google Play `internal` 트랙 게시.
- 워치가 테스트 앱에 가입되어 있고 Play 자동 업데이트가 활성화되어야 새 버전을 받을 수 있습니다. **즉시 설치는 보장되지 않습니다**.
- 코드에서 `production` 게시를 하지 않습니다.

## 최초 설정: 1~7만 하면 됨

### 1. Play Console에 워치 앱 등록

[Google Play Console](https://play.google.com/console/)에서 앱 **바다한눈** 생성.

- 패키지명이 정확히 `com.eastsea.badahannun`이어야 합니다.
- `테스트 및 출시 → 고급 설정 → 폼 팩터`에서 **Wear OS**를 추가하고 워치 스크린샷 등 필수 스토어 정보를 입력합니다(실제 메뉴 이름은 콘솔 버전에 따라 다를 수 있음).
- `테스트 및 출시 → 테스트 → 내부 테스트 → 테스터`에 워치에서 사용할 Google 계정을 추가합니다.
- 내부 테스트는 최대 100명. 프로덕션 공개가 아닙니다.

공식: [Wear OS 배포](https://developer.android.com/training/wearables/packaging), [내부 테스트](https://support.google.com/googleplay/android-developer/answer/9845334?hl=ko).

### 2. 업로드 키 만들기·안전하게 백업

Android Studio → **Build → Generate Signed Bundle / APK → Android App Bundle → Create new**에서 업로드 keystore(`.jks`)를 생성합니다.

- key store 파일, key store password, key alias, key password를 별도로 보관합니다.
- Google Play에는 **Play App Signing**을 사용합니다. CI는 이 **업로드 키**로 AAB를 서명하고 Google Play가 설치용 서명을 관리합니다.
- 업로드 서명키를 분실하면 이후 배포가 중단될 수 있으니 백업이 중요합니다.
- 이미 같은 패키지명으로 Play에 앱이 있다면 기존 업로드 키 구성을 사용해야 합니다.

공식: [앱 서명](https://developer.android.com/studio/publish/app-signing).

### 3. GitHub Environment와 서명 Secrets

GitHub 저장소 → **Settings → Environments → New environment** → 이름 **`google-play-internal`**.

**Deployment branches**를 가능하면 `master`만 허용하고, **Environment secrets**에 아래 5개를 추가합니다.

| Secret 이름 | 내용 |
|---|---|
| `PLAY_UPLOAD_KEYSTORE_B64` | JKS 파일 전체의 Base64 인코딩 문자열 |
| `PLAY_UPLOAD_STORE_PASSWORD` | JKS 저장소 비밀번호 |
| `PLAY_UPLOAD_KEY_ALIAS` | JKS 키 별칭 |
| `PLAY_UPLOAD_KEY_PASSWORD` | JKS 키 비밀번호 |
| `PLAY_SERVICE_ACCOUNT_JSON` | 4단계 서비스 계정 JSON **전체 내용** |

Windows PowerShell에서 Base64를 화면에 출력하지 않고 클립보드로 복사:

~~~powershell
# 실제 JKS 경로로 변경
$jks = "C:\SecureKeys\bada-upload.jks"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)) | Set-Clipboard
~~~

GitHub의 `PLAY_UPLOAD_KEYSTORE_B64` 입력란에 붙여넣습니다. Base64는 **암호화가 아니므로** Secrets 밖에 유출하지 마세요.

### 4. Play Developer API와 서비스 계정 등록

1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트 생성 또는 선택.
2. [Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com)를 사용 설정.
3. Cloud → IAM 및 관리자 → 서비스 계정에서 새 계정을 만들고 JSON 키를 발급받습니다.
4. JSON 키 파일의 **내용 전체**를 GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON`에 저장.
5. Play Console → **사용자 및 권한**에서 서비스 계정 이메일을 초대하고 이 앱의 **Release apps to testing tracks(테스트 트랙 출시)** 권한만 부여. **프로덕션 출시 권한은 부여하지 않습니다**.

서비스 계정에 Cloud 프로젝트 전체 관리자 권한을 줄 필요는 없습니다.

공식: [Play Developer API 시작](https://developers.google.com/android-publisher/getting_started), [권한](https://support.google.com/googleplay/android-developer/answer/9844686?hl=en).

### 5. 첫 서명 AAB 생성 (자동 Play 게시 OFF)

**PR 병합 후 `master`에 워크플로가 올라온 상태**에서 GitHub → **Actions → Wear OS - Google Play Internal → Run workflow**.

- Branch: `master`
- Mode: `bundle_only` (기본값)
- Run workflow. 성공 시 Artifacts → `bada-hannun-play-signed-aab` 다운로드.
- 압축을 풀어 나오는 `app-release.aab` 파일을 **Play Console 내부 테스트의 첫 새 버전**으로 수동 업로드합니다.
- Play App Signing 동의·출시 절차와 스토어 필수 항목을 완료합니다.
- 테스터 참여 링크(opt-in)로 실제 계정에서 설치 가능한지 확인합니다.

이 모드는 Play API 업로드를 **절대 실행하지 않습니다**. 처음부터 `PLAY_DEPLOY_ENABLED`를 켤 필요가 없습니다.

### 6. GitHub 자동 배포 켜기

첫 내부 테스트 버전이 정상 등록된 것을 확인한 뒤, 저장소 → **Settings → Secrets and variables → Actions → Variables**에서 Repository variable:

- 이름: **`PLAY_DEPLOY_ENABLED`**
- 값: **`true`**

이후 `master`에 새 커밋/병합이 들어오면 자동으로 `internal` 트랙으로 업로드합니다. 즉시 한 번 실행해 보려면 Actions → Run workflow → Mode `publish_internal`를 선택하세요.

임시 중지는 `PLAY_DEPLOY_ENABLED=false`로 변경하면 됩니다.

선택사항: `PLAY_VERSION_CODE_BASE`. 기본값 `100000`, 실제 versionCode = base + 해당 워크플로 실행 번호. 이미 Play에 이보다 높은 versionCode가 존재하면 **기존 최대 코드보다 커지도록** base를 조정해야 합니다. 코드는 2,100,000,000 이하여야 합니다. 같은 실행을 재시도하여 versionCode가 중복되면 새 workflow 실행을 하세요.

### 7. 워치에 Play 테스트 버전 설치

- Play Console 내부 테스트에서 제공하는 **테스터 참여 링크**를 워치의 Google 계정으로 열고 가입합니다.
- Galaxy Watch9에서 Play 스토어로 **바다한눈** 설치. 자동 업데이트를 켜두면 배포된 새 버전을 기기 상황에 따라 자동 설치합니다. 즉시 필요하면 Play 스토어에서 **업데이트** 버튼을 확인하세요.
- **중요:** 기존 ADB 디버그 버전은 *디버그 키*로 서명되어 Play 스토어 설치본과 **서명이 다릅니다**. 처음 전환할 때는 앱 데이터 백업 후 디버그 설치본 삭제 → Play 설치가 필요할 수 있습니다. 이후에는 Play 버전끼리 업데이트됩니다.

## 각 워크플로의 의미

| 실행 | 결과 |
|---|---|
| PR | 기존 Wear OS CI 빌드·테스트, Play 비밀키 접근 없음 |
| master 병합 (변수 OFF) | 자동 Play 배포 작업 skipped — 정상 |
| 수동 `bundle_only` | 서명 AAB 생성과 Artifact 저장, Play 업로드 없음 |
| master 병합 (변수 ON) | 서명 AAB 빌드 후 Play 내부 테스트 게시 |
| 수동 `publish_internal` (변수 ON) | 새 versionCode로 Play 내부 테스트 게시 |

## 장애 시 확인

- `Package not found`: 같은 packageName으로 Play Console 앱과 **첫 수동 AAB 업로드**를 완료했는지 확인.
- `403 permission denied`: 서비스 계정 Play Console의 해당 앱 **테스트 트랙 출시** 권한.
- `Invalid upload key/signature mismatch`: Play의 업로드 인증서와 JKS가 같은지 확인.
- `Version code already used`: 새 workflow 실행 또는 `PLAY_VERSION_CODE_BASE`를 더 크게.
- `Publishing pending`: Play Console의 검토·관리형 게시 상태 확인.
- Play 다운로드가 안 됨: Wear OS 폼 팩터/테스터 계정/참여 링크/Play 스토어 연결 확인.
- 워치에 업데이트 안 됨: 자동 업데이트, Play 동기화, 기기 연결 상태 점검. **GitHub에서 AAB를 올리는 것과 워치에 즉시 설치되는 것은 다릅니다**.

이 저장소는 공개 저장소입니다. 업로드 키나 JSON 계정 키를 레포 파일, Issue, PR, 로그에 올리지 마세요.
