# Ya Desktop 0.1.0 Windows 검증

상태: **Windows 설치 패키지 빌드 PASS / 실제 Windows 설치·Google 연결은 미검증**.

## 범위

- Tauri v2 + Vite + vanilla TypeScript 기반 Windows x64 설치 앱의 첫 안전한 셸.
- Ya 승인 데스크의 목록·상세·상태 필터 UI와 23열 Items 데이터 계약.
- 실제 사용자가 아닌 명시적 인공 픽스처 3건만 포함.
- 로컬 앱 데이터 경로에 versioned JSON cache를 원자적으로 저장·읽기. 손상 캐시는 격리 후 빈 상태로 복구.
- Google 연결 상태는 `unconfigured`, 쓰기 권한은 `false`. 승인·수정·삭제·Calendar 컨트롤은 이유와 함께 비활성화.
- 네트워크, 원격 WebView, shell, 범용 filesystem/http capability, OAuth 설정, 운영 URL·Sheet ID·사용자 데이터 없음.
- Windows bundle: 현재 사용자 NSIS 및 MSI, WebView2 download bootstrapper.

## 로컬 검증

- `npm ci --ignore-scripts` 후 sandbox용 WASM 빌드 경로 사용.
- Vitest UI 4/4 PASS: bootstrap/render, 필터·선택, 쓰기 차단·상태 표기.
- TypeScript noEmit 및 Vite production build PASS.
- `npm run acceptance` PASS: package/version, Tauri identifier, CSP, 최소 capability, 23열 TS/Rust 계약, 인공 fixture 수, 캐시 시험 존재, 쓰기 차단, CI 단계, 민감 설정/금지 파일 검사.
- 1309×818 브라우저 렌더: document scrollWidth=viewport 1309, scrollHeight=viewport 818. 가로·문서 세로 overflow 없음. snapshot으로 필터와 항목 선택 동작, 비활성 쓰기 컨트롤 확인.
- 로컬 macOS sandbox에는 Rust 실행 도구가 없어 Rust 컴파일 결과를 로컬 PASS로 대신 주장하지 않음.

## Windows CI

- 공개 별도 브랜치: `desktop/windows-0.1.0`.
- 소스 commit: `469679f3fc562724a7fcc7736bcc329de432bbc5`.
- GitHub Actions run: https://github.com/seungyeon980808-pixel/ya/actions/runs/34573380921
- Windows runner에서 npm install, frontend test/build, Cargo lock 생성, `cargo test --locked`, Tauri NSIS+MSI build, artifact upload 모두 PASS.
- workflow run conclusion `success`, duration 9m40s.
- Actions artifact digest: SHA-256 `364f9181aa8142d64defb91d04637ecec4b562336750bd61a0351815a232ac14`.

## 산출물

- `Ya Desktop_0.1.0_x64-setup.exe`: 1,854,470 bytes, SHA-256 `af7a824b519ddedac7afcde6ac8c8739358a0c1b4c71c722ec94164abd8a1728`.
- `Ya Desktop_0.1.0_x64_en-US.msi`: 2,813,952 bytes, SHA-256 `14947da4b4cf9bec54c118a89fee34b1297e94197c7fa9a59b4703d03158c7d4`.
- 다운로드 ZIP: 4,435,922 bytes, SHA-256 `364f9181aa8142d64defb91d04637ecec4b562336750bd61a0351815a232ac14`; 내부 2개 파일 `unzip -t` PASS.
- 로컬 `file` 판정: NSIS PE32 GUI installer, MSI x64 Windows Installer database / Subject Ya Desktop.

## 미검증·다음 게이트

- `[blocked: Windows device required]` Windows 10/11 실제 설치·실행·제거, WebView2 bootstrap, 화면 배율·다크모드·키보드 접근, Defender SmartScreen은 실제 Windows에서 확인해야 한다.
- 설치본은 코드 서명되지 않은 개인 테스트 후보다. 배포용 신뢰성이나 SmartScreen 평판을 주장하지 않는다.
- `[blocked: desktop OAuth client configuration required]` 실제 Google Sheets 읽기 연결은 데스크톱용 OAuth Authorization Code + PKCE와 토큰 보관 정책이 확정돼야 한다. Android client ID나 비밀정보를 재사용하지 않는다.
- 현재는 네이티브 알림·tray·자동 실행·60초 동기화·오프라인 실제 데이터 캐시·쓰기를 구현하지 않았다.
- 다음 순서: Windows 실제 설치 smoke test → Desktop OAuth/read-only Sheets adapter → 실제 데이터 cache → 승인된 일정 native notification → tray/autostart → 쓰기/승인 경계.

Android, 운영 Apps Script, Calendar, Google Sheet 및 모바일 데이터는 변경하지 않았다. main 브랜치에는 병합하지 않았다.
