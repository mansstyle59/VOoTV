// Pont minimal et sûr entre la fenêtre et l'application (aucun accès direct à Node.js depuis la page).
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("TVO_DESKTOP", {
  platform: "macos",
  onDeepLink: (cb) => ipcRenderer.on("deep-link", (_e, url) => cb(url)),
  openExternal: (url) => ipcRenderer.send("open-external", url),
});
