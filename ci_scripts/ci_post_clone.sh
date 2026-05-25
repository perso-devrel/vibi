#!/bin/sh
# Xcode Cloud post-clone hook. Apple docs 상 ci_scripts/ 는 repository root 또는
# Xcode project 옆에 두면 자동 실행 — repo root 가 가장 표준이라 여기에 둠.
#
# Xcode Cloud macOS runner 에는 JDK 미설치 → KMP 의 ./gradlew 호출이 fail.
# brew 로 openjdk@21 (Gradle 9.3.1 호환 LTS) 설치만 담당.
#
# 이 스크립트의 export 는 후속 Xcode build phase 의 별도 sub-shell 로 전파되지
# 않으므로, project.yml 의 "Build Kotlin frameworks" phase 에서 brew --prefix
# 로 JAVA_HOME 을 동적 resolve 한다.

set -eu

echo "[ci_post_clone] PATH=$PATH"
echo "[ci_post_clone] which brew: $(command -v brew || echo not-found)"

brew install --quiet openjdk@21

JDK_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
echo "[ci_post_clone] installed JDK at $JDK_HOME"

# 검증 — 후속 build phase 가 의존하는 디렉토리가 실제로 존재하는지 즉시 확인.
test -x "$JDK_HOME/bin/java" || {
  echo "[ci_post_clone] ERROR: java binary not found at $JDK_HOME/bin/java" >&2
  ls -la "$(brew --prefix openjdk@21)/libexec" 2>&1 || true
  exit 1
}

"$JDK_HOME/bin/java" -version
