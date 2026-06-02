import AVFoundation
import Cmp
import Foundation

/// Kotlin `OnDeviceVideoExportBridge` (`:shared` iosMain) 의 Swift 구현 — 영상전용 편집을 온디바이스
/// 에서 AVFoundation 으로 합성·HW 인코딩한다. 서버 ffmpeg 왕복(업로드+인코딩+다운로드)을 제거하는 게 목적.
///
/// **Swift 인 이유**: K/N iOS klib 에 `AVMutableAudioMix.inputParameters` 등 오디오 setter 가 미노출
/// 이라(ios-kn-patterns skill) per-segment 볼륨/속도 피치 보정을 Kotlin 에서 구성 불가. 여기선 전체
/// AVFoundation API 를 자유롭게 쓴다.
///
/// 적격성 판정(BGM·음원분리·frame·다중소스 제외)은 Kotlin `canEncodeOnDevice` 가 이미 끝낸 상태로,
/// 여기로 온 입력은 **단일 소스의 trim/속도/볼륨/분할/재정렬 concat** 뿐이다.
final class OnDeviceVideoExportBridgeImpl: NSObject, OnDeviceVideoExportBridge {

    private let workQueue = DispatchQueue(label: "vibi.ondevice.export", qos: .userInitiated)

    func export(
        segments: [OnDeviceSegmentSpec],
        outputPath: String,
        onProgress: @escaping (KotlinInt) -> Void,
        onComplete: @escaping (String?, String?) -> Void
    ) -> OnDeviceExportCancellable {
        let handle = ExportCancellable()

        workQueue.async {
            if handle.isCancelled { return }
            do {
                let session = try self.buildExportSession(segments: segments, outputPath: outputPath)
                handle.attach(session: session)

                // 진행률 폴링 — AVAssetExportSession.progress 는 KVO 비신뢰라 타이머로 폴.
                let timer = DispatchSource.makeTimerSource(queue: self.workQueue)
                timer.schedule(deadline: .now() + 0.1, repeating: 0.2)
                timer.setEventHandler {
                    onProgress(KotlinInt(int: Int32(session.progress * 100)))
                }
                timer.resume()
                handle.attach(timer: timer)

                session.exportAsynchronously {
                    timer.cancel()
                    switch session.status {
                    case .completed:
                        onProgress(KotlinInt(int: 100))
                        onComplete(outputPath, nil)
                    case .cancelled:
                        // 사용자 취소 — Kotlin suspend wrapper 의 invokeOnCancellation 이 이미 처리하므로
                        // 완료 콜백은 굳이 부르지 않는다(중복 resume 방지). 단 race 안전을 위해 error 로 보고.
                        onComplete(nil, "cancelled")
                    default:
                        let msg = session.error?.localizedDescription ?? "export failed (status \(session.status.rawValue))"
                        onComplete(nil, msg)
                    }
                }
            } catch {
                onComplete(nil, error.localizedDescription)
            }
        }

        return handle
    }

    // MARK: - Composition 빌드

    private enum ExportError: LocalizedError {
        case noVideoTrack
        case compositionTrackCreationFailed
        case exportSessionCreationFailed
        var errorDescription: String? {
            switch self {
            case .noVideoTrack: return "source has no video track"
            case .compositionTrackCreationFailed: return "failed to create composition track"
            case .exportSessionCreationFailed: return "failed to create export session"
            }
        }
    }

