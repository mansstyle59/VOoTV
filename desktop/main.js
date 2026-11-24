// TVO pour macOS : même application que sur téléphone (même code, mêmes comptes, même synchronisation).
const { app, BrowserWindow, session, shell, ipcMain, Menu, nativeTheme } = require("electron");
const path = require("path");

let win = null;
// Bande-annonce VF lancée toute seule sur la fiche d'un film (comme sur une plateforme de streaming)
app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
let pendingLink = null;

// Une seule fenêtre TVO à la fois ; les liens tvo:// (connexion d'une TV par QR, etc.) arrivent ici
if (!app.requestSingleInstanceLock()) app.quit();
app.setAsDefaultProtocolClient("tvo");
app.on("open-url", (e, url) => { e.preventDefault(); if (win) win.webContents.send("deep-link", url); else pendingLink = url; });
app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

function createWindow() {
  nativeTheme.themeSource = "dark";
  win = new BrowserWindow({
    width: 1360, height: 860, minWidth: 900, minHeight: 600,
    backgroundColor: "#0a0f1e", title: "TVO", show: false,
    titleBarStyle: "hiddenInset", trafficLightPosition: { x: 16, y: 18 },
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true, nodeIntegration: false, sandbox: true,
      webSecurity: true, spellcheck: false, devTools: !app.isPackaged,
    },
  });
  win.once("ready-to-show", () => win.show());
  win.loadFile(path.join(__dirname, "www", "index.html"));
  win.webContents.on("did-finish-load", () => { if (pendingLink) { win.webContents.send("deep-link", pendingLink); pendingLink = null; } });
  // Les liens externes s'ouvrent dans le navigateur, jamais dans l'appli
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: "deny" }; });
  win.webContents.on("will-navigate", (e, url) => { if (!url.startsWith("file://")) { e.preventDefault(); if (/^https?:/.test(url)) shell.openExternal(url); } });
}

ipcMain.on("open-external", (_e, url) => { if (/^(https?|vlc|iina):/.test(String(url))) shell.openExternal(url); });

app.whenReady().then(() => {
  // Les add-ons et services n'envoient pas toujours d'en-têtes CORS : on les ajoute pour les réponses reçues,
  // comme le fait l'appli native sur téléphone (sans désactiver la sécurité du navigateur).
  session.defaultSession.webRequest.onHeadersReceived((details, cb) => {
    const h = details.responseHeaders || {};
    for (const k of Object.keys(h)) if (/^access-control-allow-(origin|headers|methods)$/i.test(k)) delete h[k];
    h["Access-Control-Allow-Origin"] = ["*"];
    h["Access-Control-Allow-Headers"] = ["*"];
    h["Access-Control-Allow-Methods"] = ["GET, POST, HEAD, OPTIONS"];
    cb({ responseHeaders: h, statusLine: details.method === "OPTIONS" ? "HTTP/1.1 200 OK" : details.statusLine });
  });
  // Seules les autorisations utiles sont accordées (plein écran, lecture auto) ; tout le reste est refusé
  session.defaultSession.setPermissionRequestHandler((_wc, perm, cb) => cb(["fullscreen", "media", "clipboard-sanitized-write"].includes(perm)));
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { role: "appMenu" }, { role: "editMenu" },
    { label: "Présentation", submenu: [{ role: "reload", label: "Recharger" }, { role: "togglefullscreen", label: "Plein écran" }, { type: "separator" }, { role: "resetZoom" }, { role: "zoomIn" }, { role: "zoomOut" }] },
    { role: "windowMenu" },
  ]));
  createWindow();
  app.on("activate", () => { if (!BrowserWindow.getAllWindows().length) createWindow(); });
});
app.on("window-all-closed", () => app.quit());
