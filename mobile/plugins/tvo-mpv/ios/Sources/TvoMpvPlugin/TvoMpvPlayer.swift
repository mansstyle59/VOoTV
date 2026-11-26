import Foundation
import UIKit
import Libmpv

/// Couche Metal dans laquelle mpv dessine la vidéo (repris du lecteur MPVKit / Nuvio).
final class TvoMetalLayer: CAMetalLayer {
    // MoltenVK peut forcer une taille 1x1 : on l'ignore (sinon image figée / clignotements)
    override var drawableSize: CGSize {
        get { return super.drawableSize }
        set { if Int(newValue.width) > 1 && Int(newValue.height) > 1 { super.drawableSize = newValue } }
    }
}

/// Vue noire plein écran placée sous la page web (les commandes TVO restent par-dessus).
final class TvoMpvView: UIView {
    let metalLayer = TvoMetalLayer()
    weak var pipLayer: CALayer?
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        isUserInteractionEnabled = false
        metalLayer.contentsScale = UIScreen.main.nativeScale
        metalLayer.framebufferOnly = true
        metalLayer.backgroundColor = UIColor.black.cgColor
        metalLayer.contentsGravity = .resize
        autoresizesSubviews = true
        layer.addSublayer(metalLayer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) non utilisé") }
    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin(); CATransaction.setDisableActions(true)
        metalLayer.frame = bounds
        pipLayer?.frame = bounds
        let s = metalLayer.contentsScale
        if bounds.width > 1 && bounds.height > 1 {
            metalLayer.drawableSize = CGSize(width: (bounds.width * s).rounded(), height: (bounds.height * s).rounded())
        }
        CATransaction.commit()
    }
}

/// Lecteur mpv : lit MKV, AVI, HEVC, DTS, TrueHD, sous-titres intégrés…
final class TvoMpvPlayer {
    private(set) var mpv: OpaquePointer?
    let view: TvoMpvView
    private let eventQueue = DispatchQueue(label: "tvo.mpv.events", qos: .userInitiated)
    private let workQueue = DispatchQueue(label: "tvo.mpv.work", qos: .userInitiated)
    var onEvent: ((String, [String: Any]) -> Void)?
    private var pendingSubs: [[String: String]] = []
    private var fileLoaded = false

    init?(frame: CGRect) {
        view = TvoMpvView(frame: frame)
        guard let ctx = mpv_create() else { return nil }
        mpv = ctx
        mpv_request_log_messages(ctx, "no")
        var layerPointer = Int64(Int(bitPattern: Unmanaged.passUnretained(view.metalLayer).toOpaque()))
        mpv_set_option(ctx, "wid", MPV_FORMAT_INT64, &layerPointer)
        let options: [(String, String)] = [
            ("vo", "gpu-next"), ("gpu-api", "vulkan"), ("gpu-context", "moltenvk"),
            ("hwdec", "videotoolbox"), ("hwdec-software-fallback", "300"),
            ("ao", "audiounit"), ("audio-channels", "auto"), ("audio-fallback-to-null", "yes"),
            ("vulkan-swap-mode", "fifo"), ("vulkan-queue-count", "1"), ("vulkan-async-compute", "no"),
            ("vulkan-async-transfer", "no"), ("vulkan-disable-interop", "yes"), ("video-rotate", "no"),
            ("keep-open", "yes"), ("idle", "yes"), ("input-default-bindings", "no"), ("input-vo-keyboard", "no"),
            ("alang", "fr,fre,fra,fr-FR,vff,vf,en,eng"), ("slang", "fr,fre,fra"),
            ("subs-match-os-language", "yes"), ("subs-fallback", "yes"), ("sub-ass-override", "no"),
            ("cache", "yes"), ("demuxer-max-bytes", "150MiB"), ("demuxer-max-back-bytes", "50MiB"),
            ("demuxer-readahead-secs", "30"), ("network-timeout", "30"), ("tls-verify", "no"),
            ("user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"),
        ]
        for (k, v) in options { mpv_set_option_string(ctx, k, v) }
        guard mpv_initialize(ctx) >= 0 else { mpv_terminate_destroy(ctx); mpv = nil; return nil }
        mpv_set_wakeup_callback(ctx, { raw in
            guard let raw else { return }
            let me = Unmanaged<TvoMpvPlayer>.fromOpaque(raw).takeUnretainedValue()
            me.readEvents()
        }, Unmanaged.passUnretained(self).toOpaque())
    }