    private func buildExportSession(
        segments: [OnDeviceSegmentSpec],
        outputPath: String
    ) throws -> AVAssetExportSession {
        let composition = AVMutableComposition()
        guard
            let videoTrack = composition.addMutableTrack(
                withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
            let audioTrack = composition.addMutableTrack(
                withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        else {
            throw ExportError.compositionTrackCreationFailed
        }

        // 단일 소스라 asset 캐시는 path 별 1개. (다중소스는 Kotlin 적격성에서 이미 걸러짐.)
        var assetCache: [String: AVURLAsset] = [:]
        var transformApplied = false
        var hasAnyAudio = false
        let audioParams = AVMutableAudioMixInputParameters(track: audioTrack)

        for spec in segments {
            let asset = assetCache[spec.sourceFilePath] ?? {
                let a = AVURLAsset(
                    url: URL(fileURLWithPath: spec.sourceFilePath),
                    options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
                Self.loadSynchronously(asset: a, keys: ["tracks", "duration"])
                assetCache[spec.sourceFilePath] = a
                return a
            }()

            guard let srcVideo = asset.tracks(withMediaType: .video).first else {
                throw ExportError.noVideoTrack
            }
            let srcAudio = asset.tracks(withMediaType: .audio).first

            // iOS 카메라 회전은 preferredTransform 메타로 기록 — composition track 기본 identity 라
            // 명시 복사 없으면 영상이 눕는다(ios-kn-patterns / buildCompositionPlayer 와 동일).
            if !transformApplied {
                videoTrack.preferredTransform = srcVideo.preferredTransform
                transformApplied = true
            }

            let trimStart = CMTimeMake(value: spec.trimStartMs, timescale: 1000)
            let trimEnd = CMTimeMake(value: spec.trimEndMs, timescale: 1000)
            let sourceDuration = CMTimeSubtract(trimEnd, trimStart)
            let sourceRange = CMTimeRange(start: trimStart, duration: sourceDuration)
            let insertAt = composition.duration

            try videoTrack.insertTimeRange(sourceRange, of: srcVideo, at: insertAt)
            if let srcAudio = srcAudio {
                try audioTrack.insertTimeRange(sourceRange, of: srcAudio, at: insertAt)
                hasAnyAudio = true
            }

            // 속도 — 삽입 구간을 scale. video+audio 동일 배율(피치 보존은 session.audioTimePitchAlgorithm).
            if spec.speedScale != 1.0 && spec.speedScale > 0 {
                let srcDurSec = CMTimeGetSeconds(sourceDuration)
                let scaledDurSec = srcDurSec / Double(spec.speedScale)
                let scaledDur = CMTimeMakeWithSeconds(scaledDurSec, preferredTimescale: 1000)
                let insertedRange = CMTimeRange(start: insertAt, duration: sourceDuration)
                videoTrack.scaleTimeRange(insertedRange, toDuration: scaledDur)
                audioTrack.scaleTimeRange(insertedRange, toDuration: scaledDur)
            }

            // per-segment 볼륨 — 세그먼트 시작(insertAt)에 볼륨 세팅. setVolume 은 다음 지점까지
            // 상수 유지라 순차 세그먼트별 볼륨이 정확히 적용된다(scale 후에도 시작점은 insertAt 동일).
            audioParams.setVolume(spec.volumeScale, at: insertAt)
        }

        let outputURL = URL(fileURLWithPath: outputPath)
        try? FileManager.default.removeItem(at: outputURL) // 기존 파일 있으면 export 실패 — 선제거.

        guard
            let session = AVAssetExportSession(
                asset: composition, presetName: AVAssetExportPresetHighestQuality)
        else {
            throw ExportError.exportSessionCreationFailed
        }
        session.outputURL = outputURL
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = true // -movflags +faststart 등가 (서버 출력 파리티).
        // 속도 변경 오디오의 피치 보존 — ffmpeg atempo 파리티. spectral = 최고품질 알고리즘.
        session.audioTimePitchAlgorithm = .spectral
        if hasAnyAudio {
            let mix = AVMutableAudioMix()
            mix.inputParameters = [audioParams]
            session.audioMix = mix
        }
        return session
    }

    /// AVURLAsset 의 tracks/duration 를 동기 대기 로드 — 로드 전 `tracks(withMediaType:)` 가 빈
    /// 배열을 반환해 composition 이 0-length 로 만들어지는 결함 차단(buildCompositionPlayer 와 동일 사유).
    private static func loadSynchronously(asset: AVURLAsset, keys: [String]) {
        let sem = DispatchSemaphore(value: 0)
        asset.loadValuesAsynchronously(forKeys: keys) { sem.signal() }
        sem.wait()
    }
}

/// Kotlin `OnDeviceExportCancellable` 구현 — 진행 중 세션/타이머를 취소. thread-safe.
private final class ExportCancellable: NSObject, OnDeviceExportCancellable {
    private let lock = NSLock()
    private var session: AVAssetExportSession?
    private var timer: DispatchSourceTimer?
    private(set) var cancelledFlag = false

    var isCancelled: Bool {
        lock.lock(); defer { lock.unlock() }
        return cancelledFlag
    }

    func attach(session: AVAssetExportSession) {
        lock.lock(); defer { lock.unlock() }
        self.session = session
        if cancelledFlag { session.cancelExport() }
    }

    func attach(timer: DispatchSourceTimer) {
        lock.lock(); defer { lock.unlock() }
        self.timer = timer
        if cancelledFlag { timer.cancel() }
    }

    func cancel() {
        lock.lock(); defer { lock.unlock() }
        cancelledFlag = true
        session?.cancelExport()
        timer?.cancel()
    }
}
