/* Démarrage : utilise la dernière version téléchargée de TVO si elle est plus récente que celle installée. */
(function () {
  var V = window.TVO_VERSION || { code: 0 };
  var b = null;
  try { b = JSON.parse(localStorage.getItem("tvo.bundle") || "null"); } catch (e) { b = null; }
  if (b && b.code > V.code && b.js && b.css) {
    var link = document.getElementById("css");
    if (link) link.parentNode.removeChild(link);
    var st = document.createElement("style"); st.textContent = b.css; document.head.appendChild(st);
    window.TVO_VERSION = { code: b.code, name: b.name, repo: V.repo, shell: V.code };
    var s = document.createElement("script"); s.text = b.js; document.body.appendChild(s);
  } else {
    // Ancienne version téléchargée devenue inutile (l'appli installée est plus récente) : on la supprime
    if (b) try { localStorage.removeItem("tvo.bundle"); } catch (e) {}
    var s2 = document.createElement("script"); s2.src = "app.js"; document.body.appendChild(s2);
  }
})();
