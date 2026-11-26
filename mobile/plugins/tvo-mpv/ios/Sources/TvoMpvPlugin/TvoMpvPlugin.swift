import Foundation
import UIKit
import AVFoundation
import AVKit
import Capacitor

/// Pont entre la page web TVO et le lecteur mpv.
/// La vidéo est dessinée sous la page web (rendue transparente) : les commandes TVO restent identiques.
@objc(TvoMpvPlugin)
public class TvoMpvPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "TvoMpvPlugin"
    public let jsName = "TvoMpv"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "close", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "play", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pause", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "seek", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setSpeed", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setFit", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setAudio", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setSub", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setSubScale", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addSubDelay", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getState", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getTracks", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setOrientation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pip", returnType: CAPPluginReturnPromise),
    ]
    private var pipCtl: AVPictureInPictureController?
    private var pipTimer: Timer?
    private var pipActive = false

    private var player: TvoMpvPlayer?
    private var fitTimer: Timer?
    private var saved: (opaque: Bool, bg: UIColor?, scrollBg: UIColor?)?

    override public func load() {
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self, !self.pipActive else { return }   // en incrustation, la vidéo continue
            self.player?.enterBackground()
        }
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.player?.enterForeground()
        }
    }

    @objc func open(_ call: CAPPluginCall) {
        guard let url = call.getString("url"), !url.isEmpty else { call.reject("Adresse vidéo manquante"); return }
        let start = call.getDouble("start") ?? 0
        var headers: [String: String] = [:]
        if let h = call.getObject("headers") { for (k, v) in h { if let s = v as? String { headers[k] = s } } }
        var subs: [[String: String]] = []
        if let arr = call.getArray("subtitles") as? [JSObject] {
            for o in arr { if let u = o["url"] as? String { subs.append(["url": u, "lang": (o["lang"] as? String) ?? "und", "title": (o["title"] as? String) ?? ((o["lang"] as? String) ?? "Sous-titres")]) } }
        }
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.teardown {
                guard let webView = self.bridge?.webView else { call.reject("Vue indisponible"); return }
                guard let p = TvoMpvPlayer(frame: webView.frame) else { call.reject("Lecteur MPV indisponible"); return }
                self.player = p
                p.onEvent = { [weak self] type, data in
                    var d = data; d["type"] = type
                    self?.notifyListeners("mpvEvent", data: d)
                }
                // Page web transparente, vidéo dessous
                self.saved = (webView.isOpaque, webView.backgroundColor, webView.scrollView.backgroundColor)
                webView.isOpaque = false
                webView.backgroundColor = .clear
                webView.scrollView.backgroundColor = .clear
                p.view.frame = webView.frame
                p.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                if let host = webView.superview { host.insertSubview(p.view, belowSubview: webView) }
                else { webView.insertSubview(p.view, at: 0) }
                try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
                try? AVAudioSession.sharedInstance().setActive(true)
                UIApplication.shared.isIdleTimerDisabled = true
                // Après une rotation, la vidéo doit toujours occuper tout l'écran : on recale la vue plusieurs fois par seconde
                self.fitTimer?.invalidate()
                self.fitTimer = Timer.scheduledTimer(withTimeInterval: 0.15, repeats: true) { [weak self] _ in
                    guard let self, let pl = self.player, let wv = self.bridge?.webView else { return }
                    let target = wv.superview?.bounds ?? wv.frame
                    if pl.view.frame != target { pl.view.frame = target }
                    pl.view.setNeedsLayout()
                }
                p.load(url: url, start: start, headers: headers, subtitles: subs)
                call.resolve(["ok": true])
            }
        }
    }

    @objc func close(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.teardown { call.resolve() }
        }
    }

    private func teardown(_ done: @escaping () -> Void) {
        if let c = pipCtl, c.isPictureInPictureActive { c.stopPictureInPicture() }
        pipTimer?.invalidate(); pipTimer = nil; pipCtl = nil; pipActive = false
        guard let p = player else { done(); return }
        player = nil
        fitTimer?.invalidate(); fitTimer = nil
        if let webView = bridge?.webView, let s = saved {
            webView.isOpaque = s.opaque
            webView.backgroundColor = s.bg
            webView.scrollView.backgroundColor = s.scrollBg
        }
        saved = nil
        p.view.removeFromSuperview()
        UIApplication.shared.isIdleTimerDisabled = false
        p.destroy {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            done()
        }
    }

    @objc func play(_ call: CAPPluginCall) { player?.setPause(false); call.resolve() }
    @objc func pause(_ call: CAPPluginCall) { player?.setPause(true); call.resolve() }
    @objc func seek(_ call: CAPPluginCall) { player?.seek(call.getDouble("position") ?? 0); call.resolve() }
    @objc func setSpeed(_ call: CAPPluginCall) { player?.setSpeed(call.getDouble("rate") ?? 1); call.resolve() }
    @objc func setFit(_ call: CAPPluginCall) { player?.setFit(cover: call.getString("mode") == "cover"); call.resolve() }
    @objc func setAudio(_ call: CAPPluginCall) { player?.setAudio(call.getString("id") ?? "auto"); call.resolve() }
    @objc func setSub(_ call: CAPPluginCall) { player?.setSub(call.getString("id") ?? "no"); call.resolve() }
    @objc func setSubScale(_ call: CAPPluginCall) { player?.setSubScale(call.getDouble("scale") ?? 1); call.resolve() }
    @objc func addSubDelay(_ call: CAPPluginCall) { player?.addSubDelay(call.getDouble("delta") ?? 0); call.resolve() }
    @objc func getState(_ call: CAPPluginCall) {
        guard let p = player else { call.resolve(["active": false]); return }
        var s = p.state(); s["active"] = true
        call.resolve(s)
    }
    /// Lecture : passe l'écran en paysage ; en quittant : revient en portrait (la rotation reste libre ensuite).
    @objc func setOrientation(_ call: CAPPluginCall) {
        let mode = call.getString("mode") ?? "auto"
        DispatchQueue.main.async { [weak self] in
            let phone = UIDevice.current.userInterfaceIdiom == .phone
            let mask: UIInterfaceOrientationMask = mode == "landscape" ? .landscape : (mode == "portrait" || phone ? .portrait : .allButUpsideDown)
            // Masque global lu par AppDelegate : iOS ne repasse pas en portrait, même avec le verrou de rotation activé (comme Nuvio)
            (UIApplication.shared.delegate as? NSObject)?.setValue(NSNumber(value: mask.rawValue), forKey: "tvoOrientMask")
            if #available(iOS 16.0, *) {
                if let vc = self?.bridge?.viewController, let scene = vc.view.window?.windowScene {
                    vc.setNeedsUpdateOfSupportedInterfaceOrientations()
                    scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
                }
            } else {
                let o: UIInterfaceOrientation = mode == "landscape" ? .landscapeRight : .portrait
                UIDevice.current.setValue(o.rawValue, forKey: "orientation")
                UIViewController.attemptRotationToDeviceOrientation()
            }
            call.resolve()
        }
    }

    @objc func getTracks(_ call: CAPPluginCall) { call.resolve(["tracks": player?.tracks() ?? []]) }

    // MARK: image dans l'image (MKV, HEVC… lus par mpv)

    @objc func pip(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self, let p = self.player else { call.reject("Aucune vidéo en cours"); return }
            guard #available(iOS 15.0, *), AVPictureInPictureController.isPictureInPictureSupported() else {
                call.reject("Image dans l'image non prise en charge sur cet appareil"); return
            }
            if let c = self.pipCtl, c.isPictureInPictureActive { c.stopPictureInPicture(); call.resolve(["active": false]); return }
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
            try? AVAudioSession.sharedInstance().setActive(true)
            let feeder = p.pipFeeder()
            feeder.layer.frame = p.view.bounds
            feeder.start()
            if self.pipCtl == nil {
                let src = AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer: feeder.layer, playbackDelegate: self)
                let c = AVPictureInPictureController(contentSource: src)
                c.delegate = self
                c.requiresLinearPlayback = false
                self.pipCtl = c
            }
            self.pipTimer?.invalidate()
            self.pipTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
                guard let self, let pl = self.player, pl.hasPip else { return }
                pl.pip.syncClock(); self.pipCtl?.invalidatePlaybackState()
            }
            // On attend les premières images (jusqu'à 4 s) avant de lancer l'incrustation
            var tries = 0
            func attempt() {
                guard let c = self.pipCtl else { call.reject("Image dans l'image indisponible"); return }
                if c.isPictureInPicturePossible && feeder.framesSent > 0 { c.startPictureInPicture(); call.resolve(["active": true]); return }
                tries += 1
                if tries > 40 { feeder.stop(); call.reject("La vidéo n'a pas pu passer en image dans l'image"); return }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { attempt() }
            }
            attempt()
        }
    }
}

