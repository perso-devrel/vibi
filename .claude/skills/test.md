---
name: test
description: Run unit tests smartly - API integration tests excluded by default to save ElevenLabs costs
trigger: keyword
keyword: 테스트
---

# DubCast Smart Unit Test Runner

API 비용(ElevenLabs)을 최소화하면서 테스트를 실행합니다.

## Test Architecture

- **Unit tests** (default): Fake/Mock 사용, API 호출 없음 → 항상 안전하게 실행
- **Integration tests** (`@Category(ApiTest::class)`): 실제 BFF API 호출 → 비용 발생, 명시적 요청 시만 실행

## Modes

| 키워드 | 동작 | API 비용 |
|--------|------|----------|
| `테스트` (기본) | 변경 파일 관련 유닛 테스트만 | 없음 |
| `전체 테스트` | 모든 유닛 테스트 (API 테스트 제외) | 없음 |
| `API 테스트` | 실제 API 호출 통합 테스트 | **있음** |

## Steps

### 1. Determine test scope

```!
cd C:/Users/EST/AndroidStudioProjects/DubCast && git diff --name-only HEAD 2>/dev/null; git diff --name-only --cached 2>/dev/null
```

### 2. Run tests based on scope

**"전체 테스트"** — 유닛 테스트 전체 (API 테스트 자동 제외):
```!
cd C:/Users/EST/AndroidStudioProjects/DubCast && ./gradlew testDebugUnitTest --quiet 2>&1 | tail -50
```

**"API 테스트"** — 실제 API 호출 통합 테스트 (비용 발생 경고 필수):
> ⚠️ 실행 전 반드시 사용자에게 "실제 API를 호출합니다. ElevenLabs 크레딧이 소모됩니다. 진행할까요?" 확인
```!
cd C:/Users/EST/AndroidStudioProjects/DubCast && BFF_BASE_URL=https://api.dubcast.example.com BFF_TEST_JOB_ID=<job-id> ./gradlew testDebugUnitTest -Pinclude.api.tests --tests "com.example.dubcast.integration.*" --quiet 2>&1 | tail -50
```

**기본 (선택적)** — 변경된 소스 파일의 관련 테스트만:
- 변경 파일 `Foo.kt` → `FooTest.kt` 매핑
- `--tests` 필터로 해당 테스트만 실행
- 매칭 테스트 없으면 "관련 테스트 없음" 보고

### 3. On failure, read report:

```!
cat C:/Users/EST/AndroidStudioProjects/DubCast/app/build/reports/tests/testDebugUnitTest/index.html 2>/dev/null | grep -oE '(tests|failures|ignored|duration)[^<]*' | head -10
```

### 4. On failure, find details:

```!
find C:/Users/EST/AndroidStudioProjects/DubCast/app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -l 'failures="[^0]' {} \; 2>/dev/null | while read f; do grep -A5 '<testcase' "$f" | grep -B1 'failure'; done
```

## Reporting Rules

- Report total tests run, passed, failed count
- For failures: show test name, expected vs actual, and the fix needed
- Clearly state which mode was used (selective / full / API integration)
- **API 테스트 실행 시**: 소모된 크레딧 추정치 함께 보고
