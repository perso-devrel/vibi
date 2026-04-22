# DubCast Android — 영상 하나로 AI 더빙 · 립싱크 · 자막까지

## 1. 한 줄 정의
안드로이드 폰에서 영상 하나만 올리면 AI 더빙 + 립싱크 + 자동자막을 생성하고, 타임라인 에디터로 편집한 뒤 ffmpeg로 완성본 영상 파일로 내보내는 앱.

## 2. 타깃 유저
- 안드로이드 비중이 높은 동남아/인도/한국 크리에이터.
- "PC 편집 앱 열기 전 단계"에서 이탈하는 숏폼 제작자.
- 다국어 현지화가 필요한 1인 강사·셀러.
- 핵심 가치 제안: "영상 → 더빙된 다국어 버전 (자막 포함) → 갤러리 저장"을 한 흐름으로.

## 3. 핵심 사용자 여정

### 3.1 입력
갤러리에서 영상 선택 (최대 10분, 1080p 이하 권장, mp4/mov/webm).

### 3.2 타임라인 에디터
- ExoPlayer 비디오 프리뷰 + 드래그 가능한 플레이헤드.
- 더빙 삽입: 언어 선택 → 보이스 선택 → 텍스트 입력 → TTS 생성 → 타임라인에 클립 배치.
  - "Show on screen" 토글: 켜면 더빙 입력 텍스트를 `\n` 단위로 분할해 오디오 싱크에 맞춘 자동 자막으로 동시 생성 (글자 수 가중 분할, 마지막 줄에 오차 흡수). 자동 자막은 텍스트 수정 불가, 위치/크기만 스티커처럼 조정 가능. 더빙 삭제 시 연동 삭제.
- 더빙 클립 드래그로 위치 이동, 선택 후 삭제.
- 스티커 이미지 삽입: 갤러리에서 선택 → 기본 3초 duration으로 배치 → 프리뷰에서 드래그/리사이즈로 위치·크기 조절, 타임라인에서 구간 조절.
- undo/redo 지원 (최대 50단계).

### 3.3 내보내기
저장 방식 선택:
- **본 영상만 저장**: 편집한 영상 그대로 내보내기.
- **다국어 번역본**: 아래 옵션을 조합하여 내보내기.
  - 번역 언어 선택 (한국어/English/日本語/中文/Español/Français/Deutsch).
  - 더빙 ON/OFF — AI 보이스 더빙 적용.
    - 동영상에 삽입한 더빙: 원어 유지 / 더빙 언어로 변경.
  - 립싱크 ON/OFF — 더빙 음성에 맞춰 입 모양 동기화.
  - 자동 자막 ON/OFF — 번역 언어로 자막 자동 생성 및 번인.

온디바이스 ffmpeg-kit으로 렌더링:
- 더빙 오디오 믹싱 (adelay + amix 필터 체인).
- 자막 번인 (.ass + libass, Noto Sans KR 폰트).
- 출력: 1080p H.264 + AAC, mp4.

### 3.4 저장/공유
갤러리 저장, 공유 시트.

## 4. 화면 구성
| 화면 | 설명 |
|------|------|
| Input | 갤러리에서 영상 선택, 메타데이터 검증 |
| Timeline | ExoPlayer 프리뷰 + 타임라인 에디터 (더빙/자막/스티커 이미지 클립 관리) |
| Export | 저장 방식 선택 (본 영상 / 다국어 번역본) + 옵션 설정 + 내보내기 |
| Share | 갤러리 저장, 공유 |

## 5. API (BFF v2)
| 기능 | 엔드포인트 |
|------|-----------|
| 보이스 목록 | `GET /api/v2/voices` |
| TTS 합성 | `POST /api/v2/tts` |
| 립싱크 요청 | `POST /api/v2/lipsync` (multipart) |
| 립싱크 상태 | `GET /api/v2/lipsync/{jobId}/status` |
| 립싱크 다운로드 | `GET /api/v2/lipsync/{jobId}/download` |

모든 빌드에서 실제 BFF 서버에 연결. `local.properties`의 `BFF_BASE_URL`을 실제 서버 주소로 설정 필요.

## 6. 아키텍처
```
[Android App (Kotlin, Jetpack Compose)]
├─ UI Layer
│  ├─ InputScreen / InputViewModel
│  ├─ TimelineScreen / TimelineViewModel (ExoPlayer, 클립 관리, undo/redo)
│  ├─ ExportScreen / ExportViewModel (옵션 선택, ffmpeg 렌더링)
│  └─ ShareScreen / ShareViewModel (갤러리 저장)
├─ Domain Layer
│  ├─ Models: EditProject, Segment, DubClip, SubtitleClip, ImageClip, TextOverlay, BgmClip, Stem, Voice, SubtitlePosition
│  ├─ Use Cases: input/, timeline/, tts/, subtitle/, image/, text/, bgm/, lipsync/, separation/, export/, share/
│  └─ Repository Interfaces
├─ Data Layer
│  ├─ Room DB (v14): EditProject, Segment, DubClip, SubtitleClip, ImageClip, TextOverlay, BgmClip 테이블
│  ├─ Retrofit + Moshi: BFF v2 API
│  └─ Repository Implementations
└─ DI: Hilt (DatabaseModule, NetworkModule, RepositoryModule)
        │
        ▼
[BFF (자체 서버)]
├─ TTS / 립싱크 / 보이스 API 프록시
└─ BFF_BASE_URL은 local.properties에서 BuildConfig으로 주입
```

보안: API 키는 BFF에만 보관. 앱은 자체 JWT/세션.

## 7. 데이터 모델
```kotlin
EditProject { projectId, frameWidth, frameHeight, backgroundColorHex, createdAt, updatedAt }
Segment {
    id, projectId, type: VIDEO|IMAGE, order, sourceUri, durationMs,
    width, height, trimStartMs, trimEndMs, volumeScale, speedScale
}  // 타임라인의 단일 영상/이미지 세그먼트 (trim/split/range 편집 단위)
DubClip { id, projectId, text, voiceId, voiceName, audioFilePath, startMs, durationMs, volume }
SubtitleClip {
    id, projectId, text, startMs, endMs, position: SubtitlePosition,
    // 자동 자막 전용 (sourceDubClipId != null 이면 자동 자막 = 스티커 좌표 사용)
    sourceDubClipId: String?, xPct: Float?, yPct: Float?, widthPct: Float?, heightPct: Float?
}
SubtitlePosition { anchor: Anchor(TOP|MIDDLE|BOTTOM), yOffsetPct: Float }
ImageClip { id, projectId, imageUri, startMs, endMs, xPct, yPct, widthPct, heightPct, lane }  // 영상 위 스티커 오버레이
TextOverlay { id, projectId, text, fontFamily, fontSizeSp, colorHex, startMs, endMs, xPct, yPct, lane }
BgmClip { id, projectId, sourceUri, sourceDurationMs, startMs, volumeScale }
Stem { stemId, displayName, downloadUrl, kind: BACKGROUND|VOICE_ALL|SPEAKER }  // 오디오 분리 결과 stem
Voice { voiceId, name, previewUrl, language }
```

## 8. 성공 지표 (MVP)
- 업로드 → 내보내기 완료까지 평균 < 12분 (5분 영상 기준).
- 내보낸 파일 갤러리 저장률 > 70%.
- ffmpeg 번인 실패율 < 3%.
