# CABIN MIX — Pleos Connect 차량용 앱 시작 프로젝트

## 현재 구현
- Android Automotive 앱 구성 (`android.hardware.type.automotive`)
- Kotlin + Jetpack Compose
- 추천 모드 6종 / 사용자 모드 생성·편집·복제·삭제
- 온도·풍량·시트 열선·통풍·앰비언트 색상 및 밝기·오디오 메모 수정
- SharedPreferences JSON 저장 (앱 재시작 후 유지)
- 모드 실행 결과 화면 (`모의 반영` 또는 `별도 연동 필요` 명시)

**주의: 실제 Vehicle SDK 연동은 아직 없으며 차량 하드웨어를 제어하지 않습니다.**

## 시작 방법
1. Android Studio에서 **Open** → 이 폴더 선택.
2. JDK 17, Android SDK API 35 설치. 최초 동기화 시 Gradle 8.10.2가 필요합니다. Android Studio가 wrapper 미포함 프로젝트를 바로 동기화하지 못하면 아래 'Gradle wrapper' 절차를 따르세요.
3. Pleos Connect Emulator 설치 후 Automotive AVD 생성 (공식 문서 링크 아래 참조).
4. Gradle Sync → Run `app`.

### Gradle wrapper
이 ZIP에는 gradle-wrapper.jar를 포함하지 않았습니다. 로컬 Gradle 8.10.2가 설치되어 있다면 이 폴더에서 `gradle wrapper --gradle-version 8.10.2`로 생성하세요. 또는 Android Studio에서 새 Automotive 프로젝트를 생성한 뒤 이 프로젝트의 `app/src/main`과 Gradle 설정을 옮기고 기존 wrapper를 사용하세요.

## 권장 다음 단계
1. 공식 Vehicle Sample App에서 `capability` / `get` / `set` 응답 확인
2. `VehicleController` 추상화 작성 (Mock/Pleos 구현 분리)
3. 현재 실행 결과 UI와 차량 상태 관리 연결
4. 주행 제한(`CarUxRestrictions`) 적용
5. 음악 등 별도 권한/SDK 확인
6. Room 이전, UI/안전 테스트 추가

## 공식 자료
- https://document.pleos.ai/docs/pleos-only/getting-started
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- https://github.com/Pleos-Playground/pleos-sample-vehicle-app
