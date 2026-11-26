import Foundation
import UIKit
import AVFoundation
import AVKit
import Accelerate
import Libmpv

/// Image dans l'image pour le lecteur mpv (MKV, HEVC…).
/// iOS ne sait pas incruster une couche Metal : pendant l'incrustation, mpv dessine chaque image dans un tampon
/// (rendu logiciel « libmpv ») qu'on transmet à une AVSampleBufferDisplayLayer, la seule couche qu'iOS sait incruster.
/// En revenant dans l'appli, mpv reprend son affichage habituel (Metal).
final class TvoMpvPipFeeder {
    let layer = AVSampleBufferDisplayLayer()
    private weak var player: TvoMpvPlayer?
    private var renderCtx: OpaquePointer?
    private let queue = DispatchQueue(label: "tvo.mpv.pip", qos: .userInteractive)
    private var pool: CVPixelBufferPool?
    private var poolSize = (w: 0, h: 0)
    private var target = (w: 1280, h: 720)
    private let swFormat = strdup("bgr0")!
    private let swApi = strdup("sw")!
    private(set) var active = false
    private(set) var framesSent = 0

    init(player: TvoMpvPlayer) {
        self.player = player
        layer.videoGravity = .resizeAspect
        layer.backgroundColor = UIColor.black.cgColor
        layer.isHidden = true
        var tb: CMTimebase?
        CMTimebaseCreateWithSourceClock(allocator: kCFAllocatorDefault, sourceClock: CMClockGetHostTimeClock(), timebaseOut: &tb)
        if let tb { layer.controlTimebase = tb }
    }

    deinit { free(swFormat); free(swApi) }

    /// Taille de rendu : celle de la vidéo, limitée à 1280 px de large (assez pour la petite fenêtre, léger pour le téléphone).
    func setTargetSize(width: Int, height: Int) {
        queue.async {
            guard width > 16, height > 16 else { return }
            var w = min(width, 1280); var h = Int((Double(height) * Double(w) / Double(width)).rounded())
            w -= w % 2; h -= h % 2
            self.target = (w, h)
        }
    }

    /// Démarre l'envoi des images (à appeler avant l'incrustation).
    func start() {
        guard let p = player, let mpv = p.mpv, !active else { return }
        active = true; framesSent = 0
        if renderCtx == nil {
            var rc: OpaquePointer?
            var params = [mpv_render_param(type: MPV_RENDER_PARAM_API_TYPE, data: UnsafeMutableRawPointer(swApi)),
                          mpv_render_param(type: MPV_RENDER_PARAM_INVALID, data: nil)]
            if mpv_render_context_create(&rc, mpv, &params) >= 0, let rc {
                renderCtx = rc
                mpv_render_context_set_update_callback(rc, { raw in
                    guard let raw else { return }
                    let me = Unmanaged<TvoMpvPipFeeder>.fromOpaque(raw).takeUnretainedValue()
                    me.queue.async { me.renderIfNeeded() }
                }, Unmanaged.passUnretained(self).toOpaque())
            }
        }
        if let w = p.videoWidth(), let h = p.videoHeight() { setTargetSize(width: w, height: h) }
        layer.flush()
        layer.isHidden = false
        // Décodage matériel avec copie (le rendu logiciel ne lit pas les images restées dans la puce vidéo), puis sortie « libmpv »
        p.setOptionString("hwdec", "videotoolbox-copy")
        p.setOptionString("vo", "libmpv")
        syncClock()
    }

    /// Arrête l'envoi et rend la main à l'affichage Metal.
    func stop() {
        guard active, let p = player else { return }
        active = false
        p.setOptionString("vo", "gpu-next")
        p.setOptionString("hwdec", "videotoolbox")
        layer.isHidden = true
        layer.flush()
    }

    /// Horloge de la fenêtre incrustée (barre de progression et lecture/pause d'iOS).
    func syncClock() {
        guard let p = player, let tb = layer.controlTimebase else { return }
        let s = p.state()
        let t = (s["time"] as? Double) ?? 0
        let paused = (s["paused"] as? Bool) ?? false
        CMTimebaseSetTime(tb, time: CMTime(seconds: t, preferredTimescale: 600))
        CMTimebaseSetRate(tb, rate: paused ? 0 : ((s["speed"] as? Double) ?? 1))
    }