    // MARK: lecture

    func load(url: String, start: Double, headers: [String: String], subtitles: [[String: String]]) {
        guard let mpv else { return }
        if start > 1 { mpv_set_property_string(mpv, "start", String(format: "%.1f", start)) }
        let headerList = headers.filter { $0.key.lowercased() != "user-agent" }
            .map { "\($0.key): \($0.value.replacingOccurrences(of: ",", with: "\\,"))" }
        if !headerList.isEmpty { mpv_set_property_string(mpv, "http-header-fields", headerList.joined(separator: ",")) }
        if let ua = headers.first(where: { $0.key.lowercased() == "user-agent" })?.value { mpv_set_property_string(mpv, "user-agent", ua) }
        pendingSubs = subtitles
        command(["loadfile", url, "replace"])
    }

    func setPause(_ p: Bool) { setFlag("pause", p) }
    func seek(_ pos: Double) { command(["seek", String(format: "%.2f", max(0, pos)), "absolute"]) }
    func setSpeed(_ r: Double) { setDouble("speed", r) }
    func setFit(cover: Bool) { setDouble("panscan", cover ? 1.0 : 0.0) }
    func setSubScale(_ s: Double) { setDouble("sub-scale", s) }
    func addSubDelay(_ d: Double) { setDouble("sub-delay", (getDouble("sub-delay") ?? 0) + d) }
    func setAudio(_ id: String) { setString("aid", id) }
    func setSub(_ id: String) { setString("sid", id) }

    func state() -> [String: Any] {
        return [
            "time": getDouble("time-pos") ?? 0,
            "duration": getDouble("duration") ?? -1,
            "paused": getFlag("pause"),
            "buffering": getFlag("paused-for-cache") || getFlag("seeking") || !fileLoaded,
            "eof": getFlag("eof-reached"),
            "speed": getDouble("speed") ?? 1,
        ]
    }

    func tracks() -> [[String: Any]] {
        var out: [[String: Any]] = []
        let n = getInt("track-list/count")
        guard n > 0 else { return out }
        for i in 0..<n {
            let p = "track-list/\(i)/"
            out.append([
                "type": getString(p + "type") ?? "",
                "id": getInt(p + "id"),
                "lang": getString(p + "lang") ?? "",
                "title": getString(p + "title") ?? "",
                "codec": getString(p + "codec") ?? "",
                "channels": getInt(p + "demux-channel-count"),
                "selected": getFlag(p + "selected"),
                "external": getFlag(p + "external"),
            ])
        }
        return out
    }

    func videoWidth() -> Int? { let v = getInt("video-params/dw"); return v > 0 ? v : nil }
    func videoHeight() -> Int? { let v = getInt("video-params/dh"); return v > 0 ? v : nil }
    func setOptionString(_ name: String, _ value: String) { setString(name, value) }
    func seekRelative(_ d: Double) { command(["seek", String(format: "%.2f", d), "relative"]) }

    /// Image dans l'image (créée à la demande)
    private(set) lazy var pip: TvoMpvPipFeeder = {
        let f = TvoMpvPipFeeder(player: self)
        f.layer.frame = view.bounds
        view.layer.addSublayer(f.layer)
        view.pipLayer = f.layer
        return f
    }()
    private(set) var hasPip = false
    func pipFeeder() -> TvoMpvPipFeeder { hasPip = true; return pip }

    func enterBackground() { setString("vid", "no") }
    func enterForeground() { setString("vid", "auto") }