@available(iOS 15.0, *)
extension TvoMpvPlugin: AVPictureInPictureSampleBufferPlaybackDelegate, AVPictureInPictureControllerDelegate {
    public func pictureInPictureController(_ c: AVPictureInPictureController, setPlaying playing: Bool) {
        player?.setPause(!playing)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { [weak self] in self?.player?.pip.syncClock(); c.invalidatePlaybackState() }
    }
    public func pictureInPictureControllerTimeRangeForPlayback(_ c: AVPictureInPictureController) -> CMTimeRange {
        let d = (player?.state()["duration"] as? Double) ?? -1
        if d <= 0 { return CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity) }
        return CMTimeRange(start: .zero, duration: CMTime(seconds: d, preferredTimescale: 600))
    }
    public func pictureInPictureControllerIsPlaybackPaused(_ c: AVPictureInPictureController) -> Bool {
        return (player?.state()["paused"] as? Bool) ?? false
    }
    public func pictureInPictureController(_ c: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {
        guard let p = player, p.hasPip else { return }
        let scale = Int(UIScreen.main.nativeScale.rounded())
        if let w = p.videoWidth(), let h = p.videoHeight() {
            let want = max(Int(newRenderSize.width) * scale, 640)
            p.pip.setTargetSize(width: min(w, want), height: Int(Double(h) * Double(min(w, want)) / Double(w)))
        }
    }
    public func pictureInPictureController(_ c: AVPictureInPictureController, skipByInterval skipInterval: CMTime, completion completionHandler: @escaping () -> Void) {
        player?.seekRelative(skipInterval.seconds)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in self?.player?.pip.syncClock(); completionHandler() }
    }
    public func pictureInPictureControllerDidStartPictureInPicture(_ c: AVPictureInPictureController) {
        pipActive = true
        notifyListeners("mpvEvent", data: ["type": "pip", "active": true])
    }
    public func pictureInPictureControllerDidStopPictureInPicture(_ c: AVPictureInPictureController) {
        pipActive = false
        pipTimer?.invalidate(); pipTimer = nil
        if let p = player, p.hasPip { p.pip.stop() }
        player?.enterForeground()
        notifyListeners("mpvEvent", data: ["type": "pip", "active": false])
    }
    public func pictureInPictureController(_ c: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
        pipActive = false
        if let p = player, p.hasPip { p.pip.stop() }
        notifyListeners("mpvEvent", data: ["type": "pip", "active": false, "error": error.localizedDescription])
    }
    public func pictureInPictureController(_ c: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        completionHandler(true)
    }
}