    /// Libère le contexte de rendu (avant la destruction de mpv).
    func release() {
        queue.sync {
            active = false
            if let rc = renderCtx { mpv_render_context_set_update_callback(rc, nil, nil); mpv_render_context_free(rc) }
            renderCtx = nil
        }
    }

    private func renderIfNeeded() {
        guard let rc = renderCtx else { return }
        let flags = mpv_render_context_update(rc)
        guard active, flags & UInt64(MPV_RENDER_UPDATE_FRAME.rawValue) != 0 else { return }
        let (w, h) = target
        if pool == nil || poolSize.w != w || poolSize.h != h {
            let attrs: [CFString: Any] = [kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey: w,
                                          kCVPixelBufferHeightKey: h, kCVPixelBufferIOSurfacePropertiesKey: [String: Any]()]
            var np: CVPixelBufferPool?
            CVPixelBufferPoolCreate(kCFAllocatorDefault, nil, attrs as CFDictionary, &np)
            pool = np; poolSize = (w, h)
        }
        guard let pool else { return }
        var pbOut: CVPixelBuffer?
        guard CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &pbOut) == kCVReturnSuccess, let pb = pbOut else { return }
        CVPixelBufferLockBaseAddress(pb, [])
        guard let base = CVPixelBufferGetBaseAddress(pb) else { CVPixelBufferUnlockBaseAddress(pb, []); return }
        var size: [Int32] = [Int32(w), Int32(h)]
        var stride = CVPixelBufferGetBytesPerRow(pb)
        size.withUnsafeMutableBytes { sz in
            withUnsafeMutablePointer(to: &stride) { st in
                var params = [
                    mpv_render_param(type: MPV_RENDER_PARAM_SW_SIZE, data: sz.baseAddress),
                    mpv_render_param(type: MPV_RENDER_PARAM_SW_FORMAT, data: UnsafeMutableRawPointer(swFormat)),
                    mpv_render_param(type: MPV_RENDER_PARAM_SW_STRIDE, data: UnsafeMutableRawPointer(st)),
                    mpv_render_param(type: MPV_RENDER_PARAM_SW_POINTER, data: base),
                    mpv_render_param(type: MPV_RENDER_PARAM_INVALID, data: nil),
                ]
                _ = mpv_render_context_render(rc, &params)
            }
        }
        // « bgr0 » laisse l'octet de transparence à 0 : on le met à 255 (image opaque)
        var buf = vImage_Buffer(data: base, height: vImagePixelCount(h), width: vImagePixelCount(w), rowBytes: stride)
        vImageOverwriteChannelsWithScalar_ARGB8888(255, &buf, &buf, 0x1, vImage_Flags(kvImageNoFlags))
        CVPixelBufferUnlockBaseAddress(pb, [])

        var fmt: CMVideoFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(allocator: kCFAllocatorDefault, imageBuffer: pb, formatDescriptionOut: &fmt)
        guard let fmt else { return }
        var timing = CMSampleTimingInfo(duration: .invalid, presentationTimeStamp: .zero, decodeTimeStamp: .invalid)
        if let tb = layer.controlTimebase { timing.presentationTimeStamp = CMTimebaseGetTime(tb) }
        var sbOut: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(allocator: kCFAllocatorDefault, imageBuffer: pb, formatDescription: fmt, sampleTiming: &timing, sampleBufferOut: &sbOut)
        guard let sb = sbOut else { return }
        if let arr = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: true), CFArrayGetCount(arr) > 0 {
            let dict = unsafeBitCast(CFArrayGetValueAtIndex(arr, 0), to: CFMutableDictionary.self)
            CFDictionarySetValue(dict, Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                                 Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
        }
        framesSent += 1
        DispatchQueue.main.async { [weak self] in
            guard let self, self.active else { return }
            if self.layer.status == .failed { self.layer.flush() }
            self.layer.enqueue(sb)
        }
    }
}
