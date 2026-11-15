# N:MODE

**Personalized In-Vehicle Environment Modes**

N:MODE는 사용자가 자신의 상황과 활동에 맞춰 차량 실내 환경을 구성하고 관리할 수 있는 Android Automotive 기반 앱입니다.

차량의 여러 환경 설정을 하나의 모드로 관리하여, 사용자가 원하는 실내 환경을 보다 직관적으로 구성할 수 있도록 기획했습니다.

## 1. Project Overview

차량 내부에는 공조, 시트, 조명 등 다양한 편의 기능이 존재합니다. 하지만 사용자가 원하는 환경을 구성하려면 여러 설정을 개별적으로 조작해야 하는 번거로움이 있습니다.

N:MODE는 이러한 문제를 해결하기 위해 **활동 중심의 사용자 정의 차량 환경 모드**를 제안합니다.

사용자는 추천 모드를 살펴보거나 자신만의 모드를 생성하고 편집할 수 있습니다.

## 2. Key Features

### Personalized Modes
- 다양한 상황을 위한 차량 환경 모드 제공
- 사용자 정의 모드 생성 및 저장
- 기존 모드의 설정값 확인과 편집
- 여러 환경 설정을 하나의 모드로 구성

### Vehicle State Monitoring
- Pleos Vehicle SDK를 활용한 차량 상태 조회
- 공조 온도 및 송풍 관련 데이터 확인
- SDK에서 지원하는 항목과 차량 상태를 기반으로 정보 표시

### Mode Preview
- 사용자가 설정한 모드의 목표값 확인
- 현재 차량 상태와 모드 설정값 비교
- 실제 차량 제어 이전에 모드 구성 결과를 미리 확인

## 3. Implementation Scope

| 기능 | 구현 방식 |
|---|---|
| 사용자 정의 모드 | 앱 내부 기능 |
| 모드 생성 및 편집 | 앱 내부 기능 |
| 차량 공조 상태 조회 | Pleos Vehicle SDK 연동 |
| 모드 설정값 미리보기 | 시뮬레이션 |
| 실제 차량 환경 변경 | 현재 버전에서 미지원 |

**Important:** 현재 버전은 차량 상태 조회 및 모드 설정 시뮬레이션을 중심으로 구현했습니다. 모드 실행을 통한 실제 차량 환경 제어는 지원하지 않습니다.

## 4. Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose / Material 3
- **Platform:** Android Automotive
- **Vehicle Integration:** Pleos Vehicle SDK 2.3.2
- **Development Tool:** Android Studio
- **Minimum SDK:** 28
- **Target SDK:** 34

## 5. Installation

### Download APK

[Download N:MODE Release APK](apk/김가람_NMODE_release.apk)


### Requirements

- Android Automotive 기반 실행 환경
- Pleos Connect 호환 에뮬레이터
- 앱 실행에 필요한 Pleos SDK 환경

### Installation Steps

1. Pleos Automotive 에뮬레이터를 실행합니다.
2. 저장소의 `apk/NMODE-release.apk`를 다운로드합니다.
3. ADB를 이용해 APK를 설치합니다.

```bash
adb install 김가람_NMODE_release.apk
```

4. 에뮬레이터에서 N:MODE 앱을 실행합니다.

차량 상태 조회 기능은 실행 환경과 SDK 지원 여부에 따라 동작이 달라질 수 있습니다.

## 6. Source Code

주요 소스 코드:

`app/src/main/java/com/cabinmix/app/MainActivity.kt`

Android 프로젝트의 빌드 설정 및 Pleos SDK 의존성은 `app/build.gradle.kts`에서 확인할 수 있습니다.

Debug와 Release 빌드는 서로 다른 Application ID를 사용합니다.

- Release: `com.cabinmix.app`
- Debug: `com.cabinmix.app.debug`


## 7. Demonstration Video

**[N:MODE 앱 시연 영상 보기 (YouTube)](https://youtu.be/rcmPeP6iv_Q)**

[![N:MODE 시연 영상](https://img.youtube.com/vi/rcmPeP6iv_Q/maxresdefault.jpg)](https://youtu.be/rcmPeP6iv_Q)

시연 영상에서는 기본 모드 탐색, 모드 선택, 사용자 정의 모드 생성 및 편집, 차량 상태 조회와 미리보기 기능을 확인할 수 있습니다.

※ 본 시연은 Pleos Connect 에뮬레이터에서 진행했으며, 실제 차량 환경 제어는 수행하지 않습니다.


## 8. Project Status

본 프로젝트는 현대자동차그룹 차량용 앱 개발 공모전을 위해 제작한 프로토타입입니다.

현재 차량 상태 조회와 사용자 정의 모드 관리 기능을 구현했으며, 실제 차량 제어 기능은 추후 개발 범위에 포함됩니다.