    /// Arrêt propre : mpv est détruit en arrière-plan, la vue reste en vie jusqu'à la fin.
    func destroy(completion: @escaping () -> Void) {
        guard let ctx = mpv else { completion(); return }
        mpv = nil
        onEvent = nil
        mpv_set_wakeup_callback(ctx, nil, nil)
        let keep = view
        if hasPip { pip.release() }
        eventQueue.async {
            mpv_terminate_destroy(ctx)
            DispatchQueue.main.async { _ = keep; completion() }
        }
    }

    // MARK: événements

    private func readEvents() {
        eventQueue.async { [weak self] in
            guard let self else { return }
            while let ctx = self.mpv, let ev = mpv_wait_event(ctx, 0) {
                let id = ev.pointee.event_id
                if id == MPV_EVENT_NONE { break }
                switch id {
                case MPV_EVENT_FILE_LOADED:
                    self.fileLoaded = true
                    let subs = self.pendingSubs; self.pendingSubs = []
                    self.workQueue.async { [weak self] in
                        guard let self else { return }
                        for s in subs {
                            guard let u = s["url"], !u.isEmpty else { continue }
                            self.command(["sub-add", u, "auto", s["title"] ?? (s["lang"] ?? "Sous-titres"), s["lang"] ?? "und"])
                        }
                        self.emit("tracks", ["tracks": self.tracks()])
                    }
                    self.emit("loaded", ["tracks": self.tracks()])
                case MPV_EVENT_END_FILE:
                    if let data = ev.pointee.data {
                        let end = data.assumingMemoryBound(to: mpv_event_end_file.self).pointee
                        if end.reason == MPV_END_FILE_REASON_ERROR {
                            self.emit("error", ["message": String(cString: mpv_error_string(end.error))])
                        } else if end.reason == MPV_END_FILE_REASON_EOF {
                            self.emit("ended", [:])
                        }
                    }
                case MPV_EVENT_SHUTDOWN:
                    return
                default:
                    break
                }
            }
        }
    }

    private func emit(_ type: String, _ data: [String: Any]) {
        DispatchQueue.main.async { [weak self] in self?.onEvent?(type, data) }
    }

    // MARK: accès aux propriétés

    private func command(_ args: [String]) {
        guard let mpv else { return }
        var cargs: [UnsafePointer<CChar>?] = args.map { UnsafePointer(strdup($0)) }
        cargs.append(nil)
        defer { for p in cargs where p != nil { free(UnsafeMutablePointer(mutating: p!)) } }
        mpv_command(mpv, &cargs)
    }
    private func getDouble(_ name: String) -> Double? {
        guard let mpv else { return nil }
        var v = Double()
        return mpv_get_property(mpv, name, MPV_FORMAT_DOUBLE, &v) >= 0 ? v : nil
    }
    private func getInt(_ name: String) -> Int {
        guard let mpv else { return 0 }
        var v = Int64()
        return mpv_get_property(mpv, name, MPV_FORMAT_INT64, &v) >= 0 ? Int(v) : 0
    }
    private func getFlag(_ name: String) -> Bool {
        guard let mpv else { return false }
        var v = Int32()
        return mpv_get_property(mpv, name, MPV_FORMAT_FLAG, &v) >= 0 && v != 0
    }
    private func getString(_ name: String) -> String? {
        guard let mpv, let c = mpv_get_property_string(mpv, name) else { return nil }
        defer { mpv_free(c) }
        return String(cString: c)
    }
    private func setFlag(_ name: String, _ on: Bool) {
        guard let mpv else { return }
        var v: Int32 = on ? 1 : 0
        mpv_set_property(mpv, name, MPV_FORMAT_FLAG, &v)
    }
    private func setDouble(_ name: String, _ value: Double) {
        guard let mpv else { return }
        var v = value
        mpv_set_property(mpv, name, MPV_FORMAT_DOUBLE, &v)
    }
    private func setString(_ name: String, _ value: String) {
        guard let mpv else { return }
        mpv_set_property_string(mpv, name, value)
    }
}
