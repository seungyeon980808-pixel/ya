# Ya · 야

옆 사람에게 “야” 하고 말을 건네듯, 음성으로 기록하고 확인한 뒤 일정으로 연결하는 개인용 Android 앱입니다.

## 구성

- 음량 버튼/화면 버튼 PTT, WAV 원본 보존, 한국어 인식·파싱
- 승인함에서 검토·편집·승인 후 일정 알림 생성
- 명시적으로 선택한 Calendar에만 파생 일정 생성
- Google Sheets/Drive 동기화와 Apps Script 웹 승인함
- package: `com.malhaedwo.pttprobe` (기존 업데이트 호환을 위해 유지)

기존 코드의 화면·내부 명칭에는 이전 이름 “말해둬”가 남아 있습니다. 저장소/제품 이름은 Ya이며, 이번 공개용 복사본은 기능 전체의 이름 변경 작업과 구분합니다.

## 현재 상태

0.8.7 후보 소스입니다. 삭제·완료·수락된 편집 시 이미 게시된 알림을 회수하고, 일반 주기 동기화에서는 정상 알림을 유지하도록 수정했습니다. 호스트 테스트와 Android 전체 컴파일을 검증하지만, 이 후보의 실제 기기 설치·업데이트 보존·알림 회수 검증은 아직 필요합니다.

- 호스트 회귀: 40개 시나리오 / 308 assertions (운영 Java 클래스 + Android stubs)
- 실제 소리·진동·heads-up, 자연 절전, 재부팅, 네트워크 장애 복구는 별도 실기기 시험 대상
- 현재 정시 알림은 SCHEDULE만 지원합니다. TODO 정시 알림 지원을 주장하지 않습니다.
- 과거 버전에서 DB 행이 이미 제거된 고아 알림은 업데이트만으로 자동 수거하지 않습니다.
- 표준 Gradle 프로젝트가 아니라 기존 수동 Java/D8 빌드 구조입니다.

## 공개용 설정

운영 계정/프로젝트/문서 식별자와 개인 경로는 예시 값으로 교체했습니다. **그대로는 Google 연결이 작동하지 않습니다.**

| 파일 | 구성할 값 |
|---|---|
| `GoogleOAuth.java` | CLIENT_ID, REDIRECT_SCHEME, ALLOWED_EMAIL |
| `apk-input/AndroidManifest.xml` | OAuth callback scheme (REDIRECT_SCHEME와 동일) |
| `GoogleSyncEngine.java` | SPREADSHEET_ID |
| `web/Code.gs` | ALLOWED_EMAIL |
| `MainActivity.java` | 실제 개인정보처리방침 URL |

Java 파일은 `src/com/malhaedwo/pttprobe/` 아래에 있습니다. `YOUR_GOOGLE_OAUTH_CLIENT_ID`는 도메인 접미사를 제외한 OAuth client ID 부분을 뜻합니다. 실제 client ID/redirect는 Google 설정과 일치시켜야 합니다. `owner@example.invalid`는 로그인할 수 없는 예시 주소이며 허용 계정을 무제한으로 확장하지 않습니다. 앱과 웹은 동일한 명시적 계정 allowlist를 사용해야 합니다.

웹 코드는 사용할 Google Sheet에 연결된 Apps Script 프로젝트의 `Code.gs`, `Index.html`로 구성합니다. 개인 설정을 채운 사본은 공개 커밋하지 말고 로컬 또는 별도 비공개 저장소에서 관리하세요. OAuth client secret이나 토큰을 소스에 넣지 마세요.

## 호스트 테스트

JDK 11 이상이 필요합니다. 이 테스트는 Android 기기 시험의 대체물이 아닙니다.

```sh
export JAVA_HOME=/path/to/jdk
bash tests/wiring/run.sh
```

## 수동 APK 빌드

필요한 도구는 저장소에 포함하지 않습니다. 각 도구의 배포처와 라이선스를 확인하여 준비하세요.

```text
$PTT_TOOLCHAIN_HOME/
  sdk/platforms/android-35/android.jar
  libs/r8-9.4.17.jar
  libs/ARSCLib-1.4.0.jar
  libs/apksig-9.3.2.jar
  libs/java-base.jar
```

`java-base.jar`는 D8에 제공할 JDK 플랫폼 라이브러리이며 기존 수동 도구 모음 규약입니다. 환경에 따라 호환되는 JDK 라이브러리 준비가 필요합니다. JAVA_HOME을 생략하면 도구 모음의 `jdk/Contents/Home`을 사용합니다. minSdk 31 / targetSdk 35입니다.

```sh
export JAVA_HOME=/path/to/jdk
export PTT_TOOLCHAIN_HOME=/path/to/android-toolchain
export PTT_SIGNING_HOME=/private/path/to/signing
bash build.sh
```

서명 디렉터리는 `ptt-probe.jks`(alias `pttprobe`)와 비밀번호 파일 `password`를 로컬에서 제공해야 합니다. 기본 위치는 `~/.config/ya/signing`입니다. 기존 설치 위 업데이트에는 반드시 원래 서명키가 필요합니다. 빌드는 키를 자동 생성하거나 교체하지 않습니다. 출력은 `build/malhaedwo-ptt-probe-0.8.7.apk`이며 서명 검증·Manifest 검사·ZIP 무결성·SHA-256 확인을 실행합니다.

## 데이터 안전 원칙

- 승인 전 알림/Calendar 생성 금지
- 음성인식·동기화 실패 시 WAV 원본 보존
- 기존 앱 제거/데이터 초기화 없이 동일 서명 업데이트
- Calendar 삭제 실패 시 연결 정보와 재시도 상태 보존
- 운영 데이터, 음성, DB, 토큰, 서명키, 개인 로그, 기존 Git 이력은 이 공개 복사본에 포함하지 않음

라이선스는 아직 지정하지 않았습니다. 공개 저장소라는 사실만으로 재배포·변경 허가를 부여하지 않습니다.
