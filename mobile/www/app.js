/* TVO mobile — iPhone & téléphone Android.
   Même logique que l'appli TV : profils, add-ons compatibles Stremio, Ma liste, reprise de lecture. */
(() => {
  "use strict";

  // ------------------------------------------------------------------ helpers
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const enc = encodeURIComponent;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const isNative = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
  const VERSION = window.TVO_VERSION || { code: 0, name: "dev", repo: "mansstyle59/VOoTV" };

  const ICON = {
    home: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3 2 11h3v9h6v-6h2v6h6v-9h3z"/></svg>',
    movie: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm6 4v8l6-4z"/></svg>',
    series: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 6h18v12H3zm2 2v8h14V8zM8 2l4 3 4-3 1 1-3 3h-4L7 3z"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
    list: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21s-7.5-4.6-9.5-9.2C1 8.3 3.2 5 6.6 5c2 0 3.4 1.1 4.4 2.4C12 6.1 13.4 5 15.4 5 18.8 5 21 8.3 19.5 11.8 17.5 16.4 12 21 12 21z"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.4 13a7.7 7.7 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-1.7-1L15 3.5h-4L10.7 6a7.6 7.6 0 0 0-1.7 1l-2.4-1-2 3.4L4.6 11a7.7 7.7 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 1.7 1l.3 2.5h4l.3-2.5a7.6 7.6 0 0 0 1.7-1l2.4 1 2-3.4zM12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7z"/></svg>',
    play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 4v16l13-8z"/></svg>',
    plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10"/></svg>',
    back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m15 5-7 7 7 7"/></svg>',
    close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>',
    edit: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.2V21h3.8l11-11-3.8-3.8zM20.7 7a1 1 0 0 0 0-1.4l-2.3-2.3a1 1 0 0 0-1.4 0l-1.8 1.8 3.8 3.8z"/></svg>',
    up: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 15 6-6 6 6"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 3h6l1 2h4v2H4V5h4zm-3 6h12l-1 12H7z"/></svg>',
  };
  const LOGO = '<svg class="logo" viewBox="0 0 102 40" aria-label="TVO"><text x="0" y="31" font-size="34" font-weight="800" fill="#fff" font-family="-apple-system,Segoe UI,Roboto,sans-serif">TV</text><circle cx="83" cy="20" r="15" fill="none" stroke="#fff" stroke-width="4.5"/><path d="M78 12.5v15l12-7.5z" fill="#fff"/></svg>';

  // ------------------------------------------------------------------ storage
  const LS = {
    get(k, d) { try { const v = localStorage.getItem("tvo." + k); return v ? JSON.parse(v) : d; } catch { return d; } },
    set(k, v) { try { localStorage.setItem("tvo." + k, JSON.stringify(v)); } catch { /* quota */ } },
    del(k) { localStorage.removeItem("tvo." + k); },
  };

  const GENRES = ["Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy",
    "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western"];
  const HOME_GENRES = ["Action", "Comedy", "Animation", "Sci-Fi", "Horror", "Thriller", "Drama", "Documentary"];
  const GENRE_FR = { Action: "Action", Adventure: "Aventure", Animation: "Animation", Biography: "Biographie", Comedy: "Comédie",
    Crime: "Policier", Documentary: "Documentaire", Drama: "Drame", Family: "Famille", Fantasy: "Fantastique", History: "Histoire",
    Horror: "Horreur", Music: "Musique", Mystery: "Mystère", Romance: "Romance", "Sci-Fi": "Science-fiction", Sport: "Sport",
    Thriller: "Thriller", War: "Guerre", Western: "Western", "Reality-TV": "Téléréalité", News: "Actualités" };
  const genreLabel = (g) => GENRE_FR[g] || g;
  const typeLabel = (t, plural = true) => ({ movie: plural ? "Films" : "Film", series: plural ? "Séries" : "Série", anime: "Anime", tv: "TV", channel: "Chaînes" }[t] || t);
  const catalogTitle = (type, name) => {
    const t = typeLabel(type);
    switch ((name || "").toLowerCase()) {
      case "popular": case "top": return `${t} populaires`;
      case "featured": return `${t} les mieux notés`;
      case "new": return `Nouveautés · ${t}`;
      default: return `${name} · ${t}`;
    }
  };
  const COLORS = ["#8B5CF6", "#22D3EE", "#F97316", "#10B981", "#EC4899", "#EAB308", "#3B82F6", "#EF4444"];

  const CINEMETA = {
    url: "https://v3-cinemeta.strem.io",
    manifest: {
      id: "com.linvo.cinemeta", name: "Cinemeta", version: "3.0", description: "Catalogues et fiches des films et séries",
      types: ["movie", "series"], resources: [{ name: "catalog" }, { name: "meta" }], idPrefixes: ["tt"],
      catalogs: [
        { type: "movie", id: "top", name: "Popular", extra: [{ name: "genre", options: GENRES }, { name: "search" }, { name: "skip" }] },
        { type: "series", id: "top", name: "Popular", extra: [{ name: "genre", options: GENRES }, { name: "search" }, { name: "skip" }] },
        { type: "movie", id: "imdbRating", name: "Featured", extra: [{ name: "genre", options: GENRES }, { name: "skip" }] },
        { type: "series", id: "imdbRating", name: "Featured", extra: [{ name: "genre", options: GENRES }, { name: "skip" }] },
      ],
    },
  };
  const SUGGESTED = [["https://opensubtitles-v3.strem.io/manifest.json", "Sous-titres OpenSubtitles (gratuit)"]];

  const S = {
    profiles: LS.get("profiles", []),
    addons: LS.get("addons", [CINEMETA]),
    profile: null,
    lib: [],
    prog: [],
    player: LS.get("player", "tvo"), // tvo | vlc | infuse
  };

  function selectProfile(p) {
    S.profile = p; LS.set("current", p.id);
    S.lib = LS.get("lib." + p.id, []);
    S.prog = LS.get("prog." + p.id, []);
  }
  const saveProfiles = () => LS.set("profiles", S.profiles);
  const saveAddons = () => LS.set("addons", S.addons);
  const saveLib = () => S.profile && LS.set("lib." + S.profile.id, S.lib);
  const saveProg = () => S.profile && LS.set("prog." + S.profile.id, S.prog);
  const inLib = (id) => S.lib.some((x) => x.id === id);
  function toggleLib(m) {
    if (inLib(m.id)) S.lib = S.lib.filter((x) => x.id !== m.id);
    else S.lib.unshift({ id: m.id, type: m.type, name: m.name, poster: m.poster, background: m.background, addedAt: Date.now() });
    saveLib();
  }
  const fraction = (p) => (p && p.duration > 0 ? Math.min(1, p.position / p.duration) : 0);
  const finished = (p) => fraction(p) >= 0.92;
  function saveProgress(p) {
    S.prog = [p, ...S.prog.filter((x) => x.videoId !== p.videoId)].slice(0, 400);
    saveProg();
  }
  function continueWatching() {
    const seen = new Set();
    return [...S.prog].sort((a, b) => b.updatedAt - a.updatedAt)
      .filter((p) => (seen.has(p.metaId) ? false : seen.add(p.metaId)))
      .filter((p) => fraction(p) > 0.02 && fraction(p) < 0.92).slice(0, 20);
  }

  // ------------------------------------------------------------------ add-ons (protocole Stremio)
  const cache = new Map();
  const metaCache = new Map();

  async function getJson(url, timeout = 20000) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), timeout);
    try {
      const r = await fetch(url, { signal: ctl.signal, headers: { Accept: "application/json" } });
      if (!r.ok) throw new Error("HTTP " + r.status);
      const data = await r.json();
      return typeof data === "string" ? JSON.parse(data) : data;
    } finally { clearTimeout(t); }
  }

  function normalize(input) {
    let u = String(input || "").trim();
    if (u.startsWith("stremio://")) u = "https://" + u.slice(10);
    if (!/^https?:\/\//.test(u)) u = "https://" + u;
    return u.split("/manifest.json")[0].replace(/\/+$/, "");
  }

  function parseManifest(o) {
    const arr = (v) => (Array.isArray(v) ? v : v ? [v] : []);
    return {
      id: o.id || "addon", name: o.name || "Add-on", version: o.version || "", description: o.description || "", logo: o.logo || null,
      types: arr(o.types),
      resources: arr(o.resources).map((r) => (typeof r === "string" ? { name: r } : r && r.name ? { name: r.name, types: r.types, idPrefixes: r.idPrefixes } : null)).filter(Boolean),
      catalogs: arr(o.catalogs).filter((c) => c && c.type && c.id).map((c) => ({
        type: c.type, id: c.id, name: c.name || c.id,
        extra: arr(c.extra).filter((e) => e && e.name).map((e) => ({ name: e.name, isRequired: !!e.isRequired, options: arr(e.options) })),
        extraRequired: arr(c.extraRequired), extraSupported: arr(c.extraSupported),
      })),
      idPrefixes: o.idPrefixes && o.idPrefixes.length ? o.idPrefixes : null,
    };
  }
  async function installAddon(input) {
    const base = normalize(input);
    return { url: base, manifest: parseManifest(await getJson(base + "/manifest.json")) };
  }
  const catRequires = (c) => c.extra.some((e) => e.isRequired) || (c.extraRequired || []).length > 0;
  const catSupports = (c, n) => c.extra.some((e) => e.name === n) || (c.extraSupported || []).includes(n);
  const catOnlyRequires = (c, n) => [...c.extra.filter((e) => e.isRequired).map((e) => e.name), ...(c.extraRequired || [])].every((x) => x === n);
  const catOptions = (c, n) => (c.extra.find((e) => e.name === n) || {}).options || [];

  function supports(a, resource, type, id) {
    const m = a.manifest;
    const r = m.resources.find((x) => x.name === resource);
    if (!r) return false;
    const types = r.types || m.types;
    if (types && types.length && !types.includes(type)) return false;
    const pre = r.idPrefixes || m.idPrefixes;
    if (id && pre && pre.length && !pre.some((p) => id.startsWith(p))) return false;
    return true;
  }

  function preview(o, type) {
    const id = o.id || o.imdb_id; if (!id) return null;
    return {
      id, type: o.type || type, name: o.name || o.title || "", poster: o.poster || null, background: o.background || null,
      logo: o.logo || null, description: o.description || null, releaseInfo: o.releaseInfo || (o.year ? String(o.year) : null),
      imdbRating: o.imdbRating || null, genres: o.genres || o.genre || [],
    };
  }
  async function catalog(a, c, extra = {}) {
    const keys = Object.keys(extra);
    const ex = keys.length ? "/" + keys.map((k) => `${k}=${enc(extra[k])}`).join("&") : "";
    const url = `${a.url}/catalog/${c.type}/${enc(c.id)}${ex}.json`;
    if (cache.has(url)) return cache.get(url);
    const d = await getJson(url);
    const seen = new Set();
    const list = (d.metas || []).map((m) => preview(m, c.type)).filter((m) => m && !seen.has(m.id) && seen.add(m.id));
    cache.set(url, list);
    return list;
  }
  async function meta(type, id) {
    const k = type + "/" + id;
    if (metaCache.has(k)) return metaCache.get(k);
    for (const a of S.addons.filter((x) => supports(x, "meta", type, id))) {
      try {
        const d = await getJson(`${a.url}/meta/${type}/${enc(id)}.json`);
        const o = d && d.meta; if (!o) continue;
        const p = preview(o, type); if (!p) continue;
        const videos = (o.videos || []).filter((v) => v && v.id).map((v) => ({
          id: v.id, title: v.title || v.name || "", season: v.season ?? null, episode: v.episode ?? v.number ?? null,
          released: v.released || null, thumbnail: v.thumbnail || null, overview: v.overview || v.description || null,
        }));
        const m = { ...p, runtime: o.runtime || null, videos, cast: (o.cast || []).slice(0, 6), director: o.director || [],
          defaultVideoId: (o.behaviorHints && o.behaviorHints.defaultVideoId) || null };
        m.episodes = videos.filter((v) => v.season != null).sort((x, y) => ((x.season || 1e9) - (y.season || 1e9)) || ((x.episode || 0) - (y.episode || 0)));
        metaCache.set(k, m);
        return m;
      } catch { /* next add-on */ }
    }
    return null;
  }
  const quality = (t) => {
    t = (t || "").toLowerCase();
    if (/2160|4k|uhd/.test(t)) return 4;
    if (/1080|fhd/.test(t)) return 3;
    if (/720/.test(t)) return 2;
    if (/480|\bsd\b/.test(t)) return 1;
    return 0;
  };
  const qLabel = (q) => ["Auto", "SD", "720p", "1080p", "4K"][q];
  function parseStream(o, addonName) {
    const url = o.url || null;
    const ext = o.externalUrl || (o.ytId ? "https://www.youtube.com/watch?v=" + o.ytId : null);
    if (!url && !ext && !o.infoHash) return null;
    const hints = o.behaviorHints || {};
    const headers = (hints.proxyHeaders && hints.proxyHeaders.request) || {};
    const name = o.name || addonName;
    const title = o.title || o.description || "";
    return { addonName, name, title, url, externalUrl: ext, infoHash: o.infoHash || null, headers,
      needsHeaders: Object.keys(headers).length > 0, subtitles: (o.subtitles || []).filter((s) => s && s.url).map((s) => ({ url: s.url, lang: s.lang || "und" })),
      bingeGroup: hints.bingeGroup || null, quality: quality(name + " " + title) };
  }
  function streams(type, videoId, onAddon) {
    const list = S.addons.filter((a) => supports(a, "stream", type, videoId));
    return { total: list.length, done: Promise.all(list.map(async (a) => {
      let items = [];
      try { items = ((await getJson(`${a.url}/stream/${type}/${enc(videoId)}.json`, 25000)).streams || []).map((s) => parseStream(s, a.manifest.name)).filter(Boolean); }
      catch { /* ignore */ }
      onAddon && onAddon(items, a);
      return items;
    })).then((x) => x.flat()) };
  }
  async function subtitles(type, videoId) {
    const list = S.addons.filter((a) => supports(a, "subtitles", type, videoId));
    const res = await Promise.all(list.map(async (a) => {
      try { return ((await getJson(`${a.url}/subtitles/${type}/${enc(videoId)}.json`, 6000)).subtitles || []).filter((s) => s.url).map((s) => ({ url: s.url, lang: s.lang || "und" })); }
      catch { return []; }
    }));
    return res.flat();
  }

  async function refreshAddons() {
    const fresh = await Promise.all(S.addons.map(async (a) => { try { return await installAddon(a.url); } catch { return a; } }));
    S.addons = fresh; saveAddons();
  }

  // ------------------------------------------------------------------ UI helpers
  const app = $("#app");
  const tabs = $("#tabs");
  function toast(msg, ms = 2600) {
    const t = $("#toast"); t.textContent = msg; t.hidden = false;
    clearTimeout(toast._t); toast._t = setTimeout(() => (t.hidden = true), ms);
  }
  const go = (hash) => { location.hash = hash; };
  const avatar = (p, cls = "") => `<div class="avatar ${cls}" style="background:linear-gradient(135deg, ${p.color}, ${p.color}99)">${esc((p.name || "?").slice(0, 1).toUpperCase())}</div>`;
  const posterHTML = (m) => `<button class="poster" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      <span class="fallback">${esc(m.name)}</span>${m.poster ? `<img src="${esc(m.poster)}" alt="" loading="lazy" onerror="this.remove()">` : ""}</button>`;
  const header = (title, extra = "") => `<header class="header">${title ? `<h1>${esc(title)}</h1>` : LOGO + '<span class="spacer"></span>'}${extra}</header>`;
  const metaLine = (m) => `<div class="meta-line"><span class="badge">${typeLabel(m.type, false)}</span>
      ${m.releaseInfo ? `<span>${esc(m.releaseInfo)}</span>` : ""}
      ${m.imdbRating ? `<span><span class="star">★</span> ${esc(m.imdbRating)}</span>` : ""}
      ${m.runtime ? `<span>${esc(m.runtime)}</span>` : ""}
      ${m.genres && m.genres.length ? `<span>${m.genres.slice(0, 3).map(genreLabel).map(esc).join(" · ")}</span>` : ""}</div>`;

  // Délégation : ouvrir une fiche
  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-open]");
    if (el) { const [type, id] = el.dataset.open.split("|"); go(`#/title/${enc(type)}/${enc(id)}`); }
  });
  // Ombre sous l'en-tête au défilement
  window.addEventListener("scroll", () => { const h = $(".header"); if (h) h.classList.toggle("scrolled", window.scrollY > 4); }, { passive: true });

  function setTabs(active) {
    if (!active) { tabs.hidden = true; document.body.classList.add("no-tabs"); return; }
    document.body.classList.remove("no-tabs");
    tabs.hidden = false;
    const items = [["home", "Accueil", ICON.home], ["movies", "Films", ICON.movie], ["series", "Séries", ICON.series], ["search", "Recherche", ICON.search], ["list", "Ma liste", ICON.list]];
    tabs.innerHTML = items.map(([k, l, i]) => `<button class="tab ${k === active ? "active" : ""}" data-tab="${k}">${i}<span>${l}</span></button>`).join("");
    tabs.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => {
      if (location.hash === "#/" + b.dataset.tab) window.scrollTo({ top: 0, behavior: "smooth" }); else go("#/" + b.dataset.tab);
    }));
  }

  function buildRows(type) {
    const rows = [];
    S.addons.forEach((a) => a.manifest.catalogs.filter((c) => !catRequires(c) && (!type || c.type === type)).forEach((c) => {
      rows.push({ key: `${a.url}|${c.type}|${c.id}`, title: a.manifest.id === "com.linvo.cinemeta" ? catalogTitle(c.type, c.name) : `${c.name} · ${a.manifest.name}`, a, c, extra: {} });
    }));
    if (type) {
      const src = genreSource(type);
      if (src) {
        const opts = catOptions(src.c, "genre");
        HOME_GENRES.filter((g) => !opts.length || opts.includes(g)).forEach((g) =>
          rows.push({ key: `${src.a.url}|${type}|${src.c.id}|${g}`, title: `${genreLabel(g)} · ${typeLabel(type)}`, a: src.a, c: src.c, extra: { genre: g } }));
      }
    }
    return rows;
  }
  function genreSource(type) {
    for (const a of S.addons) {
      const c = a.manifest.catalogs.find((c) => c.type === type && catSupports(c, "genre") && catOnlyRequires(c, "genre"));
      if (c) return { a, c };
    }
    return null;
  }

  function rowShell(row) {
    const sec = document.createElement("section");
    sec.className = "section";
    sec.innerHTML = `<h2 class="section-title"><span>${esc(row.title)}</span></h2>
      <div class="row">${Array.from({ length: 6 }, () => '<div class="poster skeleton"></div>').join("")}</div>`;
    return sec;
  }
  function lazyRows(container, rows, onFirst) {
    const io = new IntersectionObserver((entries) => entries.forEach(async (en) => {
      if (!en.isIntersecting) return;
      io.unobserve(en.target);
      const row = en.target._row;
      try {
        const list = await catalog(row.a, row.c, row.extra);
        if (!list.length) { en.target.remove(); return; }
        en.target.querySelector(".row").innerHTML = list.map(posterHTML).join("");
        onFirst && onFirst(list);
      } catch { en.target.remove(); }
    }), { rootMargin: "400px 0px" });
    rows.forEach((r) => { const s = rowShell(r); s._row = r; container.appendChild(s); io.observe(s); });
  }

  // ------------------------------------------------------------------ écrans
  function screenProfiles() {
    setTabs(null);
    if (!S.profiles.length) return go("#/profile/new");
    let manage = false;
    const draw = () => {
      app.innerHTML = `<div class="profiles-screen">${LOGO}
        <h1>${manage ? "Gérer les profils" : "Qui regarde ?"}</h1>
        <div class="profiles">
          ${S.profiles.map((p) => `<button class="profile" data-p="${esc(p.id)}">${avatar(p).replace("</div>", manage ? `<span class="edit">${ICON.edit}</span></div>` : "</div>")}<span>${esc(p.name)}</span></button>`).join("")}
          ${S.profiles.length < 6 ? `<button class="profile" data-new><div class="avatar add-avatar">${ICON.plus}</div><span>Ajouter</span></button>` : ""}
        </div>
        <div style="margin-top:36px"><button class="btn" id="manage">${manage ? ICON.check + " Terminé" : ICON.edit + " Gérer les profils"}</button></div>
      </div>`;
      app.querySelectorAll("[data-p]").forEach((b) => b.addEventListener("click", () => {
        const p = S.profiles.find((x) => x.id === b.dataset.p);
        if (manage) return go("#/profile/" + enc(p.id));
        selectProfile(p); go("#/home");
      }));
      const n = app.querySelector("[data-new]"); if (n) n.addEventListener("click", () => go("#/profile/new"));
      $("#manage").addEventListener("click", () => { manage = !manage; draw(); });
    };
    draw();
  }

  function screenProfileEdit(id) {
    setTabs(null);
    const existing = S.profiles.find((p) => p.id === id);
    let color = existing ? existing.color : COLORS[S.profiles.length % COLORS.length];
    app.innerHTML = `<div class="profiles-screen">
      <h1>${existing ? "Modifier le profil" : "Nouveau profil"}</h1>
      <div id="av" style="width:120px">${avatar({ name: existing ? existing.name : "?", color })}</div>
      <div style="width:100%;max-width:360px;margin-top:24px;display:grid;gap:18px">
        <input class="field" id="name" maxlength="16" placeholder="Prénom" value="${esc(existing ? existing.name : "")}" autocomplete="off">
        <div class="swatches">${COLORS.map((c) => `<button class="swatch ${c === color ? "on" : ""}" data-c="${c}" style="background:${c}" aria-label="Couleur"></button>`).join("")}</div>
        <button class="btn primary block" id="save">${ICON.check} Enregistrer</button>
        ${existing ? `<button class="btn danger block" id="del">${ICON.trash} Supprimer ce profil</button>` : ""}
        ${S.profiles.length ? `<button class="btn block" id="cancel">Annuler</button>` : ""}
      </div></div>`;
    const name = $("#name");
    const redraw = () => ($("#av").innerHTML = avatar({ name: name.value || "?", color }));
    name.addEventListener("input", redraw);
    app.querySelectorAll("[data-c]").forEach((b) => b.addEventListener("click", () => {
      color = b.dataset.c; app.querySelectorAll("[data-c]").forEach((x) => x.classList.toggle("on", x === b)); redraw();
    }));
    $("#save").addEventListener("click", () => {
      const v = name.value.trim(); if (!v) { name.focus(); return toast("Indique un prénom"); }
      if (existing) Object.assign(existing, { name: v, color });
      else S.profiles.push({ id: "p" + Date.now().toString(36), name: v, color });
      saveProfiles();
      if (S.profile && existing && S.profile.id === existing.id) S.profile = existing;
      go("#/profiles");
    });
    if (existing) $("#del").addEventListener("click", () => {
      if (!confirm(`Supprimer le profil « ${existing.name} » ?`)) return;
      S.profiles = S.profiles.filter((p) => p.id !== existing.id); saveProfiles();
      LS.del("lib." + existing.id); LS.del("prog." + existing.id);
      if (S.profile && S.profile.id === existing.id) S.profile = null;
      go("#/profiles");
    });
    const c = $("#cancel"); if (c) c.addEventListener("click", () => history.back());
  }

  function screenBrowse(type) {
    setTabs(type === "movie" ? "movies" : type === "series" ? "series" : "home");
    const p = S.profile;
    const right = `<button class="icon-btn" id="settings" aria-label="Réglages">${ICON.settings}</button>
      <button id="who" aria-label="Changer de profil">${avatar(p, "sm")}</button>`;
    app.innerHTML = header(type ? typeLabel(type) : "", right) + `<div id="upd"></div><div id="hero"></div><div id="rows"></div>`;
    $("#settings").addEventListener("click", () => go("#/settings"));
    $("#who").addEventListener("click", () => go("#/profiles"));
    showUpdateBanner($("#upd"));
    const rowsEl = $("#rows");

    const cont = continueWatching().filter((x) => !type || x.type === type);
    if (cont.length) {
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span>Continuer à regarder</span></h2><div class="row">${cont.map((x) => `
        <button class="wide" data-resume="${esc(x.type)}|${esc(x.metaId)}|${esc(x.videoId)}">
          <div class="thumb">${x.background || x.poster ? `<img src="${esc(x.background || x.poster)}" alt="" loading="lazy">` : ""}<div class="bar"><i style="width:${Math.round(fraction(x) * 100)}%"></i></div></div>
          <div class="title">${esc(x.name)}</div>
          <div class="sub">${x.season != null ? `S${x.season} : É${x.episode} · ${esc(x.episodeTitle || "")}` : `Il reste ${Math.max(1, Math.round((x.duration - x.position) / 60000))} min`}</div>
        </button>`).join("")}</div>`;
      sec.querySelectorAll("[data-resume]").forEach((b) => b.addEventListener("click", () => {
        const [t, m, v] = b.dataset.resume.split("|"); go(`#/sources/${enc(t)}/${enc(m)}/${enc(v)}`);
      }));
      rowsEl.appendChild(sec);
    }
    const lib = S.lib.filter((x) => !type || x.type === type);
    if (lib.length) {
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span>Ma liste</span><a href="#/list">Tout voir</a></h2><div class="row">${lib.map(posterHTML).join("")}</div>`;
      rowsEl.appendChild(sec);
    }
    if (!type) {
      const pal = [["#7C3AED", "#2563EB"], ["#DB2777", "#F97316"], ["#059669", "#22D3EE"], ["#1E3A8A", "#7C3AED"], ["#7F1D1D", "#111827"], ["#0F766E", "#1E293B"], ["#B45309", "#7C2D12"], ["#334155", "#0EA5E9"]];
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span>Explorer par catégorie</span></h2><div class="genres">${HOME_GENRES.map((g, i) =>
        `<button class="genre" style="background:linear-gradient(135deg, ${pal[i][0]}, ${pal[i][1]})" data-g="${g}">${genreLabel(g)}</button>`).join("")}</div>`;
      sec.querySelectorAll("[data-g]").forEach((b) => b.addEventListener("click", () => go(`#/genre/movie/${enc(b.dataset.g)}`)));
      rowsEl.appendChild(sec);
    }
    const rows = buildRows(type);
    if (!rows.length) rowsEl.insertAdjacentHTML("beforeend", `<div class="empty"><h2>Aucun catalogue</h2>Ajoute un add-on de catalogue dans Réglages.</div>`);
    let heroDone = false;
    lazyRows(rowsEl, rows, (list) => {
      if (heroDone) return; heroDone = true;
      const pick = list.slice(0, 8).find((m) => m.background) || list[0];
      if (pick) renderHero($("#hero"), pick);
    });
  }

  function renderHero(el, m) {
    el.innerHTML = `<div class="hero">
      <img class="art" src="${esc(m.poster || m.background)}" alt="">
      <div class="shade"></div>
      <div class="info">
        ${m.logo ? `<img class="logo-img" src="${esc(m.logo)}" alt="${esc(m.name)}" onerror="this.outerHTML='<h2>${esc(m.name).replace(/'/g, "")}</h2>'">` : `<h2>${esc(m.name)}</h2>`}
        <div class="meta-line">${m.genres && m.genres.length ? m.genres.slice(0, 3).map(genreLabel).map(esc).join(" · ") : esc(m.releaseInfo || "")}</div>
        <div class="actions">
          <button class="btn primary" data-open="${esc(m.type)}|${esc(m.id)}">${ICON.play} Lecture</button>
          <button class="btn" id="heroList">${inLib(m.id) ? ICON.check : ICON.plus} Ma liste</button>
        </div>
      </div></div>`;
    $("#heroList").addEventListener("click", (e) => {
      toggleLib(m); e.currentTarget.innerHTML = `${inLib(m.id) ? ICON.check : ICON.plus} Ma liste`;
      toast(inLib(m.id) ? "Ajouté à ta liste" : "Retiré de ta liste");
    });
  }

  function screenList() {
    setTabs("list");
    app.innerHTML = header("Ma liste") + (S.lib.length
      ? `<div class="grid" style="margin-top:8px">${S.lib.map(posterHTML).join("")}</div>`
      : `<div class="empty"><h2>Ta liste est vide</h2>Ouvre un film ou une série et appuie sur « Ma liste ».</div>`);
  }

  function screenSearch() {
    setTabs("search");
    const last = sessionStorage.getItem("tvo.q") || "";
    app.innerHTML = header("Recherche") + `<div class="pad"><div class="search-box">${ICON.search}
      <input class="field" id="q" type="search" enterkeyhint="search" placeholder="Film, série…" value="${esc(last)}" autocomplete="off" autocorrect="off"></div></div>
      <div id="res"></div>`;
    const q = $("#q"), res = $("#res");
    let timer, seq = 0;
    const run = async () => {
      const v = q.value.trim(); sessionStorage.setItem("tvo.q", v);
      const my = ++seq;
      if (v.length < 2) { res.innerHTML = `<div class="empty"><h2>Que veux-tu regarder ?</h2>Tape au moins deux lettres.</div>`; return; }
      res.innerHTML = `<div class="empty">Recherche…</div>`;
      const cats = [];
      S.addons.forEach((a) => a.manifest.catalogs.filter((c) => catSupports(c, "search") && catOnlyRequires(c, "search")).forEach((c) => cats.push({ a, c })));
      const out = await Promise.all(cats.map(async ({ a, c }) => {
        try { return { title: a.manifest.id === "com.linvo.cinemeta" ? typeLabel(c.type) : `${c.name} · ${a.manifest.name}`, list: await catalog(a, c, { search: v }) }; }
        catch { return { list: [] }; }
      }));
      if (my !== seq) return;
      const found = out.filter((x) => x.list.length);
      res.innerHTML = found.length ? found.map((x) => `<section class="section"><h2 class="section-title"><span>${esc(x.title)}</span></h2>
        <div class="row">${x.list.map(posterHTML).join("")}</div></section>`).join("")
        : `<div class="empty"><h2>Aucun résultat</h2>Essaie un autre titre, en anglais ou en français.</div>`;
    };
    q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(run, 400); });
    q.addEventListener("keydown", (e) => { if (e.key === "Enter") { q.blur(); run(); } });
    run();
    if (!last) setTimeout(() => q.focus(), 150);
  }

  async function screenGenre(type, genre) {
    setTabs(type === "series" ? "series" : "movies");
    app.innerHTML = header(`${genreLabel(genre)} · ${typeLabel(type)}`, `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`)
      + `<div class="grid" id="g" style="margin-top:8px"></div><div id="more" class="empty small">Chargement…</div>`;
    const src = genreSource(type);
    if (!src) { $("#more").textContent = "Aucun add-on ne propose de tri par genre."; return; }
    const ids = new Set(); let busy = false, end = false;
    const g = $("#g"), more = $("#more");
    const load = async () => {
      if (busy || end) return; busy = true;
      const extra = { genre }; if (ids.size && catSupports(src.c, "skip")) extra.skip = String(ids.size);
      let list = [];
      try { list = await catalog(src.a, src.c, extra); } catch { /* */ }
      const fresh = list.filter((m) => !ids.has(m.id)); fresh.forEach((m) => ids.add(m.id));
      g.insertAdjacentHTML("beforeend", fresh.map(posterHTML).join(""));
      if (!fresh.length || !catSupports(src.c, "skip")) { end = true; more.textContent = ids.size ? "" : "Rien à afficher."; }
      busy = false;
    };
    const io = new IntersectionObserver((e) => e[0].isIntersecting && load(), { rootMargin: "600px 0px" });
    io.observe(more);
    load();
  }

  function resumeTarget(m) {
    if (m.type !== "series" || !m.episodes.length) {
      const vid = m.defaultVideoId || (m.videos.length === 1 ? m.videos[0].id : m.id);
      return { vid, prog: S.prog.find((p) => p.videoId === vid) };
    }
    const last = S.prog.filter((p) => p.metaId === m.id).sort((a, b) => b.updatedAt - a.updatedAt)[0];
    const eps = m.episodes;
    if (!last) { const f = eps.find((e) => (e.season || 0) > 0) || eps[0]; return { vid: f.id, prog: null }; }
    if (!finished(last)) return { vid: last.videoId, prog: last };
    const i = eps.findIndex((e) => e.id === last.videoId);
    const next = eps[i + 1] || eps[Math.max(0, i)];
    return { vid: next.id, prog: S.prog.find((p) => p.videoId === next.id) };
  }

  async function screenTitle(type, id) {
    setTabs(null);
    app.innerHTML = `<div class="detail-top"><div class="backdrop skeleton"></div></div><div class="empty">Chargement…</div>`;
    const m = await meta(type, id);
    if (!m) { app.innerHTML = header("", `<button class="icon-btn" onclick="history.back()">${ICON.back}</button>`) + `<div class="empty"><h2>Fiche introuvable</h2>Aucun add-on n'a pu fournir ce titre.</div>`; return; }
    const { vid, prog } = resumeTarget(m);
    const tv = m.videos.find((v) => v.id === vid);
    const started = prog && !finished(prog) && fraction(prog) > 0.02;
    const label = started ? (tv && tv.season != null ? `Reprendre S${tv.season} : É${tv.episode}` : "Reprendre")
      : (tv && tv.season != null ? `Lecture S${tv.season} : É${tv.episode}` : "Lecture");
    const seasons = [...new Set(m.episodes.map((e) => e.season))];
    let season = tv && tv.season != null ? tv.season : seasons[0];

    app.innerHTML = `
      <div class="detail-top">
        <button class="icon-btn floating-back" onclick="history.back()" aria-label="Retour">${ICON.back}</button>
        <img class="backdrop" src="${esc(m.background || m.poster || "")}" alt="">
        <div class="shade"></div>
      </div>
      <div class="detail-body">
        ${m.logo ? `<img class="logo-img" src="${esc(m.logo)}" alt="${esc(m.name)}">` : `<h1>${esc(m.name)}</h1>`}
        ${metaLine(m)}
        <div class="detail-actions">
          <button class="btn primary block" id="play">${ICON.play} ${esc(label)}</button>
          ${started ? `<div class="small muted">Il reste ${Math.max(1, Math.round((prog.duration - prog.position) / 60000))} min</div>` : ""}
          <div class="two"><button class="btn" id="lib">${inLib(m.id) ? ICON.check + " Dans ma liste" : ICON.plus + " Ma liste"}</button>
          <button class="btn" id="share">Partager</button></div>
        </div>
        ${m.description ? `<div class="desc clamp" id="desc">${esc(m.description)}</div><button class="more" id="moreDesc">Lire la suite</button>` : ""}
        ${m.cast.length ? `<div class="people">Avec : ${m.cast.map(esc).join(", ")}</div>` : ""}
        ${m.director.length ? `<div class="people">Réalisation : ${m.director.map(esc).join(", ")}</div>` : ""}
      </div>
      ${seasons.length ? `<section class="section"><div class="chips" id="seasons"></div><div class="episodes" id="eps" style="margin-top:14px"></div></section>` : ""}`;

    $("#play").addEventListener("click", () => go(`#/sources/${enc(m.type)}/${enc(m.id)}/${enc(vid)}`));
    $("#lib").addEventListener("click", (e) => { toggleLib(m); e.currentTarget.innerHTML = inLib(m.id) ? ICON.check + " Dans ma liste" : ICON.plus + " Ma liste"; });
    $("#share").addEventListener("click", async () => {
      const text = `${m.name}${m.releaseInfo ? " (" + m.releaseInfo + ")" : ""} — à regarder sur TVO`;
      try { if (navigator.share) await navigator.share({ title: m.name, text }); else { await navigator.clipboard.writeText(text); toast("Copié"); } } catch { /* annulé */ }
    });
    const md = $("#moreDesc");
    if (md) md.addEventListener("click", () => { $("#desc").classList.toggle("clamp"); md.textContent = $("#desc").classList.contains("clamp") ? "Lire la suite" : "Réduire"; });

    if (seasons.length) {
      const drawSeasons = () => {
        $("#seasons").innerHTML = seasons.map((s) => `<button class="chip ${s === season ? "on" : ""}" data-s="${s}">${s === 0 ? "Bonus" : "Saison " + s}</button>`).join("");
        $("#seasons").querySelectorAll("[data-s]").forEach((b) => b.addEventListener("click", () => { season = Number(b.dataset.s); drawSeasons(); drawEps(); }));
      };
      const drawEps = () => {
        $("#eps").innerHTML = m.episodes.filter((e) => e.season === season).map((e) => {
          const p = S.prog.find((x) => x.videoId === e.id);
          return `<button class="episode" data-v="${esc(e.id)}">
            <div class="thumb">${e.thumbnail || m.background ? `<img src="${esc(e.thumbnail || m.background)}" alt="" loading="lazy">` : ""}
              ${p && finished(p) ? '<span class="seen">VU</span>' : ""}${p && !finished(p) ? `<div class="bar"><i style="width:${Math.round(fraction(p) * 100)}%"></i></div>` : ""}</div>
            <div><h3>${e.episode ?? "?"}. ${esc(e.title || "Épisode " + (e.episode ?? ""))}</h3>
              ${e.released ? `<div class="small muted">${new Date(e.released).toLocaleDateString("fr-FR")}</div>` : ""}
              ${e.overview ? `<p>${esc(e.overview)}</p>` : ""}</div></button>`;
        }).join("");
        $("#eps").querySelectorAll("[data-v]").forEach((b) => b.addEventListener("click", () => go(`#/sources/${enc(m.type)}/${enc(m.id)}/${enc(b.dataset.v)}`)));
      };
      drawSeasons(); drawEps();
    }
  }

  async function screenSources(type, metaId, videoId) {
    setTabs(null);
    app.innerHTML = header("Sources", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`)
      + `<div class="pad" id="info"></div><div class="pad small muted" id="status" style="margin:12px 0"></div><div class="list" id="list"></div>`;
    const m = await meta(type, metaId);
    const v = m && m.videos.find((x) => x.id === videoId);
    $("#info").innerHTML = m ? `<div style="display:flex;gap:14px;align-items:center">
        ${m.poster ? `<img src="${esc(m.poster)}" alt="" style="width:64px;border-radius:8px">` : ""}
        <div><b style="font-size:18px">${esc(m.name)}</b>${v && v.season != null ? `<div class="small" style="color:var(--cyan)">Saison ${v.season} · Épisode ${v.episode}</div><div class="small muted">${esc(v.title)}</div>` : ""}</div></div>` : "";
    const found = [];
    let done = 0;
    const draw = (total) => {
      const playable = found.filter((s) => s.url || s.externalUrl).sort((a, b) => b.quality - a.quality);
      const torrents = found.length - playable.length;
      $("#status").textContent = total === 0 ? "Aucun add-on de sources installé."
        : done < total ? `Recherche en cours… (${done}/${total} add-ons)`
        : `${playable.length} source(s)` + (torrents ? ` · ${torrents} torrent(s) masqué(s), non pris en charge` : "");
      $("#list").innerHTML = playable.length ? playable.map((s, i) => `
        <button class="card source" data-i="${i}">
          <span class="q ${s.quality >= 3 ? "hd" : ""}">${qLabel(s.quality)}</span>
          <span style="min-width:0"><span class="name" style="display:block">${esc(s.name.replace(/\n/g, " "))}</span>
            ${s.title ? `<span class="desc2">${esc(s.title)}</span>` : ""}<span class="from" style="display:block">${esc(s.addonName)}</span></span>
        </button>`).join("")
        : (done >= total ? `<div class="empty">${total === 0 ? "Ajoute un add-on qui fournit des vidéos dans Réglages › Add-ons (comme dans Nuvio ou Stremio)." : "Aucune source lisible pour ce titre."}</div>` : "");
      $("#list").querySelectorAll("[data-i]").forEach((b) => b.addEventListener("click", () => playStream(m, playable[Number(b.dataset.i)], videoId)));
    };
    const st = streams(type, videoId, (items) => { found.push(...items); done++; draw(st.total); });
    draw(st.total);
  }

  function screenSettings() {
    setTabs(null);
    const p = S.profile;
    const draw = () => {
      app.innerHTML = header("Réglages", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `
      <div class="list" style="margin-top:8px">
        <div class="card" style="display:flex;align-items:center;gap:12px">${avatar(p, "sm")}<b style="flex:1">${esc(p.name)}</b>
          <button class="btn" id="editP">${ICON.edit} Modifier</button></div>
        <button class="btn block" id="switchP">Changer de profil</button>
      </div>
      <h2 class="section-title section"><span>Add-ons</span></h2>
      <p class="pad small muted" style="margin-top:-4px">Comme dans Nuvio ou Stremio : catalogues, fiches, vidéos et sous-titres viennent des add-ons. Le premier est prioritaire.</p>
      <div class="list">${S.addons.map((a, i) => {
        const kinds = a.manifest.resources.map((r) => ({ catalog: "catalogues", meta: "fiches", stream: "vidéos", subtitles: "sous-titres" }[r.name])).filter(Boolean);
        return `<div class="card addon"><div class="top">${a.manifest.logo ? `<img src="${esc(a.manifest.logo)}" alt="" onerror="this.remove()">` : ""}
          <div style="min-width:0"><b>${esc(a.manifest.name)}</b> <span class="small muted">${esc(a.manifest.version)}</span>
          <div class="small" style="color:var(--cyan)">${kinds.join(" · ") || "—"}</div>
          ${a.manifest.description ? `<div class="small muted">${esc(a.manifest.description)}</div>` : ""}</div></div>
          <div class="actions">${i > 0 ? `<button class="btn" data-up="${i}">${ICON.up} Monter</button>` : ""}<button class="btn danger" data-rm="${i}">${ICON.trash} Retirer</button></div></div>`;
      }).join("")}
        <button class="btn accent block" id="addA">${ICON.plus} Ajouter un add-on</button></div>
      <h2 class="section-title section"><span>Lecteur</span></h2>
      <div class="chips">${[["tvo", "Lecteur TVO"], ["vlc", "VLC"], ...(isIOS ? [["infuse", "Infuse"]] : [])].map(([k, l]) => `<button class="chip ${S.player === k ? "on" : ""}" data-pl="${k}">${l}</button>`).join("")}</div>
      <p class="pad small muted">VLC ou Infuse lisent plus de formats (MKV, DTS…). Ils doivent être installés.</p>
      <h2 class="section-title section"><span>Application</span></h2>
      <div class="list"><div class="card">Version ${esc(VERSION.name)}<div class="small muted">TVO n'héberge aucun contenu : les vidéos viennent des add-ons que tu ajoutes.</div></div>
        <button class="btn block" id="upd">Rechercher une mise à jour</button></div>`;
      $("#editP").addEventListener("click", () => go("#/profile/" + enc(p.id)));
      $("#switchP").addEventListener("click", () => go("#/profiles"));
      $("#addA").addEventListener("click", () => go("#/addon-add"));
      app.querySelectorAll("[data-up]").forEach((b) => b.addEventListener("click", () => {
        const i = Number(b.dataset.up); const [x] = S.addons.splice(i, 1); S.addons.splice(i - 1, 0, x); saveAddons(); draw();
      }));
      app.querySelectorAll("[data-rm]").forEach((b) => b.addEventListener("click", () => {
        const a = S.addons[Number(b.dataset.rm)];
        if (!confirm(`Retirer « ${a.manifest.name} » ?`)) return;
        S.addons.splice(Number(b.dataset.rm), 1); saveAddons(); cache.clear(); draw();
      }));
      app.querySelectorAll("[data-pl]").forEach((b) => b.addEventListener("click", () => { S.player = b.dataset.pl; LS.set("player", S.player); draw(); }));
      $("#upd").addEventListener("click", async () => {
        const r = await checkUpdate(true);
        if (!r) toast(VERSION.code ? "TVO est à jour" : "Version de développement");
        else openUpdate(r);
      });
    };
    draw();
  }

  function screenAddonAdd() {
    setTabs(null);
    app.innerHTML = header("Ajouter un add-on", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `
      <div class="list" style="margin-top:8px">
        <input class="field" id="url" type="url" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="https://…/manifest.json">
        <button class="btn primary block" id="ok">${ICON.plus} Installer</button>
        <button class="btn block" id="paste">Coller depuis le presse-papiers</button>
        <div id="msg" class="small" style="color:var(--cyan)"></div>
      </div>
      <h2 class="section-title section"><span>Suggestions</span></h2>
      <div class="list">${SUGGESTED.map(([u, l]) => `<button class="card" data-s="${esc(u)}" style="text-align:left"><b>${esc(l)}</b><div class="small muted">${esc(u)}</div></button>`).join("")}</div>`;
    const install = async (u) => {
      if (!u.trim()) return;
      $("#msg").textContent = "Installation…";
      try {
        const a = await installAddon(u);
        const i = S.addons.findIndex((x) => x.manifest.id === a.manifest.id || x.url === a.url);
        if (i >= 0) S.addons[i] = a; else S.addons.push(a);
        saveAddons(); cache.clear(); toast(`« ${a.manifest.name} » installé`); history.back();
      } catch (e) { $("#msg").textContent = "Impossible d'installer : vérifie l'adresse (" + e.message + ")."; }
    };
    $("#ok").addEventListener("click", () => install($("#url").value));
    $("#paste").addEventListener("click", async () => { try { $("#url").value = await navigator.clipboard.readText(); } catch { toast("Colle l'adresse dans le champ"); } });
    app.querySelectorAll("[data-s]").forEach((b) => b.addEventListener("click", () => install(b.dataset.s)));
  }

  // ------------------------------------------------------------------ lecture
  function externalUrl(url) {
    if (S.player === "vlc") return isIOS ? "vlc-x-callback://x-callback-url/stream?url=" + enc(url) : "vlc://" + url;
    if (S.player === "infuse") return "infuse://x-callback-url/play?url=" + enc(url);
    return null;
  }

  async function playStream(m, s, videoId) {
    if (!s.url && s.externalUrl) { window.open(s.externalUrl, "_blank"); return; }
    const ext = externalUrl(s.url);
    if (ext || s.needsHeaders) {
      if (!ext) toast("Cette source demande un lecteur externe (VLC).");
      location.href = ext || ("vlc://" + s.url);
      return;
    }
    const v = m.videos.find((x) => x.id === videoId);
    const p = S.prog.find((x) => x.videoId === videoId);
    toast("Préparation de la lecture…", 1500);
    const subs = [...s.subtitles, ...(await subtitles(m.type, videoId).catch(() => []))];
    openPlayer({ m, s, v, videoId, start: p && !finished(p) && fraction(p) > 0.02 ? p.position / 1000 : 0, subs });
  }

  const LANG = { fr: "Français", fre: "Français", fra: "Français", en: "Anglais", eng: "Anglais", es: "Espagnol", spa: "Espagnol",
    de: "Allemand", ger: "Allemand", it: "Italien", ita: "Italien", pt: "Portugais", por: "Portugais", ar: "Arabe", ara: "Arabe" };
  const srtToVtt = (t) => "WEBVTT\n\n" + t.replace(/\r/g, "").replace(/(\d\d:\d\d:\d\d),(\d\d\d)/g, "$1.$2");

  let hls = null;
  function closePlayer() {
    const el = $("#player");
    if (el._save) el._save();
    if (hls) { hls.destroy(); hls = null; }
    el.innerHTML = ""; el.hidden = true;
    document.body.style.overflow = "";
    if (screen.orientation && screen.orientation.unlock) try { screen.orientation.unlock(); } catch { /* */ }
  }

  function openPlayer(ctx) {
    const { m, s, v } = ctx;
    const el = $("#player");
    el.hidden = false; document.body.style.overflow = "hidden";
    el.innerHTML = `<div class="bar"><button class="icon-btn" id="pClose" aria-label="Fermer">${ICON.close}</button>
        <div class="t"><b>${esc(m.name)}</b><span>${v && v.season != null ? `S${v.season} : É${v.episode} · ${esc(v.title)}` : esc(s.addonName)}</span></div></div>
      <video id="vid" controls playsinline autoplay preload="auto"></video>`;
    const video = $("#vid");
    $("#pClose").addEventListener("click", closePlayer);

    // Sous-titres (convertis en WebVTT)
    ctx.subs.slice(0, 20).forEach(async (sub, i) => {
      try {
        const r = await fetch(sub.url); let txt = await r.text();
        if (!/^WEBVTT/.test(txt)) txt = srtToVtt(txt);
        const tr = document.createElement("track");
        tr.kind = "subtitles"; tr.label = LANG[(sub.lang || "").toLowerCase()] || sub.lang || "Sous-titres " + (i + 1);
        tr.srclang = (sub.lang || "und").slice(0, 2);
        tr.src = URL.createObjectURL(new Blob([txt], { type: "text/vtt" }));
        video.appendChild(tr);
      } catch { /* ignore */ }
    });

    const isHls = /\.m3u8(\?|$)/i.test(s.url);
    if (isHls && !video.canPlayType("application/vnd.apple.mpegurl") && window.Hls && window.Hls.isSupported()) {
      hls = new window.Hls(); hls.loadSource(s.url); hls.attachMedia(video);
    } else {
      video.src = s.url;
    }
    video.addEventListener("loadedmetadata", () => { if (ctx.start > 0 && ctx.start < video.duration - 5) video.currentTime = ctx.start; }, { once: true });

    // Barre du haut qui se masque
    let idle;
    const wake = () => { el.classList.remove("idle"); clearTimeout(idle); idle = setTimeout(() => !video.paused && el.classList.add("idle"), 3500); };
    ["pointerdown", "pointermove", "touchstart"].forEach((e) => el.addEventListener(e, wake, { passive: true }));
    video.addEventListener("pause", wake); wake();

    let lastSave = 0;
    const save = (done = false) => {
      if (!video.duration || !isFinite(video.duration)) return;
      saveProgress({ videoId: ctx.videoId, metaId: m.id, type: m.type, name: m.name, poster: m.poster, background: m.background,
        season: v ? v.season : null, episode: v ? v.episode : null, episodeTitle: v ? v.title : null,
        position: Math.round((done ? video.duration : video.currentTime) * 1000), duration: Math.round(video.duration * 1000), updatedAt: Date.now() });
    };
    el._save = () => save();
    video.addEventListener("timeupdate", () => {
      if (Date.now() - lastSave > 5000) { lastSave = Date.now(); save(); }
      if (m.type === "series" && !ctx.nextShown && video.duration - video.currentTime < 25 && video.duration > 60) { ctx.nextShown = true; showNext(ctx, video); }
    });
    video.addEventListener("pause", () => save());
    video.addEventListener("ended", () => { save(true); if (m.type === "series" && !ctx.nextCancelled) playNext(ctx); else closePlayer(); });
    video.addEventListener("error", () => {
      el.insertAdjacentHTML("beforeend", `<div class="err"><div class="card"><b style="font-size:20px">Lecture impossible</b>
        <div class="muted">Ce format n'est peut-être pas pris en charge par le lecteur intégré. Essaie VLC ou une autre source.</div>
        <button class="btn primary" id="eVlc">Ouvrir dans VLC</button><button class="btn" id="eBack">Retour</button></div></div>`);
      $("#eVlc").addEventListener("click", () => { location.href = isIOS ? "vlc-x-callback://x-callback-url/stream?url=" + enc(s.url) : "vlc://" + s.url; });
      $("#eBack").addEventListener("click", closePlayer);
    });
    if (screen.orientation && screen.orientation.lock) screen.orientation.lock("landscape").catch(() => {});
  }

  function showNext(ctx, video) {
    const el = $("#player");
    const box = document.createElement("div");
    box.className = "next";
    box.innerHTML = `<b>Épisode suivant</b><div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
      <button class="btn primary" id="nNow">Lire</button><button class="btn" id="nNo">Annuler</button></div>`;
    el.appendChild(box);
    $("#nNow").addEventListener("click", () => { box.remove(); playNext(ctx); });
    $("#nNo").addEventListener("click", () => { ctx.nextCancelled = true; box.remove(); });
  }

  async function playNext(ctx) {
    const { m } = ctx;
    const i = m.episodes.findIndex((e) => e.id === ctx.videoId);
    const next = m.episodes[i + 1];
    if (!next) return closePlayer();
    toast("Recherche de l'épisode suivant…");
    const all = await streams(m.type, next.id).done;
    const ok = all.filter((x) => x.url && !x.needsHeaders);
    const pick = ok.find((x) => ctx.s.bingeGroup && x.bingeGroup === ctx.s.bingeGroup)
      || ok.filter((x) => x.addonName === ctx.s.addonName).sort((a, b) => b.quality - a.quality)[0]
      || ok.sort((a, b) => b.quality - a.quality)[0];
    closePlayer();
    if (!pick) return toast("Aucune source pour l'épisode suivant");
    const subs = [...pick.subtitles, ...(await subtitles(m.type, next.id).catch(() => []))];
    openPlayer({ m, s: pick, v: next, videoId: next.id, start: 0, subs });
  }

  // ------------------------------------------------------------------ mises à jour
  let updateInfo = null;
  async function checkUpdate(force = false) {
    if (!VERSION.code && !force) return null;
    try {
      const r = await getJson(`https://api.github.com/repos/${VERSION.repo}/releases/latest`, 10000);
      const code = parseInt(String(r.tag_name || "").split(".").pop(), 10);
      if (!(code > VERSION.code)) return null;
      const asset = (r.assets || []).find((a) => (isIOS ? /\.ipa$/i : /mobile.*\.apk$/i).test(a.name));
      updateInfo = { name: String(r.tag_name).replace(/^v/, ""), url: asset ? asset.browser_download_url : r.html_url, page: r.html_url };
      return updateInfo;
    } catch { return null; }
  }
  function openUpdate(u) {
    if (isIOS) { toast("Télécharge le .ipa puis installe-le avec ESign ou KSign", 4000); }
    window.open(u.url, "_blank");
  }
  function showUpdateBanner(el) {
    if (!updateInfo || !el) return;
    el.innerHTML = `<div class="update-banner"><div><b>TVO ${esc(updateInfo.name)}</b> est disponible.</div><button class="btn accent" id="updGo">Mettre à jour</button></div>`;
    $("#updGo").addEventListener("click", () => openUpdate(updateInfo));
  }

  // ------------------------------------------------------------------ routeur
  function route() {
    if (!$("#player").hidden) closePlayer();
    const parts = location.hash.replace(/^#\/?/, "").split("/").map(decodeURIComponent);
    const [page, a, b, c] = parts;
    window.scrollTo(0, 0);
    if (page === "profile") return screenProfileEdit(a);
    if (page === "profiles" || !S.profile) return screenProfiles();
    switch (page) {
      case "home": case "": case undefined: return screenBrowse(null);
      case "movies": return screenBrowse("movie");
      case "series": return screenBrowse("series");
      case "list": return screenList();
      case "search": return screenSearch();
      case "settings": return screenSettings();
      case "addon-add": return screenAddonAdd();
      case "title": return screenTitle(a, b);
      case "genre": return screenGenre(a, b);
      case "sources": return screenSources(a, b, c);
      default: return screenBrowse(null);
    }
  }

  // ------------------------------------------------------------------ démarrage
  const cur = LS.get("current", null);
  const curP = S.profiles.find((p) => p.id === cur);
  if (curP && S.profiles.length === 1) selectProfile(curP);
  window.addEventListener("hashchange", route);
  document.addEventListener("visibilitychange", () => { if (document.hidden) { const el = $("#player"); if (el._save && !el.hidden) el._save(); } });
  if (!location.hash || location.hash === "#/home") location.hash = S.profile ? "#/home" : "#/profiles";
  route();
  refreshAddons();
  checkUpdate().then((u) => u && showUpdateBanner($("#upd")));
})();
