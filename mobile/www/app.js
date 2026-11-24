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
    live: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="2.2" fill="currentColor"/><path d="M8.2 8.2a5.4 5.4 0 0 0 0 7.6M15.8 8.2a5.4 5.4 0 0 1 0 7.6M5.2 5.2a9.6 9.6 0 0 0 0 13.6M18.8 5.2a9.6 9.6 0 0 1 0 13.6"/></svg>',
    trend: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m3 17 6-6 4 4 8-8"/><path d="M15 7h6v6"/></svg>',
    grid: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/></svg>',
    clap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><rect x="3" y="9" width="18" height="12" rx="2"/><path d="m3 9 2.5-5 15 2.5-1 2.5"/></svg>',
    bolt: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg>',
    mask: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4 4h16v6a8 8 0 0 1-16 0zm4 5.5a1.5 1.5 0 1 0 0 .01M16 9.5a1.5 1.5 0 1 0 0 .01M8 13c1 2 7 2 8 0"/></svg>',
    ufo: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="13" rx="9" ry="3.5"/><path d="M7 11a5 5 0 0 1 10 0M8 18l-1 3M16 18l1 3M12 17v4"/></svg>',
    ghost: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a8 8 0 0 0-8 8v12l3-2 2.5 2 2.5-2 2.5 2 2.5-2 3 2V10a8 8 0 0 0-8-8zm-3 7.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm6 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"/></svg>',
    knife: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M20 3 6 17l-2 4 4-2L22 5z"/><path d="m12 11 2 2"/></svg>',
    heart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 20s-7-4.4-8.8-8.8C2 7.9 4 5 7.2 5c1.9 0 3.2 1 4.8 2.6C13.6 6 14.9 5 16.8 5 20 5 22 7.9 20.8 11.2 19 15.6 12 20 12 20z"/></svg>',
    smile: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M8 14s1.5 2.5 4 2.5 4-2.5 4-2.5M9 9.5h.01M15 9.5h.01"/></svg>',
    doc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4z" fill="currentColor"/></svg>',
    more: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="6" cy="9" r="1.8"/><circle cx="12" cy="9" r="1.8"/><circle cx="18" cy="9" r="1.8"/><circle cx="6" cy="15" r="1.8"/><circle cx="12" cy="15" r="1.8"/><circle cx="18" cy="15" r="1.8"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 3h6l1 2h4v2H4V5h4zm-3 6h12l-1 12H7z"/></svg>',
  };
  const LOGO = `<img class="logo" src="logo-tv.png" alt="TVO">`;
  const MARK = `<img class="mark" src="logo-mark.png" alt="TVO">`;

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

  // ------------------------------------------------------------------ accès des profils (géré par l'administrateur)
  const today = () => new Date().toISOString().slice(0, 10);
  const usedToday = (pid) => ((LS.get("usage", {})[pid] || {})[today()] || 0);
  function addUsage(pid, min) { const u = LS.get("usage", {}); const d = (u[pid] = u[pid] || {}); d[today()] = (d[today()] || 0) + min;
    Object.keys(d).forEach((k) => { if (k < today().slice(0, 8)) delete d[k]; }); LS.set("usage", u); }
  /** Raison pour laquelle un profil ne peut pas regarder maintenant (ou null). L'administrateur n'est jamais bloqué. */
  function profileBlock(p) {
    if (!p || isAdmin(p)) return null;
    if (p.disabled) return "Ce profil est désactivé par l'administrateur.";
    if (p.trialUntil && Date.now() > p.trialUntil) return "La période d'essai de ce profil est terminée.";
    if (p.limit && usedToday(p.id) >= p.limit) return `Temps d'écran atteint pour aujourd'hui (${fmtMin(p.limit)}).`;
    return null;
  }
  const fmtMin = (m) => (m >= 60 ? `${Math.floor(m / 60)} h${m % 60 ? " " + String(Math.round(m % 60)).padStart(2, "0") : ""}` : `${Math.round(m)} min`);
  function profileStatus(p) {
    if (isAdmin(p)) return ["Administrateur", "admin"];
    if (p.disabled) return ["Désactivé", "off"];
    if (p.trialUntil) { const d = Math.ceil((p.trialUntil - Date.now()) / 864e5); return d > 0 ? [`Essai · ${d} j restant${d > 1 ? "s" : ""}`, "trial"] : ["Essai terminé", "off"]; }
    if (p.kids) return ["Enfant", "kid"];
    return ["Standard", "std"];
  }
  // Compte le temps de visionnage du profil en cours (vidéo en lecture uniquement)
  setInterval(() => {
    const v = document.querySelector("#player video"); if (!S.profile || !v || v.paused || $("#player").hidden) return;
    addUsage(S.profile.id, 0.5);
    const b = profileBlock(S.profile); if (b) { try { closePlayer(); } catch {} toast(b, 5000); go("#/profiles"); }
  }, 30000);

  function selectProfile(p) {
    S.profile = p; LS.set("current", p.id);
    S.lib = LS.get("lib." + p.id, []);
    S.prog = LS.get("prog." + p.id, []);
  }
  // Profils : enregistrés tout de suite sur le téléphone, puis envoyés aux autres appareils sans attendre
  const saveProfiles = () => { LS.set("profiles", S.profiles); clearTimeout(syncTimer); setTimeout(() => syncNow(), 300); };
  const saveAddons = (user = true) => { LS.set("addons", S.addons); if (user) { LS.set("addons_t", Date.now()); changed(); } };
  const saveLib = () => { if (S.profile) { LS.set("lib." + S.profile.id, S.lib); changed(); } };
  const saveProg = () => { if (S.profile) { LS.set("prog." + S.profile.id, S.prog); changed(); } };
  let syncTimer = null;
  function changed() { clearTimeout(syncTimer); syncTimer = setTimeout(() => syncNow(), 15000); }
  const inLib = (id) => S.lib.some((x) => x.id === id);
  function toggleLib(m) {
    const del = LS.get("libdel." + S.profile.id, {});
    if (inLib(m.id)) { S.lib = S.lib.filter((x) => x.id !== m.id); del[m.id] = Date.now(); }
    else { S.lib.unshift({ id: m.id, type: m.type, name: m.name, poster: m.poster, background: m.background, addedAt: Date.now() }); delete del[m.id]; }
    LS.set("libdel." + S.profile.id, del);
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

  async function getJson(url, timeout = 20000, headers = {}) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), timeout);
    try {
      const r = await fetch(url, { signal: ctl.signal, headers: { Accept: "application/json", ...headers } });
      if (!r.ok) throw new Error("HTTP " + r.status);
      const data = await r.json();
      return typeof data === "string" ? JSON.parse(data) : data;
    } finally { clearTimeout(t); }
  }


  // ------------------------------------------------------------------ TMDB (clé personnelle, synchronisée)
  const TMDB = {
    URL: "tmdb://fr", API: "https://api.themoviedb.org/3", IMG: "https://image.tmdb.org/t/p/",
    MOVIE: { Action: 28, Adventure: 12, Animation: 16, Comedy: 35, Crime: 80, Documentary: 99, Drama: 18, Family: 10751, Fantasy: 14,
      History: 36, Horror: 27, Mystery: 9648, Romance: 10749, "Sci-Fi": 878, Thriller: 53, War: 10752, Western: 37 },
    TV: { Action: 10759, Animation: 16, Comedy: 35, Crime: 80, Documentary: 99, Drama: 18, Family: 10751, Mystery: 9648, "Sci-Fi": 10765, War: 10768, Western: 37 },
  };
  const tmdbKey = () => LS.get("tmdb_key", null);
  // Relais TVO (clé TMDB gardée sur le serveur) : utilisé quand il n'y a pas de clé personnelle.
  const TMDB_PROXY = "https://vfxblubjsxwseacmslnu.supabase.co/functions/v1/tvo-tmdb";
  let proxyOk = LS.get("tmdb_proxy_ok", false);
  const tmdbOn = () => !!tmdbKey() || proxyOk;
  async function checkProxy() {
    try { const r = await fetch(`${TMDB_PROXY}?path=/configuration`); proxyOk = r.ok; } catch { /* hors ligne : garder l'état */ }
    LS.set("tmdb_proxy_ok", proxyOk); return proxyOk;
  }
  const tcat = (type, id, name, genres, search) => ({ type, id, name, extraRequired: [], extraSupported: [],
    extra: [...(genres ? [{ name: "genre", isRequired: false, options: Object.keys(genres) }] : []), ...(search ? [{ name: "search", isRequired: false, options: [] }] : []), { name: "skip", isRequired: false, options: [] }] });
  TMDB.addon = { url: TMDB.URL, manifest: { id: "tmdb", name: "TMDB", version: "3", description: "Catalogues, titres et résumés en français",
    types: ["movie", "series"], resources: [{ name: "catalog" }], idPrefixes: null, catalogs: [
      ...seasonCats(),
      tcat("movie", "latest", "Derniers films sortis"),
      tcat("movie", "kids", "Films pour enfants"), tcat("series", "kids", "Dessins animés et séries enfants"),
      tcat("movie", "trending", "Films tendance cette semaine"), tcat("series", "trending", "Séries tendance cette semaine"),
      tcat("movie", "popular", "Films populaires", TMDB.MOVIE, true), tcat("series", "popular", "Séries populaires", TMDB.TV, true),
      tcat("movie", "now_playing", "Actuellement au cinéma"), tcat("movie", "upcoming", "Bientôt au cinéma"),
      tcat("movie", "prov_8", "Nouveaux films sur Netflix"), tcat("series", "prov_8", "Nouvelles séries sur Netflix"),
      tcat("movie", "prov_119", "Nouveaux films sur Prime Video"), tcat("series", "prov_119", "Nouvelles séries sur Prime Video"),
      tcat("movie", "prov_337", "Nouveautés Disney+"), tcat("series", "prov_381", "Nouveautés Canal+"), tcat("series", "prov_350", "Nouveautés Apple TV+"),
      tcat("movie", "top_rated", "Films les mieux notés"), tcat("series", "top_rated", "Séries les mieux notées")] } };
  /** Add-ons vus par les catalogues : TMDB d'abord, catalogues Cinemeta masqués (Cinemeta garde les épisodes). */
  /** Administrateur : le profil marqué admin, sinon le premier profil. Il choisit les add-ons de chaque profil. */
  const isAdmin = (p) => !!p && (p.admin || (!S.profiles.some((x) => x.admin) && S.profiles[0] && S.profiles[0].id === p.id));
  const myAddons = () => { const off = (S.profile && S.profile.off) || []; return S.addons.filter((a) => !off.includes(a.manifest.id)); };
  function catalogAddons() {
    if (S.profile && S.profile.kids) {
      // Profil enfant : seulement les catalogues jeunesse
      return [{ ...TMDB.addon, manifest: { ...TMDB.addon.manifest, catalogs: TMDB.addon.manifest.catalogs.filter((c) => c.id === "kids") } }];
    }
    if (!tmdbOn()) return myAddons();
    return [TMDB.addon, ...myAddons().map((a) => (a.manifest.id === "com.linvo.cinemeta" ? { ...a, manifest: { ...a.manifest, catalogs: [] } } : a))];
  }
  async function tget(path, params = {}, key = tmdbKey()) {
    if (!key) {
      const q = new URLSearchParams({ path, language: "fr-FR", ...params }).toString();
      const url = `${TMDB_PROXY}?${q}`;
      if (cache.has(url)) return cache.get(url);
      const d = await getJson(url, 15000); cache.set(url, d); return d;
    }
    const bearer = key.startsWith("eyJ");
    const q = new URLSearchParams({ language: "fr-FR", ...params, ...(bearer ? {} : { api_key: key }) }).toString();
    const url = `${TMDB.API}${path}?${q}`;
    if (cache.has(url)) return cache.get(url);
    const d = await getJson(url, 15000, bearer ? { Authorization: "Bearer " + key } : {});
    cache.set(url, d);
    return d;
  }
  /** Catalogues d'événements selon la date (récents puis classiques). */
  function seasonCats() {
    const d = new Date(), mo = d.getMonth() + 1, day = d.getDate();
    const ev = mo === 12 || (mo === 11 && day >= 15) ? ["christmas", "Noël"] : mo === 10 || (mo === 11 && day <= 2) ? ["halloween", "Halloween"]
      : mo === 2 && day <= 15 ? ["valentine's day", "Saint-Valentin"] : (mo === 3 && day >= 15) || (mo === 4 && day <= 25) ? ["easter", "Pâques"]
      : mo === 7 || mo === 8 ? ["summer vacation", "Vacances d'été"] : mo === 1 && day <= 7 ? ["new year's eve", "Nouvel An"] : null;
    if (!ev) return [];
    return [tcat("movie", `ev_${ev[0]}_new`, `Films de ${ev[1]} récents`), tcat("movie", `ev_${ev[0]}_old`, `Classiques de ${ev[1]}`)];
  }
  const kwCache = {};
  async function keywordId(q) {
    if (kwCache[q]) return kwCache[q];
    const r = await tget("/search/keyword", { query: q });
    const hit = (r.results || []).find((k) => k.name.toLowerCase() === q) || (r.results || [])[0];
    return (kwCache[q] = hit ? String(hit.id) : null);
  }
  const tkind = (t) => (t === "series" ? "tv" : "movie");
  const timg = (p, size) => (p ? TMDB.IMG + size + p : null);
  const rating = (v) => (v > 0 ? v.toFixed(1) : null);
  function tpreview(o, type) {
    if (!o || !o.id) return null;
    const rev = Object.fromEntries(Object.entries(type === "series" ? TMDB.TV : TMDB.MOVIE).map(([k, v]) => [v, k]));
    const date = o.release_date || o.first_air_date || "";
    return { id: `tmdb:${type}:${o.id}`, type, name: o.title || o.name || "", poster: timg(o.poster_path, "w342"), background: timg(o.backdrop_path, "w780"),
      logo: null, description: o.overview || null, releaseInfo: date.slice(0, 4) || null, imdbRating: rating(o.vote_average), genres: (o.genre_ids || []).map((g) => rev[g]).filter(Boolean) };
  }
  async function tmdbCatalog(c, extra) {
    const k = tkind(c.type), page = String(Math.floor((Number(extra.skip) || 0) / 20) + 1);
    const g = extra.genre && (c.type === "series" ? TMDB.TV : TMDB.MOVIE)[extra.genre];
    const d = extra.search ? await tget(`/search/${k}`, { query: extra.search, page })
      : g ? await tget(`/discover/${k}`, { with_genres: g, sort_by: "popularity.desc", page, "vote_count.gte": "50" })
      : c.id === "trending" ? await tget(`/trending/${k}/week`, { page })
      : c.id === "now_playing" ? await tget("/movie/now_playing", { page, region: "FR" })
      : c.id === "upcoming" ? await tget("/movie/upcoming", { page, region: "FR" })
      : c.id === "kids" ? await tget(`/discover/${k}`, k === "tv" ? { page, with_genres: "10762", sort_by: "popularity.desc" }
          : { page, with_genres: "10751", without_genres: "27,53,80", certification_country: "FR", "certification.lte": "U", sort_by: "popularity.desc", "vote_count.gte": "50" })
      : c.id.startsWith("ev_") ? await (async () => { const [, q, kind] = c.id.split("_"); const kw = await keywordId(q); if (!kw) return { results: [] };
          return tget("/discover/movie", { page, with_keywords: kw, sort_by: kind === "new" ? "primary_release_date.desc" : "vote_average.desc",
            "primary_release_date.lte": today(), "vote_count.gte": kind === "new" ? "15" : "200" }); })()
      : c.id === "latest" ? await tget("/discover/movie", { page, region: "FR", sort_by: "primary_release_date.desc", "primary_release_date.lte": today(), "vote_count.gte": "20", with_release_type: "3|4|6" })
      : c.id.startsWith("pv_") ? await (async () => { const [, pid, sort, genre] = c.id.split("_");
          return tget(`/discover/${k}`, { page, watch_region: "FR", with_watch_providers: pid, with_watch_monetization_types: "flatrate", ...(genre ? { with_genres: genre } : {}),
            sort_by: sort === "new" ? (k === "tv" ? "first_air_date.desc" : "primary_release_date.desc") : sort === "top" ? "vote_average.desc" : "popularity.desc",
            [k === "tv" ? "first_air_date.lte" : "primary_release_date.lte"]: today(), "vote_count.gte": sort === "top" ? "200" : "5" }); })()
      : c.id.startsWith("prov_") ? await tget(`/discover/${k}`, { page, watch_region: "FR", with_watch_providers: c.id.slice(5), with_watch_monetization_types: "flatrate",
          sort_by: k === "tv" ? "first_air_date.desc" : "primary_release_date.desc", [k === "tv" ? "first_air_date.lte" : "primary_release_date.lte"]: today(), "vote_count.gte": "5" })
      : await tget(`/${k}/${c.id}`, { page });
    return (d.results || []).map((o) => tpreview(o, c.type)).filter(Boolean);
  }
  async function tmdbPerson(id) {
    const d = await tget(`/person/${id}`, { append_to_response: "combined_credits" });
    const cc = d.combined_credits || {}; const seen = new Set();
    const works = [...(cc.cast || []), ...(cc.crew || [])].filter((o) => o.media_type === "movie" || (o.media_type === "tv" && !(o.episode_count < 2) && !/self/i.test(o.character || "")))
      .sort((x, y) => (y.popularity || 0) - (x.popularity || 0))
      .map((o) => tpreview(o, o.media_type === "tv" ? "series" : "movie")).filter((m) => m && m.poster && !seen.has(m.id) && seen.add(m.id));
    return { name: d.name, photo: timg(d.profile_path, "w342"), bio: d.biography || "", works };
  }
  async function tmdbImdb(id) {
    const [, type, tid] = id.split(":");
    try { return (await tget(`/${tkind(type)}/${tid}/external_ids`)).imdb_id || null; } catch { return null; }
  }
  async function tmdbEnrich(type, imdb, tmdbId, base) {
    try {
      const k = tkind(type);
      let tid = tmdbId;
      if (!tid && imdb) {
        const f = await tget(`/find/${imdb}`, { external_source: "imdb_id" });
        const r = (k === "tv" ? f.tv_results : f.movie_results) || [];
        tid = r[0] && r[0].id;
      }
      if (!tid) return base;
      const d = await tget(`/${k}/${tid}`, { append_to_response: "credits,images", include_image_language: "fr,en,null" });
      const logos = (d.images && d.images.logos) || [];
      const logo = (logos.find((l) => l.iso_639_1 === "fr") || logos.find((l) => l.iso_639_1 === "en") || {}).file_path;
      const b = base || {};
      const date = d.release_date || d.first_air_date || "";
      const m = {
        id: imdb || b.id || `tmdb:${type}:${tid}`, type, name: d.title || d.name || b.name || "",
        poster: timg(d.poster_path, "w500") || b.poster || null, background: timg(d.backdrop_path, "w1280") || b.background || null,
        logo: timg(logo, "w500") || b.logo || null, description: d.overview || null,
        releaseInfo: b.releaseInfo || date.slice(0, 4) || null, imdbRating: b.imdbRating || rating(d.vote_average),
        genres: (d.genres || []).map((g) => g.name).filter(Boolean).length ? d.genres.map((g) => g.name) : (b.genres || []),
        runtime: d.runtime ? d.runtime + " min" : (d.episode_run_time && d.episode_run_time[0] ? d.episode_run_time[0] + " min" : b.runtime || null),
        cast: ((d.credits && d.credits.cast) || []).slice(0, 6).map((c) => c.name).filter(Boolean),
        people: ((d.credits && d.credits.cast) || []).slice(0, 15).map((c) => ({ id: String(c.id), name: c.name, photo: timg(c.profile_path, "w185"), role: c.character || null })),
        director: b.director || [], defaultVideoId: b.defaultVideoId || null, videos: b.videos || [],
      };
      if (!m.cast.length) m.cast = b.cast || [];
      if (k === "tv") {
        const seasons = (d.seasons || []).map((x) => x.season_number).filter((n) => n > 0);
        const eps = (await Promise.all(seasons.map((n) => tget(`/tv/${tid}/season/${n}`).catch(() => null)))).filter(Boolean).flatMap((x) => x.episodes || []);
        const by = Object.fromEntries(eps.map((e) => [`${e.season_number}:${e.episode_number}`, e]));
        if (!m.videos.length && imdb) m.videos = eps.map((e) => ({ id: `${imdb}:${e.season_number}:${e.episode_number}`, title: "", season: e.season_number, episode: e.episode_number, released: e.air_date || null, thumbnail: null, overview: null }));
        m.videos = m.videos.map((v) => {
          const e = by[`${v.season}:${v.episode}`]; if (!e) return v;
          const name = e.name && !/^[ÉE]pisode \d+$/.test(e.name) ? e.name : null;
          return { ...v, title: name || "Épisode " + (v.episode ?? ""), overview: e.overview || null, thumbnail: timg(e.still_path, "w300") || v.thumbnail, released: v.released || e.air_date || null };
        });
      }
      m.tmdbId = String(tid); m.tmdbKind = k;
      m.episodes = m.videos.filter((v) => v.season != null).sort((x, y) => ((x.season || 1e9) - (y.season || 1e9)) || ((x.episode || 0) - (y.episode || 0)));
      return m;
    } catch { return base; }
  }


  // ------------------------------------------------------------------ chaînes TV gratuites (Free-TV/IPTV, légal)
  const FREETV = { URL: "freetv://fr", PLAYLIST: "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8", list: null };
  FREETV.addon = { url: FREETV.URL, manifest: { id: "tvo.freetv.fr", name: "Chaînes TV gratuites (France)", version: "1",
    description: "Chaînes gratuites et légales en direct (liste Free-TV/IPTV).", types: ["tv"], idPrefixes: ["freetv:"],
    resources: [{ name: "catalog" }, { name: "meta" }, { name: "stream" }],
    catalogs: [] } };
  FREETV.CATS = [["gen", "Chaînes généralistes"], ["info", "Chaînes d'info"], ["sport", "Sport"], ["kids", "Jeunesse"], ["local", "Chaînes régionales et locales"], ["other", "Autres chaînes"]];
  FREETV.addon.manifest.catalogs = [...FREETV.CATS.map(([id, name]) => ({ type: "tv", id, name, extra: [], extraRequired: [], extraSupported: [] })),
    { type: "tv", id: "search", name: "Chaînes TV", extra: [{ name: "search", isRequired: true, options: [] }], extraRequired: [], extraSupported: [] }];
  function chanCategory(name) {
    const n = name.toLowerCase().replace(/^\d+\s*[-.:]?\s*/, "");
    if (/info|news|24|bfm|lci|cnews|lcp|sénat|senat|cgtn|euronews|africanews|inter/.test(n)) return "info";
    if (/sport|équipe|equipe/.test(n)) return "sport";
    if (/gulli|kids|junior|toon|enfant/.test(n)) return "kids";
    if (/^(tf1|france [2-5]|m6|arte|tmc|tfx|w9|6ter|cstar|c8|nrj|rmc|tv5|chérie|cherie)/.test(n)) return "gen";
    if (/tv |alsace|mulhouse|bretagne|provence|lyon|paris|azur|normandie|corse|antilles|réunion|reunion/.test(n)) return "local";
    return "other";
  }
  async function freeChannels() {
    if (FREETV.list) return FREETV.list;
    const txt = await (await fetch(FREETV.PLAYLIST)).text();
    const out = []; let info = null; const seen = new Set();
    txt.split(/\r?\n/).forEach((l) => {
      l = l.trim();
      if (l.startsWith("#EXTINF")) info = l;
      else if (l && !l.startsWith("#") && info) {
        const name = info.slice(info.lastIndexOf(",") + 1).replace(/[ⒼⓎⒹⓉⓈ]/g, "").trim();
        const logo = (info.match(/tvg-logo="([^"]*)"/) || [])[1] || null;
        const id = "freetv:" + name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
        const chno = parseInt((info.match(/tvg-chno="(\d+)"/) || [])[1], 10) || 999;
        if (!seen.has(id)) { seen.add(id); out.push({ id, name, logo, url: l, chno, cat: chanCategory(name) }); }
        info = null;
      }
    });
    return (FREETV.list = out.sort((a, b) => a.chno - b.chno || a.name.localeCompare(b.name)));
  }
  const freePreview = (c) => ({ id: c.id, type: "tv", name: c.name, poster: c.logo, background: null, logo: c.logo, description: "Chaîne en direct, gratuite.", releaseInfo: c.chno < 999 ? "Canal " + c.chno : null, imdbRating: null, genres: ["En direct"] });
  async function freeCatalog(catId, extra) {
    if (Number(extra.skip) > 0) return [];
    const q = (extra.search || "").toLowerCase();
    return (await freeChannels()).filter((c) => (q ? c.name.toLowerCase().includes(q) : catId === "fr" || c.cat === catId)).map(freePreview);
  }
  async function freeMeta(id) {
    const c = (await freeChannels()).find((x) => x.id === id); if (!c) return null;
    return { ...freePreview(c), runtime: null, videos: [], cast: [], director: [], defaultVideoId: null, episodes: [] };
  }
  async function freeStreams(id) {
    const c = (await freeChannels()).find((x) => x.id === id); if (!c) return [];
    const direct = /\.(m3u8|mp4|mpd)(\?|$)/i.test(c.url);
    return [{ addonName: FREETV.addon.manifest.name, name: c.name, title: direct ? "Direct" : "S'ouvre dans l'appli ou le site de la chaîne",
      url: direct ? c.url : null, externalUrl: direct ? null : c.url, infoHash: null, headers: {}, needsHeaders: false, subtitles: [], bingeGroup: null, quality: 0 }];
  }
  // Suggestions : [adresse, nom, description, groupe, étiquettes]
  const SUGGESTIONS = [
    ["m3u:http://mafreebox.freebox.fr/freeboxtv/playlist.m3u", "Freebox TV", "Toutes les chaînes de ta Freebox, avec leurs logos.", "Chaînes TV", "Abonnés Free · Wi-Fi Freebox"],
    ["https://opensubtitles-v3.strem.io/manifest.json", "OpenSubtitles", "Sous-titres français et toutes langues, ajoutés automatiquement au lecteur.", "Sous-titres", "Recommandé"],
    ["https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json", "Streaming Catalogs", "Rangées « Sur Netflix », « Sur Prime Video », « Sur Disney+ »…", "Catalogues", "Configurable"],
    ["https://anime-kitsu.strem.fun/manifest.json", "Anime Kitsu", "Catalogues et fiches d'animés (saisons, épisodes).", "Catalogues", "Animés"],
    ["https://94c8cb9f702d-tmdb-addon.baby-beamup.club/manifest.json", "TMDB Addon", "Catalogues TMDB supplémentaires. Facultatif : TVO intègre déjà TMDB en français.", "Catalogues", "Facultatif"],
    ["https://watchhub.strem.io/manifest.json", "WatchHub", "Sur chaque fiche, les plateformes officielles où regarder le titre.", "Où regarder", "Liens officiels"],
    ["https://v3-channels.strem.io/manifest.json", "YouTube", "Tes chaînes YouTube préférées dans TVO.", "Vidéos", ""],
  ];
  const SUG_GROUPS = [["Chaînes TV", "Regarder la télé en direct"], ["Sous-titres", "Afficher des sous-titres pendant la lecture"],
    ["Catalogues", "Ajouter des rangées de films et séries à l'accueil"], ["Où regarder", "Retrouver un titre sur les plateformes officielles"], ["Vidéos", "Autres contenus vidéo"]];
  const CATEGORIES = ["Catalogues", "Streaming", "Chaînes TV", "Sous-titres", "Autres"];
  function addonCategory(a) {
    const m = a.manifest, res = m.resources.map((r) => r.name);
    if ((m.types || []).some((t) => t === "tv" || t === "channel") || m.catalogs.some((c) => c.type === "tv" || c.type === "channel")) return "Chaînes TV";
    if (res.includes("stream")) return "Streaming";
    if (res.includes("catalog") || res.includes("meta")) return "Catalogues";
    if (res.includes("subtitles")) return "Sous-titres";
    return "Autres";
  }


  // ------------------------------------------------------------------ listes M3U de l'utilisateur (chaînes + logos)
  const M3U = { P: "m3u:", lists: new Map() };
  const isM3u = (s) => String(s).startsWith(M3U.P) || /\.m3u8?(\?|$)|get\.php|type=m3u/i.test(String(s).trim());
  const m3uHash = (u) => { let h = 0; for (const ch of u) h = (h * 31 + ch.charCodeAt(0)) | 0; return (h >>> 0).toString(16); };
  const slugify = (s) => s.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  function parseM3u(txt, h) {
    const out = []; let info = null; let hdr = {}; const seen = new Set();
    txt.split(/\r?\n/).forEach((l) => {
      l = l.trim();
      if (l.startsWith("#EXTINF")) { info = l; hdr = {}; }
      else if (l.startsWith("#EXTVLCOPT:http-user-agent=")) hdr["User-Agent"] = l.split("=").slice(1).join("=");
      else if (l.startsWith("#EXTVLCOPT:http-referrer=")) hdr.Referer = l.split("=").slice(1).join("=");
      else if (l && !l.startsWith("#") && info) {
        const attr = (n) => ((info.match(new RegExp(n + '="([^"]*)"')) || [])[1] || "").trim() || null;
        const raw = (info.slice(info.lastIndexOf(",") + 1).replace(/[ⒼⓎⒹⓉⓈ]/g, "").trim()) || attr("tvg-name") || "Chaîne";
        const num = parseInt(attr("tvg-chno"), 10) || parseInt((raw.match(/^(\d+)\s*[-.:]/) || [])[1], 10) || 9999;
        const name = raw.replace(/^\d+\s*[-.:]\s*/, "").replace(/\s*\((\d{3,4}p|\d+p)\)|\s*\[(geo-blocked|not 24\/7)\]/gi, "").trim() || raw;
        const id = `${M3U.P}${h}:${slugify(name)}`;
        if (attr("http-user-agent") && !hdr["User-Agent"]) hdr["User-Agent"] = attr("http-user-agent");
        if (attr("http-referrer") && !hdr.Referer) hdr.Referer = attr("http-referrer");
        if (!seen.has(id)) { seen.add(id); out.push({ id, name, logo: attr("tvg-logo"), tvgId: (attr("tvg-id") || "").replace(/@.*$/, ""), chno: num, url: l, group: attr("group-title") || "", headers: { ...hdr } }); }
        info = null;
      }
    });
    return out;
  }
  async function m3uChannels(addonUrl) {
    if (M3U.lists.has(addonUrl)) return M3U.lists.get(addonUrl);
    const url = addonUrl.slice(M3U.P.length);
    const txt = await (await fetch(url)).text();
    const list = parseM3u(txt, m3uHash(url)); M3U.lists.set(addonUrl, list); return list;
  }
  /** Logo automatique (dépôt public tv-logo/tv-logos) quand la liste n'en fournit pas. */
  function guessLogo(name) {
    const b = String(name || "").normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase()
      .replace(/\b(fhd|uhd|hd|sd|4k|hevc|h265)\b|\(.*?\)|\[.*?\]/g, " ").replace(/\+/g, " plus ").replace(/&/g, " and ")
      .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
    return b ? `https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/france/${b}-fr.png` : null;
  }
  function logoCandidates(name, given) {
    const b = String(name || "").normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase()
      .replace(/\b(fhd|uhd|hd|sd|4k|hevc|h265|backup|direct)\b|\(.*?\)|\[.*?\]|\|.*$/g, " ").replace(/\+/g, " plus ").replace(/&/g, " and ")
      .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
    const R = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/";
    const out = [given];
    if (b) out.push(`${R}france/${b}-fr.png`, `${R}france/${b.replace(/-/g, "")}-fr.png`, `${R}belgium/${b}-be.png`, `${R}switzerland/${b}-ch.png`,
      `${R}united-kingdom/${b}-uk.png`, `${R}united-states/${b}-us.png`, `${R}international/${b}-int.png`);
    return [...new Set(out.filter(Boolean))];
  }
  /** Cherche le logo officiel de chaque chaîne (une seule fois, mémorisé). */
  const LOGO_CACHE = LS.get("logos", {});
  const chanKey = (name) => slugify(String(name || ""));
  const loadsImage = (url) => new Promise((ok) => { const i = new Image(); const t = setTimeout(() => ok(false), 6000);
    i.onload = () => { clearTimeout(t); ok(i.naturalWidth > 8); }; i.onerror = () => { clearTimeout(t); ok(false); }; i.src = url; });
  async function resolveLogos(list, onFound) {
    const todo = list.filter((c) => !(chanKey(c.name) in LOGO_CACHE)).slice(0, 600);
    let i = 0;
    const worker = async () => {
      while (i < todo.length) {
        const c = todo[i++]; let found = null;
        for (const u of logoCandidates(c.name, c.logo)) { if (await loadsImage(u)) { found = u; break; } }
        LOGO_CACHE[chanKey(c.name)] = found || "";
        if (found && onFound) onFound(c, found);
      }
    };
    await Promise.all(Array.from({ length: 6 }, worker));
    LS.set("logos", LOGO_CACHE);
  }
  const bestLogo = (c) => LOGO_CACHE[chanKey(c.name)] || c.logo || null;

  // ------------------------------------------------------------------ programmes TV en direct (guide XMLTV, comme Molotov / Freebox)
  const EPG_URL = "https://xmltvfr.fr/xmltv/xmltv_tnt.xml";
  const EPG = { ids: {}, names: {}, t: 0, loading: null };
  (() => { const c = LS.get("epg", null); if (c && Date.now() - c.t < 6 * 3600e3) Object.assign(EPG, c); })();
  const normChan = (n) => String(n || "").normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase()
    .replace(/\((\d+p)\)|\b(hd|fhd|uhd|sd|4k|tv|tnt|channel|chaine)\b/g, "").replace(/\+/g, "plus").replace(/&/g, "and").replace(/[^a-z0-9]/g, "");
  const xmlTime = (v) => { const m = String(v).match(/^(\d{4})(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)\s*([+-]\d{4})?/); if (!m) return 0;
    const tz = m[7] ? (m[7][0] === "-" ? -1 : 1) * (Number(m[7].slice(1, 3)) * 60 + Number(m[7].slice(3))) : 0;
    return Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]) - tz * 60000; };
  const unxml = (t) => String(t || "").replace(/<!\[CDATA\[|\]\]>/g, "").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;|&#39;/g, "'").trim();
  function loadEpg(force) {
    if (!force && EPG.t && Date.now() - EPG.t < 3 * 3600e3) return Promise.resolve(EPG);
    if (EPG.loading) return EPG.loading;
    EPG.loading = (async () => {
      try {
        const txt = await (await fetch(EPG_URL)).text();
        const ids = {}, names = {}; const now = Date.now(), from = now - 2 * 3600e3, to = now + 18 * 3600e3;
        for (const m of txt.matchAll(/<channel id="([^"]+)">([\s\S]*?)<\/channel>/g)) {
          const id = m[1]; ids[id] = [];
          for (const d of m[2].matchAll(/<display-name[^>]*>([\s\S]*?)<\/display-name>/g)) names[normChan(unxml(d[1]))] = id;
          names[normChan(id.replace(/\.[a-z]{2}$/i, ""))] = id;
        }
        for (const m of txt.matchAll(/<programme start="([^"]+)" stop="([^"]+)" channel="([^"]+)"[^>]*>([\s\S]*?)<\/programme>/g)) {
          const a = xmlTime(m[1]), b = xmlTime(m[2]); if (b < from || a > to || !ids[m[3]]) continue;
          const body = m[4], g = (re) => unxml((body.match(re) || [])[1]);
          ids[m[3]].push({ a, b, t: g(/<title[^>]*>([\s\S]*?)<\/title>/), s: g(/<sub-title[^>]*>([\s\S]*?)<\/sub-title>/).slice(0, 80),
            c: g(/<category[^>]*>([\s\S]*?)<\/category>/), i: (body.match(/<icon src="([^"]+)"/) || [])[1] || "", d: g(/<desc[^>]*>([\s\S]*?)<\/desc>/).slice(0, 260) });
        }
        Object.values(ids).forEach((l) => l.sort((x, y) => x.a - y.a));
        Object.assign(EPG, { ids, names, t: Date.now() });
        LS.set("epg", { ids, names, t: EPG.t });
      } catch { /* pas de guide : les chaînes restent lisibles */ }
      EPG.loading = null; return EPG;
    })();
    return EPG.loading;
  }
  function epgFor(ch) {
    const tid = ch.tvgId && Object.keys(EPG.ids).find((k) => k.toLowerCase() === String(ch.tvgId).toLowerCase());
    const id = tid || EPG.names[normChan(ch.name)];
    return id ? EPG.ids[id] || [] : [];
  }
  function nowNext(ch) {
    const l = epgFor(ch), n = Date.now(); const i = l.findIndex((p) => p.a <= n && p.b > n);
    if (i < 0) return null;
    return { now: l[i], next: l[i + 1] || null, pct: Math.round(((n - l[i].a) / (l[i].b - l[i].a)) * 100), list: l.slice(i, i + 8) };
  }
  const hm = (t) => new Date(t).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });

  // Suggestions de chaînes gratuites et légales (listes publiques sur GitHub : iptv-org, Free-TV)
  const LIVE_SUGG = [
    ["https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8", "TNT gratuite", "Les chaînes françaises en accès libre : France TV, Arte, LCP, franceinfo…", "🇫🇷", "#2f5bff", "#e11d48"],
    ["https://iptv-org.github.io/iptv/countries/fr.m3u", "France · toutes les chaînes libres", "Chaînes nationales, régionales et thématiques diffusées gratuitement", "📺", "#1d4ed8", "#0ea5e9"],
    ["https://iptv-org.github.io/iptv/categories/news.m3u", "Info du monde", "Chaînes d'information en continu du monde entier", "🌍", "#0f766e", "#22c55e"],
    ["https://iptv-org.github.io/iptv/categories/kids.m3u", "Enfants", "Dessins animés et programmes jeunesse en direct", "🧸", "#f59e0b", "#ec4899"],
    ["https://iptv-org.github.io/iptv/categories/music.m3u", "Musique", "Clips et concerts en continu", "🎵", "#7c3aed", "#db2777"],
    ["https://iptv-org.github.io/iptv/countries/be.m3u", "Belgique", "RTBF, chaînes belges en accès libre", "🇧🇪", "#111827", "#eab308"],
    ["https://iptv-org.github.io/iptv/countries/ch.m3u", "Suisse", "RTS et chaînes suisses en accès libre", "🇨🇭", "#dc2626", "#7f1d1d"],
    ["http://mafreebox.freebox.fr/freeboxtv/playlist.m3u", "Freebox TV", "Abonnés Free : toutes tes chaînes, depuis le Wi-Fi de ta Freebox", "📡", "#991b1b", "#111827"],
  ];
  const liveInstalled = (u) => S.addons.some((a) => a.url === M3U.P + u);
  function liveSuggHTML() {
    return `<div class="lsugg">${LIVE_SUGG.map(([u, n, d, ic, c1, c2]) => `<div class="lscard" style="--c1:${c1};--c2:${c2}">
      <span class="lsic">${ic}</span><b>${esc(n)}</b><span class="small">${esc(d)}</span>
      <button class="btn sm ${liveInstalled(u) ? "" : "primary"}" data-live="${esc(u)}" data-name="${esc(n)}" ${liveInstalled(u) ? "disabled" : ""}>${liveInstalled(u) ? "✓ Ajoutée" : "+ Ajouter"}</button></div>`).join("")}</div>`;
  }
  async function addLive(u, n) {
    toast("Ajout de « " + n + " »…");
    try { const a = await installM3u(u, n); const i = S.addons.findIndex((x) => x.url === a.url); if (i >= 0) S.addons[i] = a; else S.addons.push(a); saveAddons(); cache.clear(); toast("Chaînes ajoutées ✓"); screenLive(); }
    catch (e) { toast(e.message || "Liste injoignable", 4000); }
  }
  const m3uUsesGroups = (list) => new Set(list.map((c) => c.group).filter(Boolean)).size > 1;
  const m3uCat = (c, g) => (g ? c.group || "Autres" : chanCategory(c.name));
  async function installM3u(input, name) {
    const url = String(input).trim().replace(/^m3u:/, ""); const addonUrl = M3U.P + url;
    M3U.lists.delete(addonUrl);
    setTimeout(() => m3uChannels(addonUrl).then((l) => resolveLogos(l)).catch(() => {}), 1500);
    let list;
    try { list = await m3uChannels(addonUrl); } catch (e) { throw new Error(/mafreebox/.test(url) ? "Freebox introuvable : connecte-toi au Wi-Fi de ta Freebox" : e.message); }
    if (!list.length) throw new Error("liste vide ou illisible");
    const g = m3uUsesGroups(list);
    const cats = g ? [...new Set(list.map((c) => c.group || "Autres"))].slice(0, 40).map((x) => [x, x]) : FREETV.CATS.filter(([id]) => list.some((c) => chanCategory(c.name) === id));
    return { url: addonUrl, manifest: { id: "tvo.m3u." + m3uHash(url), name: name || (/mafreebox/.test(url) ? `Freebox TV (${list.length} chaînes)` : `Ma liste M3U (${list.length} chaînes)`), version: "1", description: url,
      types: ["tv"], idPrefixes: [`${M3U.P}${m3uHash(url)}:`], resources: [{ name: "catalog" }, { name: "meta" }, { name: "stream" }],
      catalogs: [...cats.map(([id, n]) => ({ type: "tv", id, name: n, extra: [], extraRequired: [], extraSupported: [] })),
        { type: "tv", id: "search", name: "Chaînes M3U", extra: [{ name: "search", isRequired: true, options: [] }], extraRequired: [], extraSupported: [] }] } };
  }
  const m3uPreview = (c) => ({ id: c.id, type: "tv", name: c.name, tvgId: c.tvgId || "", poster: bestLogo(c), background: null, logo: bestLogo(c),
    description: "Chaîne en direct" + (c.group ? " · " + c.group : ""), releaseInfo: c.chno < 9999 ? "Canal " + c.chno : null, imdbRating: null, genres: ["En direct"] });
  async function m3uCatalog(a, catId, extra) {
    if (Number(extra.skip) > 0) return [];
    const list = await m3uChannels(a.url); const g = m3uUsesGroups(list); const q = (extra.search || "").toLowerCase();
    return list.filter((c) => (q ? c.name.toLowerCase().includes(q) : m3uCat(c, g) === catId)).sort((x, y) => x.chno - y.chno || x.name.localeCompare(y.name)).map(m3uPreview);
  }
  const m3uAddonFor = (id) => S.addons.find((a) => a.url.startsWith(M3U.P) && id.startsWith(`${M3U.P}${m3uHash(a.url.slice(M3U.P.length))}:`));
  async function m3uMeta(id) {
    const a = m3uAddonFor(id); if (!a) return null;
    const c = (await m3uChannels(a.url)).find((x) => x.id === id); if (!c) return null;
    return { ...m3uPreview(c), runtime: null, videos: [], cast: [], director: [], people: [], defaultVideoId: null, episodes: [] };
  }
  async function m3uStreams(id) {
    const a = m3uAddonFor(id); if (!a) return [];
    const c = (await m3uChannels(a.url)).find((x) => x.id === id); if (!c) return [];
    const web = /youtube\.com|youtu\.be|dailymotion|twitch\.tv/i.test(c.url);
    const rtsp = /^rtsp:/i.test(c.url);
    return [{ addonName: a.manifest.name, name: c.name, title: web ? "S'ouvre dans l'appli de la chaîne" : rtsp ? "S'ouvre dans VLC" : "Direct", url: web || rtsp ? null : c.url,
      externalUrl: web ? c.url : rtsp ? (isIOS ? "vlc-x-callback://x-callback-url/stream?url=" + enc(c.url) : "vlc://" + c.url) : null,
      infoHash: null, headers: c.headers, needsHeaders: Object.keys(c.headers).length > 0, subtitles: [], bingeGroup: null, quality: 0 }];
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
      configurable: !!(o.behaviorHints && (o.behaviorHints.configurable || o.behaviorHints.configurationRequired)),
    };
  }
  async function installAddon(input) {
    if (String(input).startsWith(FREETV.URL)) return FREETV.addon;
    if (isM3u(input)) { const old = S.addons.find((a) => a.url === M3U.P + String(input).replace(/^m3u:/, "")); return installM3u(input, old && old.manifest.name); }
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
    if (a.url === TMDB.URL) return tmdbCatalog(c, extra);
    if (a.url === FREETV.URL) return freeCatalog(c.id, extra);
    if (a.url.startsWith(M3U.P)) return m3uCatalog(a, c.id, extra);
    const keys = Object.keys(extra);
    const ex = keys.length ? "/" + keys.map((k) => `${k}=${enc(extra[k])}`).join("&") : "";
    const url = `${a.url}/catalog/${c.type}/${enc(c.id)}${ex}.json`;
    if (cache.has(url)) return cache.get(url);
    const d = await getJson(url);
    const seen = new Set();
    const list = (d.metas || []).map((m) => preview(m, c.type)).filter((m) => m && !seen.has(m.id) && seen.add(m.id))
      .map((m) => (a.manifest.id === "com.linvo.cinemeta" ? { ...m, description: null } : m)); // pas de résumés anglais
    cache.set(url, list);
    return list;
  }
  async function meta(type, id) {
    const k = type + "/" + id;
    if (metaCache.has(k)) return metaCache.get(k);
    if (id.startsWith("freetv:")) return freeMeta(id);
    if (id.startsWith(M3U.P)) return m3uMeta(id);
    const tmdbId = id.startsWith("tmdb:") ? id.split(":")[2] : null;
    const realId = tmdbId ? (await tmdbImdb(id)) || id : id;
    let m = metaCache.get(type + "/" + realId) || (realId.startsWith("tmdb:") ? null : await addonMeta(type, realId));
    if (tmdbOn()) m = await tmdbEnrich(type, realId.startsWith("tt") ? realId : null, tmdbId, m);
    if (m) { metaCache.set(k, m); metaCache.set(type + "/" + m.id, m); }
    return m;
  }
  async function addonMeta(type, id) {
    for (const a of myAddons().filter((x) => supports(x, "meta", type, id))) {
      try {
        const d = await getJson(`${a.url}/meta/${type}/${enc(id)}.json`);
        const o = d && d.meta; if (!o) continue;
        const p = preview(o, type); if (!p) continue;
        const videos = (o.videos || []).filter((v) => v && v.id).map((v) => ({
          id: v.id, title: v.title || v.name || "", season: v.season ?? null, episode: v.episode ?? v.number ?? null,
          released: v.released || null, thumbnail: v.thumbnail || null, overview: v.overview || v.description || null,
        }));
        if (a.manifest.id === "com.linvo.cinemeta") { p.description = null; videos.forEach((v) => (v.overview = null)); }
        const m = { ...p, people: [], runtime: o.runtime || null, videos, cast: (o.cast || []).slice(0, 6), director: o.director || [],
          defaultVideoId: (o.behaviorHints && o.behaviorHints.defaultVideoId) || null };
        m.episodes = videos.filter((v) => v.season != null).sort((x, y) => ((x.season || 1e9) - (y.season || 1e9)) || ((x.episode || 0) - (y.episode || 0)));
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
    const list = myAddons().filter((a) => supports(a, "stream", type, videoId));
    return { total: list.length, done: Promise.all(list.map(async (a) => {
      let items = [];
      if (a.url === FREETV.URL || a.url.startsWith(M3U.P)) { try { items = a.url === FREETV.URL ? await freeStreams(videoId) : await m3uStreams(videoId); } catch { /* */ } onAddon && onAddon(items, a); return items; }
      try { items = ((await getJson(`${a.url}/stream/${type}/${enc(videoId)}.json`, 25000)).streams || []).map((s) => parseStream(s, a.manifest.name)).filter(Boolean); }
      catch { /* ignore */ }
      onAddon && onAddon(items, a);
      return items;
    })).then((x) => x.flat()) };
  }
  async function subtitles(type, videoId) {
    const list = myAddons().filter((a) => supports(a, "subtitles", type, videoId));
    const res = await Promise.all(list.map(async (a) => {
      try { return ((await getJson(`${a.url}/subtitles/${type}/${enc(videoId)}.json`, 6000)).subtitles || []).filter((s) => s.url).map((s) => ({ url: s.url, lang: s.lang || "und" })); }
      catch { return []; }
    }));
    return res.flat();
  }

  async function refreshAddons() {
    if (S.addons.some((a) => a.url === FREETV.URL)) { S.addons = S.addons.filter((a) => a.url !== FREETV.URL); saveAddons(); }
    const fresh = await Promise.all(S.addons.map(async (a) => { try { return await installAddon(a.url); } catch { return a; } }));
    S.addons = fresh; saveAddons(false);
  }


  // ------------------------------------------------------------------ synchronisation TV ⇄ téléphone
  const SB = { url: "https://vfxblubjsxwseacmslnu.supabase.co/rest/v1/rpc/", key: "sb_publishable_HojETqgwpBMQ_cjDzMNaAg_WGCFMaED" };
  const syncState = { status: "", busy: null };
  function syncId() {
    let id = LS.get("sync_id", null);
    if (!id) { id = (crypto.randomUUID ? crypto.randomUUID() : "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (c) => (c ^ (Math.random() * 16) >> (c / 4)).toString(16))); LS.set("sync_id", id); }
    return id;
  }
  async function rpc(fn, body, token) {
    const r = await fetch(SB.url + fn, { method: "POST", headers: { apikey: SB.key, Authorization: "Bearer " + (token || SB.key), "Content-Type": "application/json" }, body: JSON.stringify(body) });
    if (!r.ok) { let m = "HTTP " + r.status; try { const j = await r.json(); if (j && j.message) m = j.message; } catch {} throw new Error(m); }
    const t = await r.text();
    return t ? JSON.parse(t) : null;
  }
  // ------------------------------------------------------------------ compte Google / Apple (Supabase Auth)
  const AUTH_BASE = "https://vfxblubjsxwseacmslnu.supabase.co/auth/v1";
  const REDIRECT = "tvo://auth";
  const account = () => LS.get("auth", null);
  function signIn(provider) {
    location.href = `${AUTH_BASE}/authorize?provider=${provider}&redirect_to=${encodeURIComponent(REDIRECT)}`;
  }
  /** Fournisseurs réellement activés côté serveur (les autres boutons sont masqués). */
  let AUTH_PROVIDERS = LS.get("authprov", { google: false, apple: false, email: true });
  fetch(`${AUTH_BASE}/settings`, { headers: { apikey: SB.key } }).then((r) => r.json()).then((d) => {
    const e = d.external || {}; AUTH_PROVIDERS = { google: !!e.google, apple: !!e.apple, email: !!e.email }; LS.set("authprov", AUTH_PROVIDERS);
  }).catch(() => {});
  async function signInEmail(email) {
    const r = await fetch(`${AUTH_BASE}/otp?redirect_to=${encodeURIComponent(REDIRECT)}`, { method: "POST", headers: { apikey: SB.key, "Content-Type": "application/json" },
      body: JSON.stringify({ email, create_user: true }) });
    if (!r.ok) throw new Error(r.status === 429 ? "Trop de demandes, réessaie dans quelques minutes." : "Envoi impossible.");
  }
  // ------------------------------------------------------------------ compte TVO (identifiant + mot de passe, géré par TVO)
  const acct = () => LS.get("acct", null);
  const cleanUser = (u) => String(u || "").trim().toLowerCase();
  /** Mêmes règles que le serveur (le serveur revérifie de toute façon). */
  function passProblem(user, p) {
    const l = String(p || "").toLowerCase();
    if (l.length < 10) return "Mot de passe : 10 caractères minimum";
    if (!/[a-zà-ÿ]/i.test(p) || !/[0-9]/.test(p)) return "Mot de passe : mélange des lettres et des chiffres";
    if (user && l.includes(String(user).toLowerCase())) return "Le mot de passe ne doit pas contenir ton identifiant";
    if (/^(.)\1+$/.test(l) || /(0123|1234|2345|3456|4567|5678|6789|azert|qwert|abcd|motdepasse|password|soleil|bonjour|doudou|loulou|chouchou|marseille|football|tvo)/.test(l)) return "Mot de passe trop facile à deviner";
    return null;
  }
  function passScore(p) {
    p = String(p || ""); let s = 0;
    if (p.length >= 10) s++; if (p.length >= 14) s++;
    if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++; if (/[0-9]/.test(p)) s++; if (/[^A-Za-z0-9]/.test(p)) s++;
    return Math.min(4, s);
  }
  /** Vérifie si le mot de passe figure dans une fuite connue (Have I Been Pwned) :
   *  seuls les 5 premiers caractères de son empreinte partent sur Internet, jamais le mot de passe. */
  async function passLeaked(p) {
    try {
      const b = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(p));
      const h = [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("").toUpperCase();
      const t = await (await fetch("https://api.pwnedpasswords.com/range/" + h.slice(0, 5), { headers: { "Add-Padding": "true" } })).text();
      return t.split("\n").some((line) => { const [suf, n] = line.trim().split(":"); return suf === h.slice(5) && Number(n) > 0; });
    } catch { return false; }
  }
  async function checkNewPass(user, pass) {
    const pb = passProblem(user, pass); if (pb) throw new Error(pb);
    if (await passLeaked(pass)) throw new Error("Ce mot de passe a déjà fuité sur Internet : choisis-en un autre");
  }
  async function acctRegister(user, pass) {
    user = cleanUser(user);
    if (!/^[a-z0-9._-]{3,24}$/.test(user)) throw new Error("Identifiant : 3 à 24 caractères (lettres, chiffres, . _ -)");
    await checkNewPass(user, pass);
    await syncNow();
    const id = await rpc("tvo_register", { p_user: user, p_pass: pass, p_id: syncId() });
    if (id && id !== syncId()) LS.set("sync_id", id);
    LS.set("acct", { user }); await syncNow();
  }
  async function acctLogin(user, pass) {
    user = cleanUser(user);
    const id = await rpc("tvo_login", { p_user: user, p_pass: pass });
    if (!id) throw new Error("Identifiant ou mot de passe incorrect");
    LS.set("sync_id", id); LS.set("acct", { user });
    await syncNow();
  }
  function acctLogout() {
    LS.set("acct", null); LS.set("auth", null); LS.del("sync_id");
    toast("Déconnecté. Ce téléphone n'est plus synchronisé avec ton compte.");
  }
  function signOut() { LS.set("auth", null); toast("Déconnecté. Tes données restent sur ce téléphone."); }
  async function authToken() {
    const a = account(); if (!a) return null;
    if (Date.now() < a.exp - 60000) return a.access;
    try {
      const r = await fetch(`${AUTH_BASE}/token?grant_type=refresh_token`, { method: "POST", headers: { apikey: SB.key, "Content-Type": "application/json" }, body: JSON.stringify({ refresh_token: a.refresh }) });
      if (!r.ok) throw 0;
      const d = await r.json();
      LS.set("auth", { ...a, access: d.access_token, refresh: d.refresh_token, exp: Date.now() + d.expires_in * 1000 });
      return d.access_token;
    } catch { return null; }
  }
  /** Relie ce compte à l'espace de synchro : le même sur tous les appareils connectés au compte. */
  async function linkAccount() {
    const t = await authToken(); if (!t) return;
    const id = await rpc("tvo_account_link", { p_id: syncId() }, t);
    if (id && id !== syncId()) LS.set("sync_id", id);
    await syncNow();
  }
  async function approveTv(code, key) {
    code = String(code || "").replace(/\D/g, "");
    if (code.length !== 6) { toast("Code à 6 chiffres attendu."); return false; }
    await syncNow();
    let ok = false, err = "";
    try { ok = await rpc("tvo_login_approve", { p_code: code, p_id: syncId(), p_key: key || null }); } catch (e) { err = String(e.message || ""); }
    toast(ok ? "TV connectée ✓" : /429|essais|P0001|400/.test(err) ? "Trop d'essais. Réessaie dans 10 minutes." : "Code invalide ou expiré."); return !!ok;
  }
  async function handleUrl(url) {
    if (!url || !url.startsWith("tvo://")) return;
    const u = new URL(url.replace("tvo://", "https://tvo.app/"));
    const q = new URLSearchParams((u.hash || "").slice(1) + "&" + u.search.slice(1));
    if (u.pathname === "/auth") {
      if (!q.get("access_token")) { toast(q.get("error_description") || "Connexion annulée."); return; }
      let email = "";
      try { email = (await (await fetch(`${AUTH_BASE}/user`, { headers: { apikey: SB.key, Authorization: "Bearer " + q.get("access_token") } })).json()).email || ""; } catch {}
      LS.set("auth", { access: q.get("access_token"), refresh: q.get("refresh_token"), exp: Date.now() + Number(q.get("expires_in") || 3600) * 1000, email });
      try { await linkAccount(); toast("Connecté ✓ Tes appareils sont synchronisés."); } catch { toast("Connecté, synchro à réessayer."); }
      if (location.hash.startsWith("#/settings")) route();
    } else if (u.pathname === "/login" && q.get("code")) {
      const k = q.get("k");
      if (!/^[0-9a-f]{64}$/.test(k || "")) { toast("QR code non reconnu."); return; }
      if (confirm("Connecter ce téléviseur à ton compte TVO ?\n\nNe confirme que si ce QR code s'affiche sur TA télévision.")) approveTv(q.get("code"), k);
    }
  }
  try {
    const App = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.App;
    if (App) { App.addListener("appUrlOpen", (e) => handleUrl(e.url)); App.getLaunchUrl().then((r) => r && handleUrl(r.url)).catch(() => {}); }
  } catch {}

  function exportDoc() {
    const profiles = {};
    S.profiles.forEach((p) => (profiles[p.id] = { id: p.id, name: p.name, color: p.color, t: p.t || 0, ...(p.off && p.off.length ? { off: p.off } : {}), ...(p.admin ? { admin: true } : {}), ...(p.kids ? { kids: true } : {}), ...(p.pin ? { pin: p.pin } : {}), ...(p.icon ? { icon: p.icon } : {}), ...(p.disabled ? { disabled: true } : {}), ...(p.trialUntil ? { trialUntil: p.trialUntil, trialDays: p.trialDays || null } : {}), ...(p.limit ? { limit: p.limit } : {}) }));
    Object.entries(LS.get("profdel", {})).forEach(([id, t]) => { if (!profiles[id]) profiles[id] = { id, name: "", color: "#8B5CF6", t, del: true }; });
    const lib = {}, prog = {};
    S.profiles.forEach((p) => {
      const m = {};
      LS.get("lib." + p.id, []).forEach((it) => (m[it.id] = { item: it, t: it.addedAt || 0 }));
      Object.entries(LS.get("libdel." + p.id, {})).forEach(([id, t]) => { if (!m[id] || m[id].t < t) m[id] = { item: (m[id] && m[id].item) || { id, type: "movie", name: "" }, t, del: true }; });
      lib[p.id] = m;
      const pm = {}; LS.get("prog." + p.id, []).forEach((x) => (pm[x.videoId] = x)); prog[p.id] = pm;
    });
    return { v: 1, profiles, addons: S.addons, addonsT: LS.get("addons_t", 0), lib, prog, tmdbKey: tmdbKey(), tmdbT: LS.get("tmdb_t", 0), home: homeLayout() };
  }
  function mergeDocs(a, b) {
    const mm = (x = {}, y = {}, t) => { const o = { ...x }; Object.entries(y).forEach(([k, v]) => { if (!o[k] || t(v) > t(o[k])) o[k] = v; }); return o; };
    const profiles = mm(a.profiles, b.profiles, (p) => p.t || 0);
    const pids = new Set([...Object.keys(a.lib || {}), ...Object.keys(b.lib || {}), ...Object.keys(a.prog || {}), ...Object.keys(b.prog || {})]);
    const lib = {}, prog = {};
    pids.forEach((pid) => {
      if (profiles[pid] && profiles[pid].del) return;
      lib[pid] = mm((a.lib || {})[pid], (b.lib || {})[pid], (l) => l.t || 0);
      const pm = mm((a.prog || {})[pid], (b.prog || {})[pid], (p) => p.updatedAt || 0);
      prog[pid] = Object.fromEntries(Object.values(pm).sort((x, y) => y.updatedAt - x.updatedAt).slice(0, 400).map((p) => [p.videoId, p]));
    });
    const useB = (b.addonsT || 0) > (a.addonsT || 0) && b.addons && b.addons.length;
    const tB = (b.tmdbT || 0) > (a.tmdbT || 0);
    return { v: 1, profiles, addons: useB ? b.addons : a.addons, addonsT: useB ? b.addonsT : (a.addonsT || 0), lib, prog,
      tmdbKey: tB ? b.tmdbKey || null : a.tmdbKey || null, tmdbT: tB ? b.tmdbT : (a.tmdbT || 0),
      home: ((b.home && b.home.t) || 0) > ((a.home && a.home.t) || 0) ? b.home : (a.home || null) };
  }
  function importDoc(d) {
    const order = S.profiles.map((p) => p.id);
    S.profiles = Object.values(d.profiles || {}).filter((p) => !p.del)
      .sort((x, y) => ((order.indexOf(x.id) + 1 || 1e9) - (order.indexOf(y.id) + 1 || 1e9)))
      .map((p) => { const { del, ...rest } = p; return { ...rest, t: p.t || 0 }; });
    LS.set("profiles", S.profiles);
    if (S.profile) { const cur = S.profiles.find((x) => x.id === S.profile.id); if (cur) S.profile = cur; }
    LS.set("profdel", Object.fromEntries(Object.values(d.profiles || {}).filter((p) => p.del).map((p) => [p.id, p.t])));
    S.profiles.forEach((p) => {
      const l = Object.values((d.lib || {})[p.id] || {});
      LS.set("lib." + p.id, l.filter((x) => !x.del).map((x) => x.item).sort((x, y) => (y.addedAt || 0) - (x.addedAt || 0)));
      LS.set("libdel." + p.id, Object.fromEntries(l.filter((x) => x.del).map((x) => [x.item.id, x.t])));
      LS.set("prog." + p.id, Object.values((d.prog || {})[p.id] || {}).sort((x, y) => y.updatedAt - x.updatedAt).slice(0, 400));
    });
    if (d.home && (d.home.t || 0) > homeLayout().t) LS.set("home", d.home);
    if ((d.tmdbT || 0) > LS.get("tmdb_t", 0)) { LS.set("tmdb_key", d.tmdbKey || null); LS.set("tmdb_t", d.tmdbT); cache.clear(); metaCache.clear(); }
    if ((d.addonsT || 0) > LS.get("addons_t", 0) && d.addons && d.addons.length) { S.addons = d.addons; LS.set("addons", S.addons); LS.set("addons_t", d.addonsT); cache.clear(); }
    if (S.profile) { const p = S.profiles.find((x) => x.id === S.profile.id); if (p) selectProfile(p); else S.profile = null; }
  }
  async function syncNow() {
    if (syncState.busy) return syncState.busy;
    syncState.busy = (async () => {
      try {
        const id = syncId();
        const before = JSON.stringify(exportDoc());
        const remote = (await rpc("tvo_pull", { p_id: id })) || {};
        const merged = mergeDocs(exportDoc(), remote);
        importDoc(merged);
        await rpc("tvo_push", { p_id: id, p_doc: merged });
        syncState.status = "Synchronisé à " + new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
        return JSON.stringify(exportDoc()) !== before;
      } catch { syncState.status = "Synchronisation impossible (pas de connexion ?)"; return false; }
      finally { syncState.busy = null; }
    })();
    return syncState.busy;
  }
  async function pairCreate() { await syncNow(); return rpc("tvo_pair_create", { p_id: syncId() }); }
  async function pairClaim(code) {
    const id = await rpc("tvo_pair_claim", { p_code: code });
    if (!id) return false;
    LS.set("sync_id", id); await syncNow(); return true;
  }

  // ------------------------------------------------------------------ UI helpers
  const app = $("#app");
  const tabs = $("#tabs");
  function toast(msg, ms = 2600) {
    const t = $("#toast"); t.textContent = msg; t.hidden = false;
    clearTimeout(toast._t); toast._t = setTimeout(() => (t.hidden = true), ms);
  }
  const go = (hash) => { location.hash = hash; };
  const AVATAR_ICONS = ["", "🦊", "🐼", "🐯", "🦁", "🐸", "🐙", "🦄", "🐶", "🐱", "🚀", "⭐", "🎮", "⚽", "🎧", "🍿"];
  const avatar = (p, cls = "") => `<div class="avatar ${cls} ${p.icon ? "emoji" : ""}" style="background:linear-gradient(135deg, ${p.color}, ${p.color}99)">${p.icon ? esc(p.icon) : esc((p.name || "?").slice(0, 1).toUpperCase())}</div>`;
  async function pinHash(pin) {
    try { const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("tvo-pin:" + pin)); return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join(""); }
    catch { let h = 0; for (const ch of "tvo-pin:" + pin) h = (h * 31 + ch.charCodeAt(0)) | 0; return "j" + (h >>> 0).toString(16); }
  }
  const BIO = () => window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.NativeBiometric;
  async function bioAvailable() { try { const b = BIO(); if (!b) return null; const r = await b.isAvailable({ useFallback: false }); return r && r.isAvailable ? r : null; } catch { return null; } }
  const bioName = (r) => (r && (r.biometryType === 2 || r.biometryType === 4) ? "Face ID" : r && r.biometryType === 1 ? "Touch ID" : "l'empreinte");
  /** Déverrouille un profil protégé : Face ID / Touch ID si activé, sinon code. Renvoie true si c'est bon. */
  function unlockProfile(p, title) {
    return new Promise(async (done) => {
      const bio = await bioAvailable();
      const box = document.createElement("div"); box.className = "pin-sheet";
      box.innerHTML = `<div class="pin-card"><div class="pin-av">${avatar(p)}</div><b>${esc(title || p.name)}</b><span class="pin-msg" id="pinMsg">Saisis le code à 4 chiffres</span>
        <div class="pin-dots"><i></i><i></i><i></i><i></i></div>
        <div class="pin-pad">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map((k) => `<button data-k="${k}">${k}</button>`).join("")}
          ${bio ? `<button class="pin-bio" data-bio aria-label="${bioName(bio)}">${bioName(bio) === "Face ID" ? "🙂" : "☝️"}</button>` : `<button disabled></button>`}<button data-k="0">0</button><button data-k="del" aria-label="Effacer">⌫</button></div>
        ${bio ? `<button class="btn block" data-bio>Utiliser ${bioName(bio)}</button>` : ""}<button class="btn block ghost" data-x>Annuler</button></div>`;
      document.body.appendChild(box);
      let v = "", busy = false;
      const msg = box.querySelector("#pinMsg"), dots = box.querySelectorAll(".pin-dots i");
      const paint = (err) => { dots.forEach((d, i) => { d.classList.toggle("on", i < v.length); d.classList.toggle("err", !!err); }); };
      const close = (r) => { document.removeEventListener("keydown", onKey); box.remove(); done(r); };
      const tryBio = async () => {
        if (!bio || busy) return; busy = true;
        try { await BIO().verifyIdentity({ reason: `Déverrouiller le profil ${p.name}`, title: `Profil ${p.name}`, subtitle: "TVO", negativeButtonText: "Code" }); busy = false; return close(true); }
        catch { busy = false; msg.textContent = "Saisis le code à 4 chiffres"; }
      };
      const press = async (k) => {
        if (busy) return;
        if (k === "del") v = v.slice(0, -1); else if (v.length < 4) v += k;
        paint(false); if (navigator.vibrate) navigator.vibrate(6);
        if (v.length === 4) {
          busy = true; const ok = (await pinHash(v)) === p.pin; busy = false;
          if (ok) return close(true);
          v = ""; paint(true); msg.textContent = "Code incorrect, réessaie"; msg.classList.add("err"); if (navigator.vibrate) navigator.vibrate([40, 40, 40]);
          setTimeout(() => { paint(false); msg.classList.remove("err"); }, 900);
        }
      };
      const onKey = (e) => { if (/^[0-9]$/.test(e.key)) press(e.key); else if (e.key === "Backspace") press("del"); else if (e.key === "Escape") close(false); };
      document.addEventListener("keydown", onKey);
      box.addEventListener("pointerdown", (e) => {
        if (e.target === box || e.target.closest("[data-x]")) return close(false);
        if (e.target.closest("[data-bio]")) return tryBio();
        const b = e.target.closest("[data-k]"); if (b) { e.preventDefault(); press(b.dataset.k); }
      });
      // Face ID se lance tout seul si l'utilisateur l'a activé pour ce profil sur cet appareil
      if (bio && LS.get("bio." + p.id, false)) setTimeout(tryBio, 250);
    });
  }
  async function offerBio(p) {
    const bio = await bioAvailable(); if (!bio || LS.get("bio." + p.id, null) !== null) return;
    if (confirm(`Utiliser ${bioName(bio)} pour déverrouiller le profil ${p.name} sur cet appareil ?`)) { LS.set("bio." + p.id, true); toast(`${bioName(bio)} activé ✓`); }
    else LS.set("bio." + p.id, false);
  }
  /** Code PIN à 4 chiffres : fenêtre de saisie ; renvoie le code ou null. */
  function askPin(title) {
    return new Promise((done) => {
      const box = document.createElement("div"); box.className = "pin-sheet";
      box.innerHTML = `<div class="pin-card"><b>${esc(title)}</b><div class="pin-dots"><i></i><i></i><i></i><i></i></div>
        <div class="pin-pad">${[1, 2, 3, 4, 5, 6, 7, 8, 9, "", 0, "⌫"].map((k) => `<button ${k === "" ? "disabled" : ""} data-k="${k}">${k}</button>`).join("")}</div>
        <button class="btn block" data-x>Annuler</button></div>`;
      document.body.appendChild(box); let v = "";
      const dots = () => box.querySelectorAll(".pin-dots i").forEach((d, i) => d.classList.toggle("on", i < v.length));
      const close = (r) => { box.remove(); done(r); };
      box.addEventListener("pointerdown", (e) => {
        if (e.target === box || e.target.closest("[data-x]")) return close(null);
        const k = e.target.closest("[data-k]"); if (!k) return; e.preventDefault();
        if (k.dataset.k === "⌫") v = v.slice(0, -1); else if (v.length < 4) v += k.dataset.k;
        dots(); if (v.length === 4) setTimeout(() => close(v), 120);
      });
    });
  }
  const posterHTML = (m) => `<button class="poster" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      <span class="fallback">${esc(m.name)}</span>${m.poster ? `<img src="${esc(m.poster)}" alt="" loading="lazy" onerror="this.remove()">` : ""}</button>`;
  const landHTML = (m) => `<button class="land ${m.type === "tv" || m.type === "channel" ? "channel" : ""}" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      ${m.background || m.poster ? `<img src="${esc(m.type === "tv" || m.type === "channel" ? m.poster : m.background || m.poster)}" alt="" loading="lazy" onerror="this.remove()">` : ""}
      <span class="cap"><b>${esc(m.name)}</b><span>${[m.releaseInfo, typeLabel(m.type, false), m.imdbRating ? "★ " + m.imdbRating : ""].filter(Boolean).map(esc).join(" · ")}</span></span></button>`;
  const portraitHTML = (m) => {
    const tv = m.type === "tv" || m.type === "channel";
    return `<button class="pcard" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      <span class="pimg ${tv ? "channel" : ""}">${m.poster || m.background ? `<img src="${esc(m.poster || m.background)}" alt="" loading="lazy" onerror="this.remove()">` : `<span class="pfall">${esc(m.name)}</span>`}
        ${m.imdbRating ? `<span class="rate">★ ${esc(m.imdbRating)}</span>` : tv ? '<span class="rate live">● En direct</span>' : ""}</span>
      <span class="pname">${esc(m.name)}</span></button>`;
  };
  const landCard = (m) => `<button class="lcard" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      <span class="limg">${m.background || m.poster ? `<img src="${esc(m.background || m.poster)}" alt="" loading="lazy" onerror="this.remove()">` : ""}${m.imdbRating ? `<span class="rate">★ ${esc(m.imdbRating)}</span>` : ""}</span>
      <span class="pname">${esc(m.name)}</span></button>`;
  const isLandRow = (title) => /^(Nouv|Dernier|Actuellement|Bientôt)/.test(title);
  const rowIcon = (title) => /tendance|populaire/i.test(title) ? ICON.trend : /^(Nouv|Dernier|Actuellement|Bientôt)/.test(title) ? ICON.doc : /chaîne|sport|jeunesse/i.test(title) ? ICON.live : ICON.movie;
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
  window.addEventListener("scroll", () => { const h = $(".header"); if (h) h.classList.toggle("scrolled", window.scrollY > 4); document.body.classList.toggle("scrolled-bg", window.scrollY > 4); }, { passive: true });

  function setTabs(active) {
    if (!active) { tabs.hidden = true; document.body.classList.add("no-tabs"); return; }
    document.body.classList.remove("no-tabs");
    tabs.hidden = false;
    const items = [["home", "Accueil", ICON.home], ["discover", "Découvrir", ICON.search], ["live", "Live TV", ICON.live], ["list", "Ma liste", ICON.heart], ["settings", "Profil", ICON.settings]];
    tabs.innerHTML = items.map(([k, l, i]) => `<button class="tab ${k === active ? "active" : ""}" data-tab="${k}"><span class="ico">${i}</span><span>${l}</span></button>`).join("");
    tabs.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => {
      if (location.hash === "#/" + b.dataset.tab) window.scrollTo({ top: 0, behavior: "smooth" }); else go("#/" + b.dataset.tab);
    }));
  }

  const homeLayout = () => LS.get("home", { order: [], hidden: [], t: 0 });
  function setHome(h) { LS.set("home", { ...h, t: Date.now() }); changed(); }
  function applyHome(rows) {
    const h = homeLayout();
    const idx = (k) => { const i = h.order.indexOf(k); return i < 0 ? 1e9 : i; };
    return rows.filter((r) => !h.hidden.includes(r.key)).sort((a, b) => idx(a.key) - idx(b.key));
  }
  /** Même barre du haut sur Accueil, Films, Séries, Live TV et Ma liste. */
  function topBar(title) {
    const p = S.profile;
    return `<header class="header topbar">${LOGO}<span class="spacer"></span>
      <button class="icon-btn" data-go="#/search" aria-label="Rechercher">${ICON.search}</button>
      <button class="icon-btn" data-go="#/settings" aria-label="Réglages">${ICON.settings}</button>
      <button data-go="#/profiles" aria-label="Changer de profil">${avatar(p, "sm")}</button></header>
      ${title ? `<h1 class="page-title">${esc(title)}</h1>` : ""}`;
  }
  document.addEventListener("click", (e) => { const g = e.target.closest("[data-go]"); if (g) go(g.dataset.go); });
  function navChips(active) {
    const items = [["home", "Accueil", ICON.home], ["movies", "Films", ICON.clap], ["series", "Séries", ICON.series], ["live", "Live TV", ICON.live]];
    return `<nav class="navchips">${items.map(([k, l, i]) => `<a class="nchip ${k === active ? "on" : ""}" href="#/${k}">${i}<span>${l}</span></a>`).join("")}</nav>`;
  }
  function buildRows(type) {
    const rows = [];
    catalogAddons().forEach((a) => a.manifest.catalogs.filter((c) => !catRequires(c) && (!type || c.type === type)).forEach((c) => {
      rows.push({ key: `${a.url}|${c.type}|${c.id}`, title: a.manifest.id === "com.linvo.cinemeta" ? catalogTitle(c.type, c.name) : ["tmdb", "tvo.freetv.fr"].includes(a.manifest.id) || a.manifest.id.startsWith("tvo.m3u.") ? c.name : `${c.name} · ${a.manifest.name}`, a, c, extra: {} });
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
    for (const a of catalogAddons()) {
      const c = a.manifest.catalogs.find((c) => c.type === type && catSupports(c, "genre") && catOnlyRequires(c, "genre"));
      if (c) return { a, c };
    }
    return null;
  }

  function rowShell(row) {
    const sec = document.createElement("section");
    sec.className = "section";
    sec.innerHTML = `<h2 class="section-title"><span class="st">${rowIcon(row.title)}${esc(row.title)}</span><a href="#/catalog/${enc(row.key)}">Voir tout →</a></h2>
      <div class="row">${Array.from({ length: 4 }, () => '<div class="pcard"><span class="pimg skeleton"></span></div>').join("")}</div>`;
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
        en.target.querySelector(".row").innerHTML = list.map(isLandRow(row.title) ? landCard : portraitHTML).join("");
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
          ${S.profiles.map((p) => `<button class="profile" data-p="${esc(p.id)}">${avatar(p).replace(/<\/div>$/, (manage ? `<span class="edit">${ICON.edit}</span>` : "") + (p.pin && !manage ? '<span class="pbadge">🔒</span>' : "") + "</div>")}<span>${esc(p.name)}</span>
            <small class="prole ${profileStatus(p)[1]}">${esc(profileStatus(p)[0])}</small></button>`).join("")}
          ${S.profiles.length < 6 ? `<button class="profile" data-new><div class="avatar add-avatar">${ICON.plus}</div><span>Ajouter</span></button>` : ""}
        </div>
        <div style="margin-top:36px"><button class="btn" id="manage">${manage ? ICON.check + " Terminé" : ICON.edit + " Gérer les profils"}</button></div>
      </div>`;
      app.querySelectorAll("[data-p]").forEach((b) => b.addEventListener("click", () => {
        const p = S.profiles.find((x) => x.id === b.dataset.p);
        if (manage) {
          // Seul l'administrateur modifie les autres profils (et chaque profil se modifie lui-même, avec son code)
          return (async () => {
            if (p.pin && !(S.profile && isAdmin(S.profile) && S.profile.id !== p.id)) { if (!(await unlockProfile(p, `Modifier ${p.name}`))) return; }
            go("#/profile/" + enc(p.id));
          })();
        }
        (async () => {
          if (p.pin) { if (!(await unlockProfile(p))) return; offerBio(p); }
          const blk = profileBlock(p); if (blk) return toast(blk, 4500);
          selectProfile(p); go("#/home");
        })();
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
    let icon = existing ? existing.icon || "" : "";
    let kids = !!(existing && existing.kids), pin = existing ? existing.pin || null : null;
    const admin = !!(existing && isAdmin(existing));
    const canAdmin = !S.profiles.length || (S.profile && isAdmin(S.profile));
    app.innerHTML = `<div class="profiles-screen">
      <h1>${existing ? "Modifier le profil" : "Nouveau profil"}</h1>
      <div id="av" style="width:120px">${avatar({ name: existing ? existing.name : "?", color, icon })}</div>
      <div style="width:100%;max-width:360px;margin-top:24px;display:grid;gap:18px">
        <input class="field" id="name" maxlength="16" placeholder="Prénom" value="${esc(existing ? existing.name : "")}" autocomplete="off">
        <div class="swatches">${COLORS.map((c) => `<button class="swatch ${c === color ? "on" : ""}" data-c="${c}" style="background:${c}" aria-label="Couleur"></button>`).join("")}</div>
        <div class="icons">${AVATAR_ICONS.map((ic) => `<button class="iconpick ${ic === icon ? "on" : ""}" data-ic="${ic}" aria-label="Avatar">${ic || esc((existing ? existing.name : "A").slice(0, 1).toUpperCase())}</button>`).join("")}</div>
        <div class="card popt"><label class="sw"><span><b>Profil enfant</b><small>Uniquement films et dessins animés pour enfants</small></span><input type="checkbox" id="kids" ${kids ? "checked" : ""}><i></i></label>
          <label class="sw"><span><b>Code PIN</b><small>${pin ? "Code actif. Décoche pour le retirer." : "Protège ce profil par un code à 4 chiffres"}</small></span><input type="checkbox" id="pinOn" ${pin ? "checked" : ""}><i></i></label>
          ${canAdmin ? `<label class="sw"><span><b>Administrateur</b><small>Gère les add-ons et les accès de chaque profil</small></span><input type="checkbox" id="adm" ${admin ? "checked" : ""}><i></i></label>` : ""}</div>
        <button class="btn primary block" id="save">${ICON.check} Enregistrer</button>
        ${existing ? `<button class="btn danger block" id="del">${ICON.trash} Supprimer ce profil</button>` : ""}
        ${S.profiles.length ? `<button class="btn block" id="cancel">Annuler</button>` : ""}
      </div></div>`;
    const name = $("#name");
    const redraw = () => ($("#av").innerHTML = avatar({ name: name.value || "?", color, icon }));
    app.querySelectorAll("[data-ic]").forEach((b) => b.addEventListener("click", () => {
      icon = b.dataset.ic; app.querySelectorAll("[data-ic]").forEach((x) => x.classList.toggle("on", x === b)); redraw();
    }));
    $("#kids").addEventListener("change", (e) => (kids = e.target.checked));
    $("#pinOn").addEventListener("change", async (e) => {
      if (!e.target.checked) { pin = null; return; }
      const a = await askPin("Choisis un code"); if (a === null) { e.target.checked = !!pin; return; }
      const b = await askPin("Confirme le code"); if (a !== b) { e.target.checked = !!pin; return toast("Les codes ne correspondent pas"); }
      pin = await pinHash(a); toast("Code enregistré");
    });
    name.addEventListener("input", redraw);
    app.querySelectorAll("[data-c]").forEach((b) => b.addEventListener("click", () => {
      color = b.dataset.c; app.querySelectorAll("[data-c]").forEach((x) => x.classList.toggle("on", x === b)); redraw();
    }));
    $("#save").addEventListener("click", () => {
      const v = name.value.trim(); if (!v) { name.focus(); return toast("Indique un prénom"); }
      const admEl = $("#adm"); const wantAdmin = admEl ? admEl.checked : admin;
      if (wantAdmin) S.profiles.forEach((x) => { if (x.admin && (!existing || x.id !== existing.id)) { x.admin = false; x.t = Date.now(); } });
      const extra = { icon, kids, pin, admin: wantAdmin, t: Date.now() };
      if (existing) Object.assign(existing, { name: v, color, ...extra });
      else S.profiles.push({ id: "p" + Date.now().toString(36), name: v, color, ...extra, admin: wantAdmin || !S.profiles.length });
      saveProfiles();
      if (S.profile && existing && S.profile.id === existing.id) S.profile = existing;
      go("#/profiles");
    });
    if (existing) $("#del").addEventListener("click", () => {
      if (!confirm(`Supprimer le profil « ${existing.name} » ?`)) return;
      S.profiles = S.profiles.filter((p) => p.id !== existing.id);
      LS.set("profdel", { ...LS.get("profdel", {}), [existing.id]: Date.now() });
      saveProfiles();
      LS.del("lib." + existing.id); LS.del("prog." + existing.id); LS.del("libdel." + existing.id);
      if (S.profile && S.profile.id === existing.id) S.profile = null;
      go("#/profiles");
    });
    const c = $("#cancel"); if (c) c.addEventListener("click", () => history.back());
  }

  function screenBrowse(type) {
    setTabs(type === "movie" ? "movies" : type === "series" ? "series" : "home");
    const p = S.profile;
    app.innerHTML = (type ? topBar(typeLabel(type)) : `<header class="header homebar">
        <button data-go="#/profiles" aria-label="Changer de profil">${avatar(p, "sm")}</button>
        <h1>Tendances pour toi</h1>
        <button class="icon-btn round" data-go="#/discover" aria-label="Rechercher">${ICON.search}</button></header>`)
      + `<div id="upd"></div><div id="hero"></div><div id="rows"></div>`;
    showUpdateBanner($("#upd"));
    const rowsEl = $("#rows");

    const hidden = type ? [] : homeLayout().hidden;
    const cont = continueWatching().filter((x) => !type || x.type === type);
    if (cont.length && !hidden.includes("continue")) {
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span class="st plain">Continuer à regarder</span><span class="small muted">Appui long : options</span></h2><div class="row">${cont.map((x) => `
        <button class="ccard" data-resume="${esc(x.type)}|${esc(x.metaId)}|${esc(x.videoId)}" data-menu="cont|${esc(x.videoId)}">
          <span class="cimg">${x.background || x.poster ? `<img src="${esc(x.background || x.poster)}" alt="" loading="lazy">` : ""}</span><span class="cshade"></span>
          <span class="cplay">${ICON.play}</span>
          <span class="cinfo"><b>${esc(x.name)}</b><span>${x.season != null ? `S${x.season} · É${x.episode}${x.episodeTitle ? " · " + esc(x.episodeTitle) : ""}` : `Il reste ${fmtMin(Math.max(1, (x.duration - x.position) / 60000))}`}</span></span>
          <span class="cbar"><i style="width:${Math.round(fraction(x) * 100)}%"></i></span>
        </button>`).join("")}</div>`;
      sec.querySelectorAll("[data-resume]").forEach((b) => b.addEventListener("click", () => {
        const [t, m, v] = b.dataset.resume.split("|"); go(`#/sources/${enc(t)}/${enc(m)}/${enc(v)}`);
      }));
      rowsEl.appendChild(sec);
    }
    const lib = S.lib.filter((x) => !type || x.type === type);
    if (lib.length && !hidden.includes("library")) {
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span class="st">${ICON.heart}Ma liste</span><a href="#/list">Voir tout →</a></h2><div class="row">${lib.map(portraitHTML).join("")}</div>`;
      rowsEl.appendChild(sec);
    }
    if (false && !type && !hidden.includes("genres")) {
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span class="st">${ICON.grid}Catégories</span><a href="#/discover">Tout voir →</a></h2>${genreTilesHTML(type || "movie")}`;
      fillGenreImages(sec);
      rowsEl.appendChild(sec);
      const ps = document.createElement("section"); ps.className = "section";
      ps.innerHTML = `<h2 class="section-title"><span class="st">${ICON.series}Plateformes</span></h2><div class="ptiles">${PROVIDERS.map(([id, n, c]) =>
        provTile(id, n, c)).join("")}</div>`;
      rowsEl.appendChild(ps); refreshProvTiles();
    }
    const rows = type ? buildRows(type) : applyHome(buildRows(null));
    if (!rows.length) rowsEl.insertAdjacentHTML("beforeend", `<div class="empty"><h2>Aucun catalogue</h2>Ajoute un add-on de catalogue dans Réglages.</div>`);
    let heroDone = false;
    lazyRows(rowsEl, rows, (list) => {
      if (heroDone) return; heroDone = true;
      if (list.length) (type ? renderHero : renderCarousel)($("#hero"), list);
    });
    if (!type && tmdbOn() && !hidden.includes("collections")) {
      const cs = document.createElement("section"); cs.className = "section";
      cs.innerHTML = `<h2 class="section-title"><span class="st">${ICON.movie}Sagas et collections</span><a href="#/collections">Tout voir →</a></h2><div class="row colrow" id="colrow"><div class="empty small">Chargement des sagas…</div></div>`;
      rowsEl.insertBefore(cs, rowsEl.children[2] || null);
      loadCollections((list) => { const r = $("#colrow"); if (r) r.innerHTML = list.slice(0, 24).map(colCard).join(""); }, S.profile && S.profile.kids);
    }
  }


  // ------------------------------------------------------------------ bandes-annonces (TMDB → YouTube), en français si possible
  async function playTrailer(m) {
    const kind = m.tmdbKind || (m.type === "series" ? "tv" : "movie");
    let vids = [];
    for (const lang of ["fr-FR", "en-US"]) {
      try { vids = ((await tget(`/${kind}/${m.tmdbId}/videos`, { language: lang })).results || []).filter((v) => v.site === "YouTube"); } catch { vids = []; }
      if (vids.length) break;
    }
    const pick = vids.find((v) => v.type === "Trailer" && v.official) || vids.find((v) => v.type === "Trailer") || vids.find((v) => v.type === "Teaser") || vids[0];
    if (!pick) return toast("Pas de bande-annonce disponible pour ce titre.");
    const box = document.createElement("div"); box.className = "tr-sheet";
    box.innerHTML = `<div class="tr-card"><div class="tr-top"><b>${esc(pick.name || "Bande-annonce")}</b><button class="icon-btn" data-x aria-label="Fermer">✕</button></div>
      <div class="tr-frame"><iframe src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(pick.key)}?autoplay=1&playsinline=1&rel=0&modestbranding=1&hl=fr&cc_lang_pref=fr"
        allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe></div></div>`;
    document.body.appendChild(box);
    box.addEventListener("click", (e) => { if (e.target === box || e.target.closest("[data-x]")) box.remove(); });
  }

  // ------------------------------------------------------------------ appui long : menu d'options sur les cartes
  function actionSheet(title, actions) {
    const box = document.createElement("div"); box.className = "as-sheet";
    box.innerHTML = `<div class="as-card"><b class="as-t">${esc(title)}</b>${actions.map((a, i) => `<button class="as-btn ${a.danger ? "danger" : ""}" data-a="${i}">${a.icon || ""}<span>${esc(a.label)}</span></button>`).join("")}
      <button class="as-btn cancel" data-x>Annuler</button></div>`;
    document.body.appendChild(box);
    box.addEventListener("click", (e) => { if (e.target === box || e.target.closest("[data-x]")) return box.remove();
      const b = e.target.closest("[data-a]"); if (b) { box.remove(); actions[+b.dataset.a].run(); } });
  }
  function setProg(videoId, f) { const i = S.prog.findIndex((x) => x.videoId === videoId); if (i < 0) return; S.prog[i] = { ...S.prog[i], ...f(S.prog[i]), updatedAt: Date.now() }; saveProg(); }
  function cardMenu(el) {
    const [kind, ref] = (el.dataset.menu || "").split("|");
    if (kind === "cont") {
      const x = S.prog.find((p) => p.videoId === ref); if (!x) return;
      const m = { id: x.metaId, type: x.type, name: x.name, poster: x.poster, background: x.background };
      actionSheet(x.name, [
        { label: "Reprendre la lecture", icon: ICON.play, run: () => go(`#/sources/${enc(x.type)}/${enc(x.metaId)}/${enc(x.videoId)}`) },
        { label: "Voir la fiche", icon: ICON.movie, run: () => go(`#/title/${enc(x.type)}/${enc(x.metaId)}`) },
        { label: inLib(x.metaId) ? "Retirer de Ma liste" : "Ajouter à Ma liste", icon: ICON.heart, run: () => { toggleLib(m); toast(inLib(x.metaId) ? "Ajouté à Ma liste ✓" : "Retiré de Ma liste"); } },
        { label: "Marquer comme vu", icon: ICON.check, run: () => { setProg(x.videoId, (p) => ({ position: p.duration || 1, duration: p.duration || 1 })); toast("Marqué comme vu ✓"); route(); } },
        { label: "Retirer de Continuer à regarder", danger: true, icon: ICON.trash, run: () => { S.prog.filter((p) => p.metaId === x.metaId).forEach((p) => setProg(p.videoId, () => ({ position: 0 }))); toast("Retiré ✓"); route(); } },
      ]);
      return;
    }
    const [type, id] = (el.dataset.open || "").split("|"); if (!id) return;
    const name = el.getAttribute("aria-label") || "";
    const img = el.querySelector("img"); const m = { id, type, name, poster: img ? img.getAttribute("src") : null, background: null };
    const acts = [{ label: "Ouvrir", icon: ICON.play, run: () => el.click() }];
    if (type === "movie" || type === "series") acts.push({ label: inLib(id) ? "Retirer de Ma liste" : "Ajouter à Ma liste", icon: ICON.heart, run: () => { toggleLib(m); toast(inLib(id) ? "Ajouté à Ma liste ✓" : "Retiré de Ma liste"); } });
    acts.push({ label: "Partager le titre", icon: ICON.doc, run: async () => { try { await navigator.share({ title: name, text: name + " — à regarder sur TVO" }); } catch { try { await navigator.clipboard.writeText(name); toast("Titre copié"); } catch {} } } });
    actionSheet(name, acts);
  }
  (() => {
    let timer = null, startX = 0, startY = 0, fired = false;
    document.addEventListener("touchstart", (e) => {
      const el = e.target.closest("[data-menu], [data-open]"); if (!el || el.closest(".as-sheet")) return;
      fired = false; startX = e.touches[0].clientX; startY = e.touches[0].clientY;
      timer = setTimeout(() => { fired = true; if (navigator.vibrate) navigator.vibrate(12); cardMenu(el); }, 480);
    }, { passive: true });
    document.addEventListener("touchmove", (e) => { if (timer && (Math.abs(e.touches[0].clientX - startX) > 10 || Math.abs(e.touches[0].clientY - startY) > 10)) { clearTimeout(timer); timer = null; } }, { passive: true });
    document.addEventListener("touchend", () => { clearTimeout(timer); timer = null; }, { passive: true });
    document.addEventListener("click", (e) => { if (fired) { fired = false; e.preventDefault(); e.stopImmediatePropagation(); } }, true);
    document.addEventListener("contextmenu", (e) => { const el = e.target.closest("[data-menu], [data-open]"); if (el) { e.preventDefault(); cardMenu(el); } });
  })();
  // ------------------------------------------------------------------ sagas et collections (TMDB)
  const COLLECTIONS = ["Toy Story", "Fast & Furious", "American Pie", "Harry Potter", "Star Wars", "Le Seigneur des anneaux", "Le Hobbit", "Indiana Jones",
    "Retour vers le futur", "Jurassic Park", "Pirates des Caraïbes", "Shrek", "L'Âge de glace", "Moi, moche et méchant", "Madagascar", "Kung Fu Panda", "Cars",
    "Le Monde de Nemo", "Les Indestructibles", "Dragons", "Hôtel Transylvanie", "Mission Impossible", "James Bond", "John Wick", "Matrix", "Terminator", "Alien",
    "Rocky", "Creed", "Rambo", "Die Hard", "L'Arme fatale", "Bad Boys", "Men in Black", "Taxi", "Les Visiteurs", "Astérix", "OSS 117", "Les Tuche",
    "Qu'est-ce qu'on a fait au Bon Dieu", "Les Bronzés", "La Grande Vadrouille", "Scary Movie", "Very Bad Trip", "Austin Powers", "Destination finale", "Saw",
    "Scream", "Conjuring", "Insidious", "Annabelle", "Hunger Games", "Twilight", "Divergente", "Le Labyrinthe", "Transformers", "X-Men", "Avengers", "Spider-Man",
    "Iron Man", "Captain America", "Thor", "Les Gardiens de la Galaxie", "Black Panther", "The Dark Knight", "Batman", "Jumanji", "La Planète des singes",
    "Kingsman", "Ocean's", "Mad Max", "Expendables", "Le Parrain", "Retour vers", "Shaun", "Paddington", "Le Livre de la jungle", "Toy", "Pitch Perfect",
    "Jason Bourne", "Taken", "Underworld", "Resident Evil", "Ghostbusters", "SOS Fantômes", "Sherlock Holmes", "Charlie et ses drôles de dames", "Rush Hour",
    "Equalizer", "Sonic", "Les Minions", "Les Schtroumpfs", "Garfield", "Cinquante nuances", "Bridget Jones", "Mamma Mia", "Night at the Museum", "La Nuit au musée",
    "Le Monde de Narnia", "Percy Jackson", "Les Animaux fantastiques", "Dune", "Avatar", "Godzilla", "King Kong", "Predator", "Tremors", "Paranormal Activity"];
  const KIDS_COLS = new Set(["Toy Story", "Shrek", "L'Âge de glace", "Moi, moche et méchant", "Madagascar", "Kung Fu Panda", "Cars", "Le Monde de Nemo", "Les Indestructibles",
    "Dragons", "Hôtel Transylvanie", "Paddington", "Le Livre de la jungle", "Sonic", "Les Minions", "Les Schtroumpfs", "Garfield", "La Nuit au musée", "Le Monde de Narnia", "Harry Potter", "Jumanji"]);
  let COLS = LS.get("collections", { t: 0, list: [], q: {} });
  function colCard(c) {
    return `<a class="colcard" href="#/collection/${c.id}"><span class="cimg">${c.b ? `<img src="${esc(c.b)}" alt="" loading="lazy">` : ""}</span>
      <span class="cshade"></span><b>${esc(c.n.replace(/\s*[-–]\s*(Saga|Collection)$/i, "").replace(/^(Saga|Collection)\s+/i, ""))}</b></a>`;
  }
  async function loadCollections(onUpdate, kidsOnly) {
    if (Date.now() - COLS.t > 7 * 864e5) COLS = { t: Date.now(), list: [], q: {} };
    const pick = () => COLS.list.filter((c) => !kidsOnly || KIDS_COLS.has(c.q));
    if (COLS.list.length) onUpdate(pick());
    const todo = COLLECTIONS.filter((q) => !(q in COLS.q) && (!kidsOnly || KIDS_COLS.has(q)));
    let i = 0, changed = false;
    const worker = async () => {
      while (i < todo.length) {
        const q = todo[i++];
        try {
          const d = await tget("/search/collection", { query: q });
          const words = q.toLowerCase().split(/\s+/)[0];
          const res = (d.results || []).filter((r, k) => k === 0 || (r.name || "").toLowerCase().includes(words)).slice(0, 3);
          COLS.q[q] = res.map((r) => r.id);
          res.forEach((r) => { if (r.backdrop_path && !COLS.list.some((c) => c.id === r.id)) { COLS.list.push({ id: r.id, n: r.name, b: timg(r.backdrop_path, "w780"), q }); changed = true; } });
        } catch { COLS.q[q] = []; }
      }
    };
    await Promise.all([worker(), worker(), worker(), worker()]);
    if (changed || todo.length) { LS.set("collections", COLS); onUpdate(pick()); }
  }
  function screenCollections() {
    setTabs("discover");
    app.innerHTML = header("Sagas et collections", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`)
      + `<div class="pad"><input class="field" id="cq" type="search" placeholder="Chercher une saga (Toy Story, Fast & Furious…)" autocomplete="off"></div><div class="colgrid pad" id="cg"><div class="empty small">Chargement…</div></div>`;
    let all = [];
    const draw = () => { const q = $("#cq").value.trim().toLowerCase();
      $("#cg").innerHTML = all.filter((c) => !q || c.n.toLowerCase().includes(q)).map(colCard).join("") || `<div class="empty small">Aucune saga trouvée. Essaie la recherche TMDB ci-dessous.</div>`; };
    $("#cq").addEventListener("input", () => {
      draw(); clearTimeout(screenCollections.t);
      const q = $("#cq").value.trim(); if (q.length < 3) return;
      screenCollections.t = setTimeout(async () => {
        try { const d = await tget("/search/collection", { query: q });
          (d.results || []).forEach((r) => { if (r.backdrop_path && !all.some((c) => c.id === r.id)) all.push({ id: r.id, n: r.name, b: timg(r.backdrop_path, "w780") }); });
          draw(); } catch {}
      }, 400);
    });
    loadCollections((l) => { all = [...l].sort((a, b) => a.n.localeCompare(b.n, "fr")); draw(); }, S.profile && S.profile.kids);
  }
  async function screenCollection(id) {
    setTabs("discover");
    const back = `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`;
    app.innerHTML = header("", back) + `<div class="empty">Chargement…</div>`;
    let d; try { d = await tget(`/collection/${id}`); } catch { d = null; }
    if (!d || !d.parts) { app.querySelector(".empty").textContent = "Saga introuvable."; return; }
    const parts = d.parts.filter((p) => p.poster_path).sort((a, b) => (a.release_date || "9999").localeCompare(b.release_date || "9999"));
    const list = parts.map((o) => tpreview(o, "movie"));
    const years = parts.map((p) => (p.release_date || "").slice(0, 4)).filter(Boolean);
    app.innerHTML = header("", back) + `<div class="pv-hero" style="--c:#2a1f7a">${d.backdrop_path ? `<img src="${esc(timg(d.backdrop_path, "w1280"))}" alt="">` : ""}<div class="shade"></div>
        <div class="pv-brand"><h1>${esc(d.name.replace(/\s*[-–]\s*(Saga|Collection)$/i, ""))}</h1></div></div>
      <div class="pad small" style="color:var(--cyan);margin-top:10px">${list.length} film${list.length > 1 ? "s" : ""}${years.length ? ` · ${years[0]} – ${years[years.length - 1]}` : ""}</div>
      ${d.overview ? `<p class="pad desc" style="margin-top:6px">${esc(d.overview)}</p>` : ""}
      <div class="chips pad" id="cs" style="margin-top:6px"><button class="chip on" data-s="asc">Ordre de sortie</button><button class="chip" data-s="desc">Plus récents</button></div>
      <div class="grid" id="cgr"></div>`;
    const drawC = (o) => { $("#cgr").innerHTML = (o === "desc" ? [...list].reverse() : list).map(portraitHTML).join(""); };
    $("#cs").addEventListener("click", (e) => { const b = e.target.closest("[data-s]"); if (!b) return; document.querySelectorAll("#cs .chip").forEach((c) => c.classList.toggle("on", c === b)); drawC(b.dataset.s); });
    drawC("asc");
  }

  function renderHero(el, list) {
    const items = list.slice(0, 5);
    let i = 0, timer;
    const draw = () => {
      const m = items[i];
      el.innerHTML = `<div class="hero3">
        <img class="art" src="${esc(m.background || m.poster)}" alt="">
        <div class="shade"></div>
        <div class="info">
          ${m.logo ? `<img class="hlogo" src="${esc(m.logo)}" alt="${esc(m.name)}" onerror="this.outerHTML='<h2>${esc(m.name).replace(/'/g, "")}</h2>'">` : `<h2>${esc(m.name)}</h2>`}
          <div class="hmeta">${m.imdbRating ? `<span class="ybadge">★ ${esc(m.imdbRating)}</span>` : ""}${m.releaseInfo ? `<span>${esc(m.releaseInfo)}</span>` : ""}${m.genres && m.genres[0] ? `<span>${esc(genreLabel(m.genres[0]))}</span>` : ""}</div>
          ${m.description ? `<p>${esc(m.description)}</p>` : ""}
          <div class="actions">
            <button class="btn blue" data-open="${esc(m.type)}|${esc(m.id)}">${ICON.play} Regarder</button>
            <button class="btn outline" id="heroList">${inLib(m.id) ? ICON.check : ICON.plus} Ma liste</button>
          </div>
          <div class="dots">${items.map((x, k) => `<button class="${k === i ? "on" : ""}" data-k="${k}" aria-label="${esc(x.name)}"></button>`).join("")}</div>
        </div></div>`;
      $("#heroList").addEventListener("click", (e) => {
        toggleLib(m); e.currentTarget.innerHTML = (inLib(m.id) ? ICON.check : ICON.plus) + " Ma liste";
        toast(inLib(m.id) ? "Ajouté à ta liste" : "Retiré de ta liste");
      });
      el.querySelectorAll("[data-k]").forEach((b) => b.addEventListener("click", () => { i = Number(b.dataset.k); restart(); draw(); }));
    };
    let x0 = null;
    el.ontouchstart = (e) => (x0 = e.touches[0].clientX);
    el.ontouchend = (e) => { if (x0 == null) return; const dx = e.changedTouches[0].clientX - x0; if (Math.abs(dx) > 50) { i = (i + (dx < 0 ? 1 : items.length - 1)) % items.length; restart(); draw(); } x0 = null; };
    const restart = () => { clearInterval(timer); timer = setInterval(() => { if (!document.body.contains(el)) return clearInterval(timer); i = (i + 1) % items.length; draw(); }, 8000); };
    draw(); restart();
  }

  async function screenPerson(id) {
    setTabs(null);
    app.innerHTML = header("", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `<div class="empty">Chargement…</div>`;
    let p; try { p = await tmdbPerson(id); } catch { p = null; }
    if (!p) { app.querySelector(".empty").textContent = "Acteur introuvable (clé TMDB nécessaire)."; return; }
    app.innerHTML = header("", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `
      <div class="person-head pad"><span class="ph big">${p.photo ? `<img src="${esc(p.photo)}" alt="">` : ""}</span>
        <div><h1>${esc(p.name)}</h1><div class="small" style="color:var(--cyan)">${p.works.length} films et séries</div></div></div>
      ${p.bio ? `<p class="pad desc clamp" id="bio" style="margin-top:12px">${esc(p.bio)}</p>` : ""}
      <h2 class="section-title section"><span class="st">${ICON.movie}Filmographie</span></h2>
      <div class="chips" id="fkind">${[["all", `Tout (${p.works.length})`], ["movie", `Films (${p.works.filter((w) => w.type === "movie").length})`], ["series", `Séries (${p.works.filter((w) => w.type === "series").length})`]].map(([k, l]) => `<button class="chip ${k === "all" ? "on" : ""}" data-k="${k}">${l}</button>`).join("")}</div>
      <div class="chips" id="fsort" style="margin-top:6px">${[["new", "Plus récents"], ["old", "Plus anciens"], ["pop", "Populaires"]].map(([k, l]) => `<button class="chip ${k === "new" ? "on" : ""}" data-s="${k}">${l}</button>`).join("")}</div>
      <div class="grid" id="fgrid"></div>`;
    const st = { k: "all", s: "new" };
    const yr = (w) => Number(String(w.releaseInfo || "").slice(0, 4)) || 0;
    const drawF = () => {
      let l = p.works.filter((w) => st.k === "all" || w.type === st.k);
      if (st.s === "new") l = [...l].sort((a, b) => yr(b) - yr(a));
      if (st.s === "old") l = [...l.filter(yr).sort((a, b) => yr(a) - yr(b)), ...l.filter((w) => !yr(w))];
      $("#fgrid").innerHTML = l.map(portraitHTML).join("") || `<div class="empty">Rien dans cette catégorie.</div>`;
      document.querySelectorAll("#fkind .chip").forEach((c) => c.classList.toggle("on", c.dataset.k === st.k));
      document.querySelectorAll("#fsort .chip").forEach((c) => c.classList.toggle("on", c.dataset.s === st.s));
    };
    $("#fkind").addEventListener("click", (e) => { const b = e.target.closest("[data-k]"); if (b) { st.k = b.dataset.k; drawF(); } });
    $("#fsort").addEventListener("click", (e) => { const b = e.target.closest("[data-s]"); if (b) { st.s = b.dataset.s; drawF(); } });
    drawF();
    const bio = $("#bio"); if (bio) bio.addEventListener("click", () => bio.classList.toggle("clamp"));
  }

  const PROVIDERS = [["8", "Netflix", "#e50914"], ["119", "Prime Video", "#00a8e1"], ["337", "Disney+", "#113ccf"], ["381", "Canal+", "#111111"],
    ["350", "Apple TV+", "#555555"], ["531", "Paramount+", "#0064ff"], ["1899", "Max", "#002be7"]];
  /** Logos officiels des plateformes (fournis par TMDB), mis en cache 7 jours. */
  let PROV_LOGOS = LS.get("provlogos", null);
  async function provLogos() {
    if (PROV_LOGOS && Date.now() - PROV_LOGOS.t < 7 * 864e5) return PROV_LOGOS.m;
    if (!tmdbOn()) return {};
    try {
      const m = {};
      for (const k of ["movie", "tv"]) (await tget(`/watch/providers/${k}`, { watch_region: "FR" })).results.forEach((r) => { m[r.provider_id] = timg(r.logo_path, "w154"); });
      PROV_LOGOS = { t: Date.now(), m }; LS.set("provlogos", PROV_LOGOS); return m;
    } catch { return {}; }
  }
  function provTile(id, n, c) {
    const logo = PROV_LOGOS && PROV_LOGOS.m[id];
    return `<a class="prov ptile" style="--c:${c}" href="#/provider/${id}" aria-label="${esc(n)}"><span class="plogo">${logo ? `<img src="${esc(logo)}" alt="" onerror="this.remove()">` : esc(n.slice(0, 1))}</span><b>${esc(n)}</b></a>`;
  }
  function refreshProvTiles() {
    if (PROV_LOGOS && Object.keys(PROV_LOGOS.m).length) return;
    provLogos().then(() => document.querySelectorAll(".prov.ptile").forEach((a) => { const id = a.getAttribute("href").split("/").pop(); const p = PROVIDERS.find((x) => x[0] === id); if (p) a.outerHTML = provTile(...p); }));
  }

  const PROV_GENRES = [["28|10759", "Action et aventure"], ["35", "Comédies"], ["18", "Drames"], ["53|80", "Thrillers et policiers"], ["878|10765", "Science-fiction et fantastique"],
    ["16", "Animation"], ["10751|10762", "En famille"], ["27", "Horreur"], ["10749", "Romance"], ["99", "Documentaires"]];
  async function screenProvider(pid) {
    setTabs("home");
    const pr = PROVIDERS.find((x) => x[0] === pid) || [pid, "Plateforme", "#2f5bff"];
    const back = `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`;
    app.innerHTML = header("", back) + `<div class="pv-hero" id="pvh" style="--c:${pr[2]}"><div class="shade"></div><div class="pv-brand" id="pvb"><h1>${esc(pr[1])}</h1></div></div>
      <div class="chips pad" id="pvk"><button class="chip on" data-k="all">Tout</button><button class="chip" data-k="movie">Films</button><button class="chip" data-k="series">Séries</button></div><div id="rows"></div>`;
    if (!tmdbOn()) { $("#rows").innerHTML = `<div class="empty"><h2>Catalogue indisponible</h2>Le service de titres (TMDB) n'est pas joignable pour le moment.</div>`; return; }
    provLogos().then((m) => { if (m[pid]) $("#pvb").innerHTML = `<img class="pv-logo" src="${esc(m[pid])}" alt="${esc(pr[1])}"><h1>${esc(pr[1])}</h1>`; });
    tget("/discover/movie", { watch_region: "FR", with_watch_providers: pid, with_watch_monetization_types: "flatrate", sort_by: "popularity.desc" }).then((d) => {
      const b = (d.results || []).find((r) => r.backdrop_path); const h = $("#pvh");
      if (b && h) h.insertAdjacentHTML("afterbegin", `<img src="${esc(timg(b.backdrop_path, "w1280"))}" alt="">`);
    }).catch(() => {});
    const mk = (type, sort, name, g) => ({ key: `prov|${pid}|${type}|${sort}|${g || ""}`, title: name, a: TMDB.addon,
      c: { type, id: `pv_${pid}_${sort}_${g || ""}`, name, extra: [{ name: "skip", isRequired: false, options: [] }], extraRequired: [], extraSupported: [] }, extra: {} });
    const draw = (k) => {
      const types = k === "all" ? ["movie", "series"] : [k];
      const L = (t, a, b) => (t === "movie" ? a : b);
      const rows = [];
      types.forEach((t) => rows.push(mk(t, "pop", L(t, "Films tendance", "Séries tendance"))));
      types.forEach((t) => rows.push(mk(t, "new", L(t, "Nouveaux films", "Nouvelles séries"))));
      types.forEach((t) => rows.push(mk(t, "top", L(t, "Films les mieux notés", "Séries les mieux notées"))));
      PROV_GENRES.forEach(([g, n]) => types.forEach((t) => {
        const ids = g.split("|"); const gid = t === "movie" ? ids[0] : ids[1] || ids[0];
        if (t === "movie" && /^107/.test(gid)) return;
        rows.push(mk(t, "pop", k === "all" ? `${n} · ${L(t, "films", "séries")}` : n, gid));
      }));
      $("#rows").innerHTML = ""; lazyRows($("#rows"), rows);
    };
    $("#pvk").addEventListener("click", (e) => { const b = e.target.closest("[data-k]"); if (!b) return;
      document.querySelectorAll("#pvk .chip").forEach((c) => c.classList.toggle("on", c === b)); draw(b.dataset.k); });
    draw("all");
  }

  // Catégories en rectangles avec image (une affiche de film par genre, mise en cache 7 jours)
  const GENRE_TILES = [["Action", "#ef4444"], ["Adventure", "#f97316"], ["Comedy", "#f59e0b"], ["Drama", "#a855f7"], ["Thriller", "#14b8a6"],
    ["Crime", "#64748b"], ["Sci-Fi", "#6366f1"], ["Fantasy", "#8b5cf6"], ["Horror", "#991b1b"], ["Romance", "#ec4899"], ["Animation", "#22c55e"],
    ["Family", "#06b6d4"], ["Mystery", "#475569"], ["History", "#b45309"], ["War", "#57534e"], ["Western", "#c2410c"], ["Documentary", "#0ea5e9"]];
  let GENRE_IMG = LS.get("genreimg", { t: 0, m: {} });
  function genreTilesHTML(type = "movie") {
    return `<div class="gtiles">${GENRE_TILES.filter(([g]) => type !== "series" || g in TMDB.TV).map(([g, c]) => {
      const img = GENRE_IMG.m[g];
      return `<a class="gtile" style="--c:${c}" href="#/genre/${type}/${enc(g)}" data-gt="${g}">${img ? `<img src="${esc(img)}" alt="" loading="lazy">` : ""}<span class="gshade"></span><b>${esc(genreLabel(g))}</b></a>`;
    }).join("")}</div>`;
  }
  async function fillGenreImages(root) {
    if (!tmdbOn()) return;
    if (Date.now() - GENRE_IMG.t > 7 * 864e5) GENRE_IMG = { t: Date.now(), m: {} };
    const used = new Set(Object.values(GENRE_IMG.m));
    for (const [g] of GENRE_TILES) {
      if (GENRE_IMG.m[g]) continue;
      try {
        const d = await tget("/discover/movie", { with_genres: TMDB.MOVIE[g], sort_by: "popularity.desc", "vote_count.gte": "300" });
        const r = (d.results || []).find((x) => x.backdrop_path && !used.has(timg(x.backdrop_path, "w500")));
        if (!r) continue;
        const u = timg(r.backdrop_path, "w500"); used.add(u); GENRE_IMG.m[g] = u;
        if (root) root.querySelectorAll(`[data-gt="${g}"]`).forEach((t) => { if (!t.querySelector("img")) t.insertAdjacentHTML("afterbegin", `<img src="${esc(u)}" alt="">`); });
      } catch { break; }
    }
    LS.set("genreimg", GENRE_IMG);
  }

  /** Carrousel « Tendances pour toi » : grande affiche au centre. */
  let carTimer = null;
  function renderCarousel(el, list) {
    const items = list.filter((m) => m.poster).slice(0, 10);
    clearInterval(carTimer);
    el.innerHTML = `<div class="car-wrap"><div class="car-bg" id="carBg"></div>
      <div class="carousel" id="car">${items.map((m, i) => `<button class="cslide" data-i="${i}" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
        <img src="${esc(m.poster)}" alt="" ${i > 2 ? 'loading="lazy"' : ""}><span class="shine"></span></button>`).join("")}</div>
      <div class="car-info" id="carInfo"></div>
      <div class="car-dots" id="carDots">${items.map((_, i) => `<i data-d="${i}"></i>`).join("")}</div></div>`;
    const car = $("#car"), bg = $("#carBg"), info = $("#carInfo"), dots = $("#carDots");
    const slides = [...car.children];
    let cur = -1, user = 0;
    const show = (i) => {
      if (i === cur || !items[i]) return; cur = i; const m = items[i];
      bg.innerHTML = `<img src="${esc(m.background || m.poster)}" alt="">`;
      info.innerHTML = `<b>${esc(m.name)}</b><span>${[m.releaseInfo, (m.genres || []).slice(0, 2).map(genreLabel).join(", "), m.imdbRating ? "★ " + m.imdbRating : ""].filter(Boolean).map(esc).join("  ·  ")}</span>
        <div class="car-act"><button class="btn primary sm" data-open="${esc(m.type)}|${esc(m.id)}">${ICON.play || "▶"} Regarder</button>
        <button class="btn sm" id="carList">${inLib(m.id) ? "✓ Ma liste" : "+ Ma liste"}</button></div>`;
      $("#carList").addEventListener("click", (e) => { e.stopPropagation(); toggleLib(m); e.currentTarget.textContent = inLib(m.id) ? "✓ Ma liste" : "+ Ma liste"; });
      [...dots.children].forEach((d, k) => d.classList.toggle("on", k === i));
    };
    const layout = () => {
      const mid = car.scrollLeft + car.clientWidth / 2; let best = 0, bd = 1e9;
      slides.forEach((sl, i) => {
        const c = sl.offsetLeft + sl.offsetWidth / 2, d = (c - mid) / sl.offsetWidth;
        const ad = Math.min(Math.abs(d), 1.6);
        sl.style.transform = `perspective(900px) translateZ(${-ad * 90}px) rotateY(${Math.max(-1, Math.min(1, d)) * -16}deg) scale(${1 - ad * 0.06})`;
        sl.style.opacity = String(1 - ad * 0.35);
        sl.classList.toggle("center", ad < 0.3);
        if (Math.abs(d) < bd) { bd = Math.abs(d); best = i; }
      });
      show(best);
    };
    let raf = 0; car.addEventListener("scroll", () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(layout); }, { passive: true });
    const goTo = (i, smooth = true) => { const sl = slides[(i + slides.length) % slides.length]; if (sl) car.scrollTo({ left: sl.offsetLeft - (car.clientWidth - sl.offsetWidth) / 2, behavior: smooth ? "smooth" : "auto" }); };
    dots.addEventListener("click", (e) => { const d = e.target.closest("[data-d]"); if (d) { user = Date.now(); goTo(+d.dataset.d); } });
    ["touchstart", "pointerdown", "wheel"].forEach((ev) => car.addEventListener(ev, () => (user = Date.now()), { passive: true }));
    requestAnimationFrame(() => { goTo(Math.min(1, slides.length - 1), false); layout(); });
    carTimer = setInterval(() => {
      if (!document.body.contains(car)) return clearInterval(carTimer);
      if (document.hidden || Date.now() - user < 8000) return;
      goTo(cur + 1 >= slides.length ? 0 : cur + 1);
    }, 5500);
  }


  /** Découvrir : recherche + Films / Séries + catégories + plateformes. */
  function screenDiscover() {
    setTabs("discover");
    app.innerHTML = topBar("Découvrir") + `<div class="pad"><button class="search-pill big" data-go="#/search">${ICON.search}<span>Film, série, acteur, chaîne…</span></button></div>
      <div class="disc-grid pad">
        <a class="disc" href="#/movies">${ICON.clap}<b>Films</b></a><a class="disc" href="#/series">${ICON.series}<b>Séries</b></a>
        <a class="disc" href="#/live">${ICON.live}<b>Live TV</b></a><a class="disc" href="#/catalog/${enc(TMDB.URL + "|movie|kids")}">${ICON.smile}<b>Enfants</b></a>
        <a class="disc wide" href="#/collections">${ICON.movie}<b>Sagas et collections</b></a></div>
      <div id="rows"></div>`;
    const rowsEl = $("#rows");
    rowsEl.innerHTML = `<section class="section"><h2 class="section-title"><span class="st plain">Catégories</span>
        <span class="seg" id="catSeg"><button class="on" data-t="movie">Films</button><button data-t="series">Séries</button></span></h2>
        <div id="catTiles">${genreTilesHTML("movie")}</div></section>
      <section class="section"><h2 class="section-title"><span class="st plain">Plateformes</span></h2><div class="ptiles">${PROVIDERS.map(([id, n, c]) =>
      provTile(id, n, c)).join("")}</div></section>`;
    fillGenreImages(rowsEl); refreshProvTiles();
    $("#catSeg").addEventListener("click", (e) => { const b = e.target.closest("[data-t]"); if (!b) return;
      $("#catSeg").querySelectorAll("button").forEach((x) => x.classList.toggle("on", x === b));
      $("#catTiles").innerHTML = genreTilesHTML(b.dataset.t); fillGenreImages($("#catTiles")); });
  }

  // Live TV : toutes les catégories de chaînes
  async function screenLive() {
    setTabs("live");
    const p = S.profile;
    app.innerHTML = topBar("Live TV") + `<div id="rows"></div>`;
    const rows = buildRows("tv").concat(buildRows("channel"));
    if (!rows.length) {
      $("#rows").innerHTML = `<div class="pad"><h2 class="lhead">Ajoute des chaînes</h2><p class="small muted" style="margin:4px 0 12px">Des listes gratuites et légales, avec le programme TV en direct. Un appui et c'est prêt.</p></div>
        ${liveSuggHTML()}<div class="pad" style="margin-top:14px"><button class="btn block" id="lAdd2">${ICON.plus} Ajouter ma propre liste M3U</button></div>`;
      $("#rows").querySelectorAll("[data-live]").forEach((b) => b.addEventListener("click", () => addLive(b.dataset.live, b.dataset.name)));
      $("#lAdd2").addEventListener("click", () => go("#/addon-add"));
      return;
    }
    $("#rows").innerHTML = `<div class="pad live-tools"><input class="field" id="lq" type="search" placeholder="Rechercher une chaîne" autocomplete="off">
        <button class="icon-btn" id="lAdd" aria-label="Ajouter une liste M3U">${ICON.plus}</button></div>
      <div class="chips pad" id="lcats"><button class="chip on" data-c="*">Toutes</button>${rows.map((r, i) => `<button class="chip" data-c="${i}">${esc(r.title)}</button>`).join("")}</div>
      <div class="ltiles pad" id="lgrid"><div class="empty small">Chargement des chaînes…</div></div>`;
    $("#lAdd").addEventListener("click", () => go("#/addon-add"));
    const all = []; const seen = new Set();
    const st = { c: "*", q: "" };
    const initials = (n) => esc(String(n).replace(/[^\p{L}\p{N} ]/gu, "").split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join("").toUpperCase() || "TV");
    const tile = (m) => `<button class="ltile" data-open="${esc(m.type)}|${esc(m.id)}" data-cn="${esc(chanKey(m.name))}" aria-label="${esc(m.name)}">
        <span class="llogo">${m.poster ? `<img src="${esc(m.poster)}" alt="" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('i'),{textContent:'${initials(m.name)}'}))">` : `<i>${initials(m.name)}</i>`}</span>
        <b>${esc(m.name)}</b>${(() => { const e = nowNext(m); return e ? `<span class="lnow">${esc(e.now.t)}</span><span class="lbar"><i style="width:${e.pct}%"></i></span><span class="lnext">${hm(e.now.b)} · ${esc(e.next ? e.next.t : "")}</span>` : ""; })()}
        <span class="llive">● Direct</span></button>`;
    const draw = () => {
      const q = st.q.toLowerCase();
      const l = all.filter((x) => (st.c === "*" || x.cat === st.c) && (!q || x.m.name.toLowerCase().includes(q)));
      $("#lgrid").innerHTML = l.map((x) => tile(x.m)).join("") || `<div class="empty small">Aucune chaîne.</div>`;
    };
    $("#lq").addEventListener("input", (e) => { st.q = e.target.value; draw(); });
    $("#lcats").addEventListener("click", (e) => { const b = e.target.closest("[data-c]"); if (!b) return; st.c = b.dataset.c;
      document.querySelectorAll("#lcats .chip").forEach((c) => c.classList.toggle("on", c === b)); draw(); });
    await Promise.all(rows.map(async (r, i) => {
      try { (await catalog(r.a, r.c, r.extra)).forEach((m) => { if (!seen.has(m.id)) { seen.add(m.id); all.push({ m, cat: String(i) }); } }); } catch {}
    }));
    if (!$("#lgrid")) return;
    draw();
    $("#lgrid").insertAdjacentHTML("afterend", `<div class="pad" style="margin-top:18px"><h2 class="lhead">Suggestions de chaînes</h2></div>${liveSuggHTML()}<div style="height:20px"></div>`);
    $("#rows").querySelectorAll("[data-live]").forEach((b) => b.addEventListener("click", () => addLive(b.dataset.live, b.dataset.name)));
    // Programme TV : chargé une fois, puis mis à jour chaque minute tant que la page est ouverte
    loadEpg().then(() => { if ($("#lgrid")) draw(); });
    const tick = setInterval(() => { if (!$("#lgrid")) return clearInterval(tick); if (!document.hidden) draw(); }, 60000);
    // Logos officiels : recherche en arrière-plan, mise à jour des tuiles au fur et à mesure
    resolveLogos(all.map((x) => ({ name: x.m.name, logo: x.m.poster })), (c, url) => {
      all.forEach((x) => { if (chanKey(x.m.name) === chanKey(c.name)) x.m.poster = url; });
      document.querySelectorAll(`.ltile[data-cn="${chanKey(c.name)}"] .llogo`).forEach((el) => { el.innerHTML = `<img src="${esc(url)}" alt="">`; });
    });
  }

  // « Voir tout » d'une rangée
  async function screenCatalog(key) {
    setTabs("home");
    const row = [...buildRows(null), ...buildRows("movie"), ...buildRows("series")].find((r) => r.key === key);
    app.innerHTML = header(row ? row.title : "Catalogue", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`)
      + `<div class="grid" id="g" style="margin-top:8px"></div><div id="more" class="empty small">Chargement…</div>`;
    if (!row) { $("#more").textContent = "Catalogue introuvable."; return; }
    const ids = new Set(); let busy = false, end = false;
    const load = async () => {
      if (busy || end) return; busy = true;
      const extra = { ...row.extra }; if (ids.size && catSupports(row.c, "skip")) extra.skip = String(ids.size);
      let list = []; try { list = await catalog(row.a, row.c, extra); } catch { /* */ }
      const fresh = list.filter((m) => !ids.has(m.id)); fresh.forEach((m) => ids.add(m.id));
      $("#g").insertAdjacentHTML("beforeend", fresh.map(portraitHTML).join(""));
      if (!fresh.length || !catSupports(row.c, "skip")) { end = true; $("#more").textContent = ids.size ? "" : "Rien à afficher."; }
      busy = false;
    };
    new IntersectionObserver((e) => e[0].isIntersecting && load(), { rootMargin: "600px 0px" }).observe($("#more"));
    load();
  }

  function screenList() {
    setTabs("list");
    app.innerHTML = topBar("Ma liste") + (S.lib.length
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
      catalogAddons().forEach((a) => a.manifest.catalogs.filter((c) => catSupports(c, "search") && catOnlyRequires(c, "search")).forEach((c) => cats.push({ a, c })));
      const out = await Promise.all(cats.map(async ({ a, c }) => {
        try { return { title: a.manifest.id === "tvo.freetv.fr" || a.manifest.id.startsWith("tvo.m3u.") ? c.name : ["com.linvo.cinemeta", "tmdb"].includes(a.manifest.id) ? typeLabel(c.type) : `${c.name} · ${a.manifest.name}`, list: await catalog(a, c, { search: v }) }; }
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

  const stars = (r) => { const n = Math.round((parseFloat(r) || 0) / 2); return n ? "★".repeat(n) + "☆".repeat(5 - n) : ""; };
  async function screenTitle(type, id) {
    setTabs(null);
    app.innerHTML = `<div class="dt-hero skeleton"></div><div class="empty">Chargement…</div>`;
    const m = await meta(type, id);
    if (!m) { app.innerHTML = header("", `<button class="icon-btn" onclick="history.back()">${ICON.back}</button>`) + `<div class="empty"><h2>Fiche introuvable</h2>Aucun add-on n'a pu fournir ce titre.</div>`; return; }
    const { vid, prog } = resumeTarget(m);
    const tv = m.videos.find((v) => v.id === vid);
    const started = prog && !finished(prog) && fraction(prog) > 0.02;
    const label = started ? (tv && tv.season != null ? `Reprendre · É${tv.episode}` : "Reprendre")
      : (tv && tv.season != null ? `Lecture · S${tv.season} É${tv.episode}` : "Lecture");
    const seasons = [...new Set(m.episodes.map((e) => e.season))];
    let season = tv && tv.season != null ? tv.season : seasons[0];
    const tabsDef = [...(seasons.length ? [["eps", "Épisodes"]] : []), ...(m.people && m.people.length ? [["cast", "Distribution"]] : []),
      ...(m.tmdbId ? [["sim", "Similaires"]] : []), ["info", "Infos"]];
    let tab = tabsDef[0][0];
    const metaRow = [m.releaseInfo, m.imdbRating ? `<span class="stars">${stars(m.imdbRating)}</span>` : "", m.genres && m.genres[0] ? esc(genreLabel(m.genres[0])) : "", m.runtime ? esc(m.runtime) : ""]
      .filter(Boolean).join('<i class="sep"></i>');

    app.innerHTML = `
      <div class="dt-hero">
        <img src="${esc(m.background || m.poster || "")}" alt="">
        <div class="shade"></div>
        <button class="icon-btn round floating-back" onclick="history.back()" aria-label="Retour">${ICON.back}</button>
      </div>
      <div class="dt-body">
        ${m.logo ? `<img class="dt-logo" src="${esc(m.logo)}" alt="${esc(m.name)}">` : `<h1>${esc(m.name)}</h1>`}
        <div class="dt-meta">${metaRow}</div>
        ${m.description ? `<p class="dt-desc clamp" id="desc">${esc(m.description)}</p>` : !tmdbOn() ? `<p class="dt-desc small" style="color:var(--cyan)">Résumé en français indisponible pour l'instant.</p>` : ""}
        <button class="btn pill block" id="play">${ICON.play} ${esc(label)}</button>
        ${started ? `<div class="dt-prog"><i style="width:${Math.round(fraction(prog) * 100)}%"></i></div>` : ""}
        <div class="dt-actions">
          <button id="lib">${inLib(m.id) ? ICON.check : ICON.plus}<span>Ma liste</span></button>
          ${m.tmdbId ? `<button id="trailer"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z" fill="currentColor"/></svg><span>Bande-annonce</span></button>` : ""}
          <button id="share"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 3v12M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/></svg><span>Partager</span></button>
        </div>
      </div>
      <div class="dt-tabs" id="dtTabs"></div>
      <div id="dtPane"></div>`;

    const tr = $("#trailer"); if (tr) tr.addEventListener("click", () => playTrailer(m));
    const drawTabs = () => {
      $("#dtTabs").innerHTML = tabsDef.map(([k, l]) => `<button class="${k === tab ? "on" : ""}" data-t="${k}">${l}</button>`).join("");
      $("#dtTabs").querySelectorAll("[data-t]").forEach((b) => b.addEventListener("click", () => { tab = b.dataset.t; drawTabs(); drawPane(); }));
    };
    const drawPane = async () => {
      const pane = $("#dtPane");
      if (tab === "eps") {
        pane.innerHTML = `<div class="snum">${seasons.map((x) => `<button class="${x === season ? "on" : ""}" data-s="${x}">${x === 0 ? "Bonus" : String(x).padStart(2, "0")}</button>`).join("")}</div>
          <h3 class="pane-t">${season === 0 ? "Bonus" : "Saison " + season} · ${m.episodes.filter((e) => e.season === season).length} épisodes</h3>
          <div class="eplist">${m.episodes.filter((e) => e.season === season).map((e) => {
            const p = S.prog.find((x) => x.videoId === e.id);
            return `<button class="ep" data-v="${esc(e.id)}"><span class="th">${e.thumbnail || m.background ? `<img src="${esc(e.thumbnail || m.background)}" alt="" loading="lazy">` : ""}
                <span class="pl">${ICON.play}</span>${p ? `<span class="bar"><i style="width:${Math.round(fraction(p) * 100)}%"></i></span>` : ""}</span>
              <span class="tx"><b>${e.episode ?? "?"}. ${esc(e.title || "Épisode " + (e.episode ?? ""))}</b>
                <small>${p && finished(p) ? "Vu · " : ""}${e.released ? new Date(e.released).toLocaleDateString("fr-FR") : ""}</small>
                ${e.overview ? `<span class="ov">${esc(e.overview)}</span>` : ""}</span></button>`;
          }).join("")}</div>`;
        pane.querySelectorAll("[data-s]").forEach((b) => b.addEventListener("click", () => { season = Number(b.dataset.s); drawPane(); }));
        pane.querySelectorAll("[data-v]").forEach((b) => b.addEventListener("click", () => go(`#/sources/${enc(m.type)}/${enc(m.id)}/${enc(b.dataset.v)}`)));
        const on = pane.querySelector(".snum .on"); if (on) on.scrollIntoView({ inline: "center", block: "nearest" });
      } else if (tab === "cast") {
        pane.innerHTML = `<div class="castgrid">${m.people.map((pp) => `<a class="bubble" href="#/person/${enc(pp.id)}"><span class="ph">${pp.photo ? `<img src="${esc(pp.photo)}" alt="" loading="lazy">` : esc(pp.name.slice(0, 1))}</span>
          <b>${esc(pp.name)}</b>${pp.role ? `<small>${esc(pp.role)}</small>` : ""}</a>`).join("")}</div>`;
      } else if (tab === "sim") {
        pane.innerHTML = `<div class="empty small">Chargement…</div>`;
        try {
          const r = await tget(`/${m.tmdbKind}/${m.tmdbId}/recommendations`);
          const list = (r.results || []).map((o) => tpreview(o, m.tmdbKind === "tv" ? "series" : "movie")).filter((x) => x && x.poster);
          pane.innerHTML = list.length ? `<div class="grid">${list.map(portraitHTML).join("")}</div>` : `<div class="empty small">Aucune suggestion.</div>`;
        } catch { pane.innerHTML = `<div class="empty small">Suggestions indisponibles.</div>`; }
      } else {
        pane.innerHTML = `<div class="list"><div class="card info">
          ${m.genres && m.genres.length ? `<p><b>Genres</b>${m.genres.map(genreLabel).map(esc).join(", ")}</p>` : ""}
          ${m.director && m.director.length ? `<p><b>Réalisation</b>${m.director.map(esc).join(", ")}</p>` : ""}
          ${m.cast && m.cast.length ? `<p><b>Avec</b>${m.cast.map(esc).join(", ")}</p>` : ""}
          ${m.releaseInfo ? `<p><b>Année</b>${esc(m.releaseInfo)}</p>` : ""}${m.runtime ? `<p><b>Durée</b>${esc(m.runtime)}</p>` : ""}
          ${m.imdbRating ? `<p><b>Note</b>${esc(m.imdbRating)} / 10</p>` : ""}</div></div>`;
      }
    };
    drawTabs(); drawPane();

    $("#play").addEventListener("click", () => go(`#/sources/${enc(m.type)}/${enc(m.id)}/${enc(vid)}`));
    $("#lib").addEventListener("click", (e) => { toggleLib(m); e.currentTarget.innerHTML = (inLib(m.id) ? ICON.check : ICON.plus) + "<span>Ma liste</span>"; toast(inLib(m.id) ? "Ajouté à ta liste" : "Retiré de ta liste"); });
    $("#share").addEventListener("click", async () => {
      const text = `${m.name}${m.releaseInfo ? " (" + m.releaseInfo + ")" : ""} — à regarder sur TVO`;
      try { if (navigator.share) await navigator.share({ title: m.name, text }); else { await navigator.clipboard.writeText(text); toast("Copié"); } } catch { /* annulé */ }
    });
    const d = $("#desc"); if (d) d.addEventListener("click", () => d.classList.toggle("clamp"));
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
    const found = []; let done = 0;
    const test = new Map(); // url -> { ms, ok }
    const openQ = new Set();
    const lang = (s) => { const t = (s.name + " " + s.title).toLowerCase();
      return /\bmulti\b/.test(t) ? "MULTI" : /\b(vff|vfq|vf2?|french|truefrench|fr)\b|🇫🇷/.test(t) ? "VF" : /\bvostfr\b/.test(t) ? "VOSTFR" : ""; };
    const size = (s) => { const m = (s.name + " " + s.title).match(/(\d+(?:[.,]\d+)?)\s?(GB|Go|MB|Mo)\b/i); return m ? m[1].replace(",", ".") + " " + (/g/i.test(m[2]) ? "Go" : "Mo") : ""; };
    const score = (s) => { const r = test.get(s.url); const alive = !r ? 1 : r.ok ? 2 : 0;
      return alive * 1e7 + (lang(s) ? 1e6 : 0) + s.quality * 1e5 - (r && r.ok ? Math.min(r.ms, 99999) : 50000); };
    // Test rapide : on mesure le temps de réponse de chaque lien (sans télécharger la vidéo)
    const queue = []; let running = 0;
    const pump = () => {
      while (running < 6 && queue.length) {
        const s = queue.shift(); running++;
        const t0 = performance.now(); const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), 6000);
        fetch(s.url, { method: "HEAD", signal: ctl.signal, headers: s.headers || {} })
          .then((r) => test.set(s.url, { ok: r.ok || r.status === 405 || r.status === 206, ms: Math.round(performance.now() - t0) }))
          .catch(() => test.set(s.url, { ok: false, ms: 99999 }))
          .finally(() => { clearTimeout(to); running--; schedule(); pump(); });
      }
    };
    let raf = 0; const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(() => draw(st.total)); };
    const badge = (s) => { if (!s.url) return `<span class="sp ext">Lien externe</span>`;
      const r = test.get(s.url); if (!r) return `<span class="sp wait">Test…</span>`;
      if (!r.ok) return `<span class="sp bad">Hors ligne</span>`;
      return `<span class="sp ${r.ms < 400 ? "fast" : r.ms < 1200 ? "mid" : "slow"}">⚡ ${r.ms < 1000 ? r.ms + " ms" : (r.ms / 1000).toFixed(1) + " s"}</span>`; };
    const row = (s) => `<button class="card source" data-k="${esc(s._k)}">
        <span style="min-width:0;flex:1"><span class="name">${esc(s.name.replace(/\n/g, " ").slice(0, 80))}</span>
        <span class="tags">${badge(s)}${lang(s) ? `<span class="tag">${lang(s)}</span>` : ""}${size(s) ? `<span class="tag">${size(s)}</span>` : ""}<span class="from">${esc(s.addonName)}</span></span></span>
        <span class="playic">${ICON.play}</span></button>`;
    const draw = (total) => {
      const playable = found.filter((s) => s.url || s.externalUrl).sort((a, b) => score(b) - score(a));
      const torrents = found.length - playable.length;
      const tested = playable.filter((s) => s.url && test.has(s.url)).length, toTest = playable.filter((s) => s.url).length;
      $("#status").textContent = total === 0 ? "Aucun add-on de sources installé."
        : done < total ? `Recherche des sources… (${done}/${total} add-ons)`
        : tested < toTest ? `${playable.length} sources · test des liens ${tested}/${toTest}…`
        : `${playable.length} sources testées et classées` + (torrents ? ` · ${torrents} torrent(s) non pris en charge` : "");
      if (!playable.length) { $("#list").innerHTML = done >= total ? `<div class="empty">${total === 0 ? "Ajoute un add-on qui fournit des vidéos dans Réglages › Add-ons." : "Aucune source lisible pour ce titre."}</div>` : ""; return; }
      const best = playable.find((s) => !s.url || (test.get(s.url) || {}).ok !== false) || playable[0];
      const groups = [4, 3, 2, 1, 0].map((q) => [q, playable.filter((s) => s.quality === q)]).filter(([, l]) => l.length);
      $("#list").innerHTML = `<div class="best-src"><div class="small muted">Meilleure source</div>${row(best)}</div>` +
        groups.map(([q, l]) => { const ok = l.filter((s) => !s.url || (test.get(s.url) || {}).ok !== false).length;
          return `<details class="qgroup" data-q="${q}" ${openQ.has(q) ? "open" : ""}><summary><b>${qLabel(q) === "Auto" ? "Autres" : qLabel(q)}</b><span class="small muted">${l.length} lien${l.length > 1 ? "s" : ""}${ok < l.length ? ` · ${ok} en ligne` : ""}</span><i class="chev"></i></summary>
            <div class="list">${l.map(row).join("")}</div></details>`; }).join("");
      $("#list").querySelectorAll("details.qgroup").forEach((d) => d.addEventListener("toggle", () => { const q = +d.dataset.q; d.open ? openQ.add(q) : openQ.delete(q); }));
      $("#list").querySelectorAll("[data-k]").forEach((b) => b.addEventListener("click", () => { const s = playable.find((x) => x._k === b.dataset.k); if (s) playStream(m, s, videoId); }));
    };
    let k = 0;
    const st = streams(type, videoId, (items) => {
      items.forEach((s) => { s._k = String(k++); found.push(s); if (s.url && /^https?:/.test(s.url) && !test.has(s.url)) queue.push(s); });
      done++; schedule(); pump();
    });
    draw(st.total);
  }

  /** Réglages en sections repliables (ouvertes/fermées mémorisées). */
  const SECT_INFO = { "Compte": "Ton compte TVO et la synchronisation", "Gestion des profils": "Statut, accès, essai et temps d'écran", "Clés API": "TMDB et clé de synchronisation (masquées)", "Connecter une TV": "QR code ou code à 6 chiffres", "Lecteur": "Lecteur intégré ou appli externe",
    "Titres et résumés (TMDB)": "Catalogues et fiches en français", "Add-ons": "Catalogues, sources, chaînes, sous-titres, accès par profil", "Page d'accueil": "Rangées affichées et ordre", "Application": "Version et mises à jour" };
  function makeCollapsible(root, openByDefault) {
    const st = LS.get("setopen", {});
    root.querySelectorAll("h2.section-title.section").forEach((h) => {
      const title = h.textContent.trim();
      const d = document.createElement("details"); d.className = "sect";
      d.open = title in st ? st[title] : openByDefault.includes(title);
      d.innerHTML = `<summary class="sect-head"><span class="sect-t"><b>${esc(title)}</b>${SECT_INFO[title] ? `<small>${esc(SECT_INFO[title])}</small>` : ""}</span><i class="chev"></i></summary><div class="sect-body"></div>`;
      const body = d.lastElementChild; let n = h.nextElementSibling;
      h.replaceWith(d);
      while (n && !n.matches("h2.section-title.section")) { const nx = n.nextElementSibling; body.appendChild(n); n = nx; }
      d.addEventListener("toggle", () => { st[title] = d.open; LS.set("setopen", st); });
    });
    // Un seul bloc groupé (style réglages iPhone) au lieu d'une pile de cartes
    const all = [...root.querySelectorAll("details.sect")];
    if (all.length) { const g = document.createElement("div"); g.className = "sect-group"; all[0].before(g); all.forEach((d) => g.appendChild(d)); }
  }
  function screenSettings() {
    setTabs(null);
    const p = S.profile;
    const draw = () => {
      app.innerHTML = header("Réglages", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `
      <div class="list" style="margin-top:8px">
        <div class="card" style="display:flex;align-items:center;gap:12px">${avatar(p, "sm")}<span style="flex:1;min-width:0;display:grid;gap:3px"><b>${esc(p.name)}</b><span><span class="pstat ${profileStatus(p)[1]}">${esc(profileStatus(p)[0])}</span>${p.limit ? ` <span class="small muted">· ${fmtMin(usedToday(p.id))} / ${fmtMin(p.limit)} aujourd'hui</span>` : ""}</span></span>
          <button class="btn" id="editP">${ICON.edit} Modifier</button></div>
        <button class="btn block" id="switchP">Changer de profil</button>
      </div>
      ${isAdmin(p) ? `<h2 class="section-title section"><span>Gestion des profils</span></h2>
      <div class="list" id="pman"></div>
      <h2 class="section-title section"><span>Clés API</span></h2>
      <div class="list">
        <div class="card small muted">Visibles par l'administrateur uniquement. Masquées par défaut.</div>
        <div class="card keyrow"><span class="ht"><b>TMDB</b><small>Titres, résumés, affiches en français</small></span></div>
        <div class="keybox"><input class="field selectable" id="kTmdb" type="password" autocomplete="off" spellcheck="false" value="${esc(tmdbKey() || "")}" placeholder="Aucune clé">
          <button class="icon-btn" data-eye="kTmdb" aria-label="Afficher">👁</button><button class="icon-btn" data-copy="kTmdb" aria-label="Copier">⧉</button></div>
        <div class="two-btn"><button class="btn primary" id="kSave">Enregistrer</button>${tmdbKey() ? '<button class="btn danger" id="kDel">Retirer</button>' : ""}</div>
        <div class="card keyrow"><span class="ht"><b>Clé de synchronisation</b><small>Identifie tes données sur le serveur. Ne la partage jamais.</small></span></div>
        <div class="keybox"><input class="field selectable" id="kSync" type="password" readonly value="${esc(syncId())}"><button class="icon-btn" data-eye="kSync" aria-label="Afficher">👁</button><button class="icon-btn" data-copy="kSync" aria-label="Copier">⧉</button></div>
      </div>` : ""}
      <h2 class="section-title section"><span>Compte</span></h2>
      <div class="list">
        ${acct() ? `<div class="card" style="display:flex;align-items:center;gap:12px"><div style="flex:1;min-width:0"><b>Connecté</b><div class="small muted">Compte « ${esc(acct().user)} » · tes appareils sont synchronisés</div></div><button class="btn" id="acctOut">Se déconnecter</button></div>
          <details class="card"><summary class="small">Changer le mot de passe</summary><div class="list" style="padding:10px 0 0">
            <input class="field" id="pOld" type="password" autocomplete="current-password" placeholder="Mot de passe actuel">
            <input class="field" id="pNew" type="password" autocomplete="new-password" placeholder="Nouveau mot de passe">
            <div class="pmeter" id="pNewM"><i></i></div>
            <button class="btn primary block" id="pGo">Changer</button>
            <div class="small muted">Changer le mot de passe déconnecte aussi tes autres appareils.</div></div></details>
          <details class="card"><summary class="small">Déconnecter tous mes appareils</summary><div class="list" style="padding:10px 0 0">
            <div class="small muted">Si tu penses que quelqu'un utilise ton compte : tous les autres appareils sont déconnectés immédiatement.</div>
            <input class="field" id="rPass" type="password" autocomplete="current-password" placeholder="Ton mot de passe">
            <button class="btn danger block" id="rGo">Déconnecter les autres appareils</button></div></details>`
        : `<div class="card small muted">Crée ton compte TVO pour retrouver Ma liste, Continuer à regarder, tes profils et tes add-ons sur tous tes appareils.</div>
          <div class="seg wide" id="acctSeg"><button class="on" data-m="in">Se connecter</button><button data-m="up">Créer un compte</button></div>
          <input class="field" id="aUser" autocapitalize="off" autocorrect="off" spellcheck="false" autocomplete="username" placeholder="Identifiant">
          <input class="field" id="aPass" type="password" autocomplete="current-password" placeholder="Mot de passe">
          <div class="pmeter" id="aPassM" hidden><i></i><span></span></div>
          <input class="field" id="aPass2" type="password" autocomplete="new-password" placeholder="Confirme le mot de passe" hidden>
          <label class="small muted showpw"><input type="checkbox" id="aShow"> Afficher le mot de passe</label>
          <button class="btn primary block" id="aGo">Se connecter</button>
          <div id="aMsg" class="small" style="color:#ff8a8a"></div>`}
        <div id="syncStatus" class="small" style="color:var(--cyan)">${esc(syncState.status)}</div>
      </div>
      <h2 class="section-title section"><span>Connecter une TV</span></h2>
      <div class="list">
        <div class="card small muted">Sur la TV : Réglages › Compte › Connexion rapide. Scanne le QR code avec l'appareil photo, ou saisis le code affiché.</div>
        <div style="display:grid;grid-template-columns:1fr auto;gap:8px">
          <input class="field" id="tvCode" inputmode="numeric" maxlength="6" placeholder="Code TV (6 chiffres)" autocomplete="one-time-code">
          <button class="btn primary" id="tvGo">Connecter</button></div>
        <details class="card"><summary class="small">Relier sans compte (code entre appareils)</summary>
          <div class="list" style="padding:10px 0 0">
          <div class="two-btn"><button class="btn" id="syncNow">Synchroniser</button><button class="btn" id="syncCode">Afficher un code</button></div>
          <div id="codeBox"></div>
          <div style="display:grid;grid-template-columns:1fr auto;gap:8px">
            <input class="field" id="claim" inputmode="numeric" maxlength="6" placeholder="J'ai un code (6 chiffres)" autocomplete="one-time-code">
            <button class="btn primary" id="claimGo">Relier</button></div></div></details>
      </div>
      <h2 class="section-title section"><span>Lecteur</span></h2>
      <div class="chips">${[["tvo", "Lecteur TVO (recommandé)"], ["vlc", "Appli VLC"], ...(isIOS ? [["infuse", "Infuse"]] : [])].map(([k, l]) => `<button class="chip ${S.player === k ? "on" : ""}" data-pl="${k}">${l}</button>`).join("")}</div>
      <p class="pad small muted">Le lecteur TVO lit MP4, HLS et la plupart des MKV. Pour un fichier au son illisible (DTS, TrueHD), choisis VLC ou Infuse s'ils sont installés.</p>
      <h2 class="section-title section"><span>Titres et résumés (TMDB)</span></h2>
      <div class="list">
        <div class="card small muted">${tmdbKey() ? `Clé TMDB active (…${esc(tmdbKey().slice(-4))}). Catalogues, fiches et épisodes en français.` : "Ajoute ta clé API TMDB (gratuite sur themoviedb.org › Paramètres › API) : catalogues, titres, résumés et affiches en français. Elle passe aussi sur la TV par la synchro."}</div>
        <input class="field" id="tmdb" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="Clé d'API ou jeton d'accès en lecture">
        <div class="two-btn"><button class="btn primary" id="tmdbSave">Enregistrer</button>${tmdbKey() ? '<button class="btn danger" id="tmdbDel">Retirer</button>' : '<a class="btn" href="https://www.themoviedb.org/settings/api" target="_blank" rel="noopener">Obtenir une clé</a>'}</div>
      </div>
      <h2 class="section-title section"><span>Add-ons</span></h2>
      <p class="pad small muted" style="margin-top:-4px">Compatible avec les add-ons Stremio / Nuvio existants : colle l'adresse de leur manifest.json. Le premier est prioritaire. Les sources torrent seules ne sont pas lisibles.</p>
      <div class="list">${CATEGORIES.map((cat) => [cat, S.addons.map((a, i) => [a, i]).filter(([a]) => addonCategory(a) === cat)]).filter(([, g]) => g.length).map(([cat, g]) => `<div class="small" style="color:var(--cyan);font-weight:700;margin-top:6px">${cat}</div>` + g.map(([a, i]) => {
        const kinds = a.manifest.resources.map((r) => ({ catalog: "catalogues", meta: "fiches", stream: "vidéos", subtitles: "sous-titres" }[r.name])).filter(Boolean);
        return `<div class="card addon"><div class="top">${a.manifest.logo ? `<img src="${esc(a.manifest.logo)}" alt="" onerror="this.remove()">` : ""}
          <div style="min-width:0"><b>${esc(a.manifest.name)}</b> <span class="small muted">${esc(a.manifest.version)}</span>
          <div class="small" style="color:var(--cyan)">${kinds.join(" · ") || "—"}</div>
          ${a.manifest.description ? `<div class="small muted">${esc(a.manifest.description)}</div>` : ""}</div></div>
          ${isAdmin(p) && S.profiles.length > 1 ? `<div class="ptog"><span class="small muted">Activé pour</span>${S.profiles.map((pp) => `<button class="chip ${((pp.off || []).includes(a.manifest.id)) ? "" : "on"}" data-pa="${i}|${esc(pp.id)}">${esc(pp.name)}</button>`).join("")}</div>` : ""}
          ${!isAdmin(p) ? "" : `<div class="actions">${i > 0 ? `<button class="btn" data-up="${i}">${ICON.up} Monter</button>` : ""}${a.manifest.configurable && /^https?:/.test(a.url) ? `<a class="btn" href="${esc(a.url)}/configure" target="_blank" rel="noopener">Configurer</a>` : ""}${/^https?:/.test(a.url) ? `<button class="btn" data-rf="${i}">Actualiser</button>` : ""}<button class="btn danger" data-rm="${i}">${ICON.trash} Retirer</button></div>`}</div>`;
      }).join("")).join("")}
        ${isAdmin(p) ? `<button class="btn accent block" id="addA">${ICON.plus} Ajouter un add-on</button>` : `<div class="card small muted">Seul l'administrateur peut ajouter, retirer ou activer des add-ons pour chaque profil.</div>`}</div>
      <h2 class="section-title section"><span>Page d'accueil</span></h2>
      <p class="pad small muted" style="margin-top:-4px">Choisis les rangées affichées et leur ordre. Chaque catalogue d'add-on peut devenir une rangée.</p>
      <div class="list" id="homeEd"></div>
      <h2 class="section-title section"><span>Application</span></h2>
      <div class="list"><div class="card">Version ${esc(VERSION.name)}<div class="small muted">TVO n'héberge aucun contenu : les vidéos viennent des add-ons que tu ajoutes.</div></div>
        <button class="btn block" id="upd">Rechercher une mise à jour</button></div>`;
      makeCollapsible(app, acct() ? [] : ["Compte"]);
      $("#editP").addEventListener("click", () => go("#/profile/" + enc(p.id)));
      const drawHome = () => {
        const h = homeLayout();
        const special = [["continue", "Continuer à regarder", "Reprends là où tu t'es arrêté"], ["library", "Ma liste", "Tes films et séries enregistrés"], ["collections", "Sagas et collections", "Toy Story, Fast & Furious, Harry Potter…"]];
        const idx = (k) => { const i = h.order.indexOf(k); return i < 0 ? 1e9 : i; };
        const all = buildRows(null).sort((a, b) => idx(a.key) - idx(b.key)).map((r) => [r.key, r.title]);
        const shown = all.filter(([k]) => !h.hidden.includes(k)), off = all.filter(([k]) => h.hidden.includes(k));
        const sw = (k, on) => `<label class="sw mini"><input type="checkbox" data-hv="${esc(k)}" ${on ? "checked" : ""}><i></i></label>`;
        $("#homeEd").innerHTML = `
          <div class="hsub">Rangées spéciales</div>
          ${special.map(([k, t, d]) => `<div class="card hrow"><span class="ht"><b>${esc(t)}</b><small>${esc(d)}</small></span>${sw(k, !h.hidden.includes(k))}</div>`).join("")}
          <div class="hsub">Catalogues affichés <span>${shown.length}</span></div>
          ${shown.map(([k, t], i) => `<div class="card hrow"><span class="ht"><b>${esc(t)}</b></span>
            <button class="icon-btn hmv" data-mv="${esc(k)}|-1" ${i === 0 ? "disabled" : ""} aria-label="Monter">${ICON.up}</button>
            <button class="icon-btn hmv down" data-mv="${esc(k)}|1" ${i === shown.length - 1 ? "disabled" : ""} aria-label="Descendre">${ICON.up}</button>${sw(k, true)}</div>`).join("") || `<div class="card small muted">Aucun catalogue affiché.</div>`}
          ${off.length ? `<details class="card hoff"><summary><b>Catalogues masqués</b> <span class="small muted">${off.length}</span><i class="chev"></i></summary>
            ${off.map(([k, t]) => `<div class="hrow"><span class="ht"><b style="color:var(--muted)">${esc(t)}</b></span>${sw(k, false)}</div>`).join("")}</details>` : ""}
          <div class="two-btn"><button class="btn" id="hAll">Tout afficher</button><button class="btn" id="hReset">Ordre par défaut</button></div>`;
        $("#homeEd").querySelectorAll("[data-hv]").forEach((b) => b.addEventListener("change", () => {
          const k = b.dataset.hv; const hh = homeLayout();
          setHome({ ...hh, hidden: b.checked ? hh.hidden.filter((x) => x !== k) : [...new Set([...hh.hidden, k])] }); drawHome();
        }));
        $("#homeEd").querySelectorAll("[data-mv]").forEach((b) => b.addEventListener("click", () => {
          const [k, d] = b.dataset.mv.split("|"); const keys = shown.map((r) => r[0]);
          const i = keys.indexOf(k), j = i + Number(d); if (j < 0 || j >= keys.length) return;
          keys.splice(j, 0, keys.splice(i, 1)[0]); setHome({ ...homeLayout(), order: [...keys, ...off.map((r) => r[0])] }); drawHome();
        }));
        $("#hAll").addEventListener("click", () => { setHome({ ...homeLayout(), hidden: [] }); drawHome(); toast("Toutes les rangées sont affichées"); });
        $("#hReset").addEventListener("click", () => { setHome({ ...homeLayout(), order: [] }); drawHome(); toast("Ordre par défaut rétabli"); });
      };
      drawHome();
      $("#tmdbSave").addEventListener("click", async () => {
        const k = $("#tmdb").value.trim(); if (!k) return toast("Colle ta clé TMDB");
        toast("Vérification…");
        try { await tget("/configuration", {}, k); } catch (e) { return toast("Clé refusée par TMDB (" + e.message + ")", 4000); }
        LS.set("tmdb_key", k); LS.set("tmdb_t", Date.now()); cache.clear(); metaCache.clear(); changed(); syncNow();
        toast("Clé TMDB enregistrée ✓"); draw();
      });
      const td = $("#tmdbDel");
      if (td) td.addEventListener("click", () => { LS.set("tmdb_key", null); LS.set("tmdb_t", Date.now()); cache.clear(); metaCache.clear(); changed(); syncNow(); draw(); });
      const gIn = $("#gIn"); if (gIn) gIn.addEventListener("click", () => signIn("google"));
      const aIn = $("#aIn"); if (aIn) aIn.addEventListener("click", () => signIn("apple"));
      const mg = $("#mailGo"); if (mg) mg.addEventListener("click", async () => {
        const v = $("#mail").value.trim(); if (!/^[^@\s]+@[^@\s]+\.[a-z]{2,}$/i.test(v)) return toast("Adresse e-mail invalide");
        mg.disabled = true; try { await signInEmail(v); toast("E-mail envoyé ✓ Ouvre le lien sur ce téléphone.", 4500); } catch (e) { toast(e.message, 4000); } mg.disabled = false;
      });
      const seg = $("#acctSeg"); let mode = "in";
      if (seg) seg.addEventListener("click", (e) => { const b = e.target.closest("[data-m]"); if (!b) return; mode = b.dataset.m;
        seg.querySelectorAll("button").forEach((x) => x.classList.toggle("on", x === b));
        $("#aPass2").hidden = mode !== "up"; $("#aPassM").hidden = mode !== "up"; $("#aGo").textContent = mode === "up" ? "Créer mon compte" : "Se connecter";
        $("#aPass").setAttribute("autocomplete", mode === "up" ? "new-password" : "current-password"); $("#aMsg").textContent = ""; });
      const meter = (input, box) => { if (!input || !box) return; input.addEventListener("input", () => {
        const sc = passScore(input.value), pb = passProblem($("#aUser") ? $("#aUser").value : (acct() || {}).user, input.value);
        box.dataset.s = input.value ? (pb ? 0 : sc) : ""; const sp = box.querySelector("span");
        if (sp) sp.textContent = !input.value ? "" : pb || ["", "Faible", "Moyen", "Bon", "Excellent"][Math.max(1, sc)];
      }); };
      meter($("#aPass"), $("#aPassM")); meter($("#pNew"), $("#pNewM"));
      const sh = $("#aShow"); if (sh) sh.addEventListener("change", () => ["aPass", "aPass2"].forEach((k) => ($("#" + k).type = sh.checked ? "text" : "password")));
      const aGo = $("#aGo"); if (aGo) aGo.addEventListener("click", async () => {
        const u = $("#aUser").value, pw = $("#aPass").value; $("#aMsg").textContent = "";
        if (mode === "up" && pw !== $("#aPass2").value) { $("#aMsg").textContent = "Les mots de passe ne correspondent pas"; return; }
        aGo.disabled = true; aGo.textContent = "Patiente…";
        try { if (mode === "up") await acctRegister(u, pw); else await acctLogin(u, pw); toast("Connecté ✓"); draw(); }
        catch (e) { $("#aMsg").textContent = e.message.replace(/^HTTP \d+$/, "Pas de connexion internet"); aGo.disabled = false; aGo.textContent = mode === "up" ? "Créer mon compte" : "Se connecter"; }
      });
      const ao = $("#acctOut"); if (ao) ao.addEventListener("click", () => { if (confirm("Se déconnecter du compte TVO sur ce téléphone ?")) { acctLogout(); draw(); } });
      const pGo = $("#pGo"); if (pGo) pGo.addEventListener("click", async () => {
        try {
          await checkNewPass(acct().user, $("#pNew").value);
          const r = await rpc("tvo_change_password", { p_user: acct().user, p_old: $("#pOld").value, p_new: $("#pNew").value });
          if (!r) return toast("Mot de passe actuel incorrect");
          if (typeof r === "string") { LS.set("sync_id", r); syncNow(); }
          $("#pOld").value = $("#pNew").value = ""; toast("Mot de passe changé ✓ Les autres appareils sont déconnectés.", 4000);
        } catch (e) { toast(e.message, 4000); }
      });
      const rGo = $("#rGo"); if (rGo) rGo.addEventListener("click", async () => {
        try { const r = await rpc("tvo_rotate", { p_user: acct().user, p_pass: $("#rPass").value });
          if (!r) return toast("Mot de passe incorrect");
          LS.set("sync_id", r); $("#rPass").value = ""; syncNow(); toast("Tous les autres appareils sont déconnectés ✓", 4000);
        } catch (e) { toast(e.message, 4000); }
      });
      const so = $("#signOut"); if (so) so.addEventListener("click", () => { signOut(); draw(); });
      $("#tvGo").addEventListener("click", async () => { if (await approveTv($("#tvCode").value)) $("#tvCode").value = ""; });
      $("#syncNow").addEventListener("click", async () => { $("#syncStatus").textContent = "Synchronisation…"; await syncNow(); $("#syncStatus").textContent = syncState.status; });
      $("#syncCode").addEventListener("click", async () => {
        $("#codeBox").innerHTML = '<div class="card">Création du code…</div>';
        try { const c = String(await pairCreate());
          $("#codeBox").innerHTML = `<div class="card" style="text-align:center"><div style="font-size:36px;font-weight:800;letter-spacing:.12em">${esc(c.slice(0, 3) + " " + c.slice(3))}</div><div class="small muted">Sur l'autre appareil : Réglages › J'ai un code. Valable 10 minutes.</div></div>`;
        } catch { $("#codeBox").innerHTML = '<div class="card">Pas de connexion.</div>'; }
      });
      $("#claimGo").addEventListener("click", async () => {
        const c = $("#claim").value.replace(/\D/g, "");
        if (c.length !== 6) return toast("Le code fait 6 chiffres");
        toast("Liaison…");
        if (await pairClaim(c)) { toast("Appareils reliés ✓"); go("#/profiles"); } else toast("Code invalide ou expiré");
      });
      $("#switchP").addEventListener("click", () => go("#/profiles"));
      if ($("#addA")) $("#addA").addEventListener("click", () => go("#/addon-add"));
      app.querySelectorAll("[data-eye]").forEach((b) => b.addEventListener("click", () => { const i = $("#" + b.dataset.eye); i.type = i.type === "password" ? "text" : "password"; b.classList.toggle("on", i.type === "text"); }));
      app.querySelectorAll("[data-copy]").forEach((b) => b.addEventListener("click", async () => { try { await navigator.clipboard.writeText($("#" + b.dataset.copy).value); toast("Copié ✓"); } catch { toast("Copie impossible"); } }));
      if ($("#kSave")) $("#kSave").addEventListener("click", async () => {
        const k = $("#kTmdb").value.trim(); if (!k) return toast("Colle ta clé TMDB");
        toast("Vérification…");
        try { await tget("/configuration", {}, k); } catch (e) { return toast("Clé refusée par TMDB", 4000); }
        LS.set("tmdb_key", k); LS.set("tmdb_t", Date.now()); cache.clear(); metaCache.clear(); changed(); syncNow(); toast("Clé enregistrée ✓"); draw();
      });
      if ($("#kDel")) $("#kDel").addEventListener("click", () => { if (!confirm("Retirer la clé TMDB ?")) return; LS.set("tmdb_key", null); LS.set("tmdb_t", Date.now()); cache.clear(); metaCache.clear(); changed(); draw(); });
      if ($("#pman")) {
        const drawP = () => {
          $("#pman").innerHTML = S.profiles.map((pp) => { const [st, cls] = profileStatus(pp); const adm = isAdmin(pp);
            const days = pp.trialUntil ? Math.max(0, Math.ceil((pp.trialUntil - Date.now()) / 864e5)) : 0;
            return `<div class="card pm">
              <div class="pm-top">${avatar(pp, "sm")}<span class="ht"><b>${esc(pp.name)}</b><span><span class="pstat ${cls}">${esc(st)}</span>${pp.limit ? ` <span class="small muted">${fmtMin(usedToday(pp.id))} / ${fmtMin(pp.limit)} aujourd'hui</span>` : ` <span class="small muted">${fmtMin(usedToday(pp.id))} aujourd'hui</span>`}</span></span>
                ${adm ? "" : `<label class="sw mini"><input type="checkbox" data-pon="${esc(pp.id)}" ${pp.disabled ? "" : "checked"}><i></i></label>`}</div>
              ${adm ? `<div class="small muted">L'administrateur a toujours accès à tout.</div>` : `
              <div class="pm-l">Période d'essai</div>
              <div class="chips">${[[0, "Aucune"], [3, "3 jours"], [7, "7 jours"], [30, "30 jours"]].map(([d, l]) => `<button class="chip ${(d === 0 ? !pp.trialUntil : pp.trialUntil && pp.trialDays === d) ? "on" : ""}" data-ptr="${esc(pp.id)}|${d}">${l}</button>`).join("")}</div>
              ${pp.trialUntil ? `<div class="small muted">${days > 0 ? `Se termine le ${new Date(pp.trialUntil).toLocaleDateString("fr-FR", { day: "numeric", month: "long" })} à ${hm(pp.trialUntil)}` : "Terminée : le profil ne peut plus regarder."}</div>` : ""}
              <div class="pm-l">Temps d'écran par jour</div>
              <div class="chips">${[[0, "Illimité"], [30, "30 min"], [60, "1 h"], [120, "2 h"], [180, "3 h"], [300, "5 h"]].map(([m, l]) => `<button class="chip ${(pp.limit || 0) === m ? "on" : ""}" data-plim="${esc(pp.id)}|${m}">${l}</button>`).join("")}</div>
              ${usedToday(pp.id) ? `<button class="btn sm" data-preset="${esc(pp.id)}">Remettre le compteur du jour à zéro</button>` : ""}`}
              <button class="btn sm" data-pedit="${esc(pp.id)}">Modifier le profil (nom, avatar, enfant, code)</button></div>`; }).join("")
            + `<button class="btn accent block" id="pAdd">${ICON.plus} Ajouter un profil</button>`;
          const upd = (id, f) => { const pp = S.profiles.find((x) => x.id === id); if (!pp) return; f(pp); pp.t = Date.now(); saveProfiles(); drawP(); };
          $("#pman").querySelectorAll("[data-pon]").forEach((b) => b.addEventListener("change", () => upd(b.dataset.pon, (pp) => { pp.disabled = !b.checked; })));
          $("#pman").querySelectorAll("[data-ptr]").forEach((b) => b.addEventListener("click", () => { const [id, d] = b.dataset.ptr.split("|"); upd(id, (pp) => { pp.trialUntil = +d ? Date.now() + +d * 864e5 : null; pp.trialDays = +d || null; }); }));
          $("#pman").querySelectorAll("[data-plim]").forEach((b) => b.addEventListener("click", () => { const [id, m] = b.dataset.plim.split("|"); upd(id, (pp) => { pp.limit = +m || null; }); }));
          $("#pman").querySelectorAll("[data-preset]").forEach((b) => b.addEventListener("click", () => { const u = LS.get("usage", {}); if (u[b.dataset.preset]) delete u[b.dataset.preset][today()]; LS.set("usage", u); drawP(); }));
          $("#pman").querySelectorAll("[data-pedit]").forEach((b) => b.addEventListener("click", () => go("#/profile/" + enc(b.dataset.pedit))));
          $("#pAdd").addEventListener("click", () => go("#/profile/new"));
        };
        drawP();
      }
      app.querySelectorAll("[data-pa]").forEach((b) => b.addEventListener("click", () => {
        const [i, pid] = b.dataset.pa.split("|"); const a = S.addons[+i]; const pp = S.profiles.find((x) => x.id === pid); if (!a || !pp) return;
        const off = new Set(pp.off || []); off.has(a.manifest.id) ? off.delete(a.manifest.id) : off.add(a.manifest.id);
        pp.off = [...off]; pp.t = Date.now(); if (S.profile.id === pp.id) S.profile.off = pp.off;
        saveProfiles(); cache.clear(); draw();
      }));
      app.querySelectorAll("[data-up]").forEach((b) => b.addEventListener("click", () => {
        const i = Number(b.dataset.up); const [x] = S.addons.splice(i, 1); S.addons.splice(i - 1, 0, x); saveAddons(); draw();
      }));
      app.querySelectorAll("[data-rm]").forEach((b) => b.addEventListener("click", () => {
        const a = S.addons[Number(b.dataset.rm)];
        if (!confirm(`Retirer « ${a.manifest.name} » ?`)) return;
        S.addons.splice(Number(b.dataset.rm), 1); saveAddons(); cache.clear(); draw();
      }));
      app.querySelectorAll("[data-rf]").forEach((b) => b.addEventListener("click", async () => {
        const i = Number(b.dataset.rf); toast("Actualisation…");
        try { S.addons[i] = await installAddon(S.addons[i].url); saveAddons(); cache.clear(); toast("Add-on à jour ✓"); draw(); }
        catch (e) { toast("Impossible d'actualiser (" + e.message + ")"); }
      }));
      app.querySelectorAll("[data-pl]").forEach((b) => b.addEventListener("click", () => { S.player = b.dataset.pl; LS.set("player", S.player); draw(); }));
      $("#upd").addEventListener("click", async () => {
        toast("Recherche…");
        if (await autoUpdate(true)) applyUpdate(); else toast("TVO est à jour");
      });
    };
    draw();
  }

  function screenAddonAdd() {
    setTabs(null);
    app.innerHTML = header("Ajouter un add-on", `<button class="icon-btn" onclick="history.back()" aria-label="Retour">${ICON.back}</button>`) + `
      <div class="list" style="margin-top:8px">
        <input class="field" id="url" type="url" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="Add-on (…/manifest.json) ou liste M3U (….m3u)">
        <button class="btn primary block" id="ok">${ICON.plus} Installer</button>
        <button class="btn block" id="paste">Coller depuis le presse-papiers</button>
        <div id="msg" class="small" style="color:var(--cyan)"></div>
      </div>
      <h2 class="section-title section"><span>Suggestions</span></h2>
      <p class="pad small muted" style="margin-top:-4px">Une sélection compatible avec TVO, classée selon ce que tu veux faire.</p>
      <div class="list">${SUG_GROUPS.map(([g, why]) => [g, why, SUGGESTIONS.filter((x) => x[3] === g)]).filter(([, , l]) => l.length).map(([g, why, l]) =>
        `<div class="sug-head"><b>${esc(g)}</b><span class="small muted">${esc(why)}</span></div>` + l.map(([u, n, d, , tag]) => {
          const inst = S.addons.some((a) => a.url === u || a.url === normalize(u));
          return `<div class="card sug"><div style="flex:1;min-width:0"><b>${esc(n)}</b>${tag ? ` <span class="tag">${esc(tag)}</span>` : ""}<div class="small muted">${esc(d)}</div></div>
            <button class="btn ${inst ? "" : "primary"}" data-s="${esc(u)}" ${inst ? "disabled" : ""}>${inst ? "✓ Installé" : "Installer"}</button></div>`;
        }).join("")).join("")}</div>`;
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
    const PAUSE = '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>';
    const R10 = (dir) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="${dir > 0 ? "transform:scaleX(-1)" : ""}"><path d="M4 12a8 8 0 1 0 3-6.2"/><path d="M4 3v4h4"/></svg>`;
    el.innerHTML = `
      <video id="vid" playsinline autoplay preload="auto"></video>
      <div class="ploading" id="pLoad"><span></span></div>
      <div class="nf" id="nf">
        <div class="nf-top"><button class="nf-ic" id="pClose" aria-label="Fermer">${ICON.back}</button>
          <div class="t"><b>${esc(m.name)}</b><span>${v && v.season != null ? `S${v.season} : É${v.episode} · ${esc(v.title)}` : esc(s.addonName)}</span></div>
          ${document.pictureInPictureEnabled || isIOS ? `<button class="nf-ic" id="pPip" aria-label="Image dans l'image"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><rect x="12" y="11" width="7" height="6" rx="1" fill="currentColor"/></svg></button>` : ""}</div>
        <div class="nf-mid">
          <button class="nf-big" id="pBack" aria-label="Reculer de 10 secondes">${R10(-1)}<i>10</i></button>
          <button class="nf-play" id="pPlay" aria-label="Lecture / pause">${PAUSE}</button>
          <button class="nf-big" id="pFwd" aria-label="Avancer de 10 secondes">${R10(1)}<i>10</i></button>
        </div>
        <div class="nf-bot">
          <div class="nf-seek"><input type="range" id="pSeek" min="0" max="1000" value="0" aria-label="Position"><span id="pTime">0:00</span></div>
          <div class="nf-row">
            <button class="nf-opt" id="pSpeed">${ICON.play}<span>Vitesse (x1)</span></button>
            <button class="nf-opt" id="pSubs"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 12h4M13 12h4M7 15h6"/></svg><span>Audio et sous-titres</span></button>
            <button class="nf-opt" id="pFit"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg><span>Ajuster</span></button>
            ${m.type === "series" ? `<button class="nf-opt" id="pNext"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M5 4v16l11-8zM17 4h2v16h-2z"/></svg><span>Épisode suivant</span></button>` : ""}
          </div>
        </div>
      </div>
      <div class="nf-sheet" id="pSheet" hidden></div>
      <div class="pflash" id="pFlash" hidden></div>`;
    const video = $("#vid");
    const fmt = (t) => { if (!isFinite(t)) return "En direct"; t = Math.max(0, Math.floor(t)); const h = Math.floor(t / 3600), mm = Math.floor((t % 3600) / 60), ss = t % 60; return (h ? h + ":" + String(mm).padStart(2, "0") : mm) + ":" + String(ss).padStart(2, "0"); };
    $("#pClose").addEventListener("click", closePlayer);
    const flash = (t) => { const f = $("#pFlash"); f.textContent = t; f.hidden = false; clearTimeout(flash._t); flash._t = setTimeout(() => (f.hidden = true), 800); };
    const seek = (d) => { if (!isFinite(video.duration)) return; video.currentTime = Math.max(0, Math.min(video.duration - 1, video.currentTime + d)); flash((d > 0 ? "+" : "−") + Math.abs(d) + " s"); };
    $("#pBack").addEventListener("click", (e) => { e.stopPropagation(); seek(-10); wake(); });
    $("#pFwd").addEventListener("click", (e) => { e.stopPropagation(); seek(10); wake(); });
    const playBtn = $("#pPlay");
    playBtn.addEventListener("click", (e) => { e.stopPropagation(); video.paused ? video.play() : video.pause(); });
    video.addEventListener("play", () => (playBtn.innerHTML = PAUSE));
    video.addEventListener("pause", () => (playBtn.innerHTML = ICON.play));
    const seekEl = $("#pSeek"), timeEl = $("#pTime"); let dragging = false;
    seekEl.addEventListener("input", () => { dragging = true; if (isFinite(video.duration)) timeEl.textContent = fmt(video.duration * seekEl.value / 1000) + " / " + fmt(video.duration); wake(); });
    seekEl.addEventListener("change", () => { if (isFinite(video.duration)) video.currentTime = video.duration * seekEl.value / 1000; dragging = false; });
    video.addEventListener("timeupdate", () => {
      if (dragging) return;
      if (isFinite(video.duration) && video.duration > 0) { seekEl.value = String(Math.round(video.currentTime / video.duration * 1000)); timeEl.textContent = fmt(video.currentTime) + " / " + fmt(video.duration); seekEl.style.setProperty("--p", (video.currentTime / video.duration * 100) + "%"); }
      else { timeEl.textContent = "● En direct"; seekEl.disabled = true; }
    });
    const speeds = [1, 1.25, 1.5, 2, 0.75];
    $("#pSpeed").addEventListener("click", (e) => { e.stopPropagation(); const i = (speeds.indexOf(video.playbackRate) + 1) % speeds.length; video.playbackRate = speeds[i]; e.currentTarget.querySelector("span").textContent = `Vitesse (x${speeds[i]})`; flash("Vitesse x" + speeds[i]); wake(); });
    $("#pFit").addEventListener("click", (e) => { e.stopPropagation(); const cover = video.style.objectFit !== "cover"; video.style.objectFit = cover ? "cover" : "contain"; e.currentTarget.querySelector("span").textContent = cover ? "Remplir" : "Ajuster"; wake(); });
    const nx = $("#pNext"); if (nx) nx.addEventListener("click", (e) => { e.stopPropagation(); playNext(ctx); });
    // Feuille audio + sous-titres
    $("#pSubs").addEventListener("click", (e) => {
      e.stopPropagation();
      const sh = $("#pSheet");
      const tt = [...video.textTracks];
      const at = video.audioTracks ? [...video.audioTracks] : [];
      const hlsAudio = hls ? hls.audioTracks : [];
      const audioItems = hlsAudio.length > 1 ? hlsAudio.map((t, i) => [`ha${i}`, t.name || LANG[(t.lang || "").toLowerCase()] || "Piste " + (i + 1), hls.audioTrack === i])
        : at.map((t, i) => [`a${i}`, t.label || LANG[(t.language || "").toLowerCase()] || "Piste " + (i + 1), t.enabled]);
      sh.innerHTML = `<div class="nf-sheet-in"><div class="col"><h3>Audio</h3>${audioItems.length ? audioItems.map(([k, l, on]) => `<button data-k="${k}" class="${on ? "on" : ""}">${on ? "✓ " : ""}${esc(l)}</button>`).join("") : '<p class="muted">Piste unique</p>'}</div>
        <div class="col"><h3>Sous-titres</h3><button data-k="off" class="${tt.every((t) => t.mode !== "showing") ? "on" : ""}">${tt.every((t) => t.mode !== "showing") ? "✓ " : ""}Désactivés</button>
        ${tt.map((t, i) => `<button data-k="t${i}" class="${t.mode === "showing" ? "on" : ""}">${t.mode === "showing" ? "✓ " : ""}${esc(t.label || "Sous-titres " + (i + 1))}</button>`).join("")}</div>
        <button class="btn blue block" id="shClose">Fermer</button></div>`;
      sh.hidden = false;
      sh.querySelectorAll("[data-k]").forEach((b) => b.addEventListener("click", () => {
        const k = b.dataset.k;
        if (k === "off") tt.forEach((t) => (t.mode = "disabled"));
        else if (k[0] === "t") tt.forEach((t, i) => (t.mode = "t" + i === k ? "showing" : "disabled"));
        else if (k.startsWith("ha")) hls.audioTrack = Number(k.slice(2));
        else if (k[0] === "a") at.forEach((t, i) => (t.enabled = "a" + i === k));
        $("#pSubs").click();
      }));
      $("#shClose").addEventListener("click", (ev) => { ev.stopPropagation(); sh.hidden = true; });
      sh.addEventListener("click", (ev) => ev.stopPropagation());
    });
    const pip = $("#pPip");
    if (pip) pip.addEventListener("click", async (e) => {
      e.stopPropagation();
      try { if (video.webkitSetPresentationMode) video.webkitSetPresentationMode(video.webkitPresentationMode === "picture-in-picture" ? "inline" : "picture-in-picture");
        else if (document.pictureInPictureElement) await document.exitPictureInPicture(); else await video.requestPictureInPicture(); } catch { toast("Image dans l'image indisponible"); }
    });
    // Chargement
    const load = $("#pLoad");
    ["waiting", "stalled", "loadstart"].forEach((ev) => video.addEventListener(ev, () => (load.hidden = false)));
    ["playing", "canplay", "pause"].forEach((ev) => video.addEventListener(ev, () => (load.hidden = true)));
    // Commandes qui apparaissent / disparaissent ; double appui à gauche / à droite = −10 s / +10 s
    let idle;
    const wake = () => { el.classList.remove("idle"); clearTimeout(idle); idle = setTimeout(() => !video.paused && $("#pSheet").hidden && el.classList.add("idle"), 3500); };
    let lastTap = 0, tapTimer;
    $("#nf").addEventListener("click", (e) => {
      if (e.target.closest("button, input")) return;
      const now = Date.now();
      if (now - lastTap < 300) { clearTimeout(tapTimer); seek(e.clientX < window.innerWidth / 2 ? -10 : 10); wake(); }
      else tapTimer = setTimeout(() => { if (el.classList.contains("idle")) wake(); else el.classList.add("idle"); }, 280);
      lastTap = now;
    });
    video.addEventListener("pause", wake); wake();

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
    if (isHls && !isIOS && window.Hls && window.Hls.isSupported()) {
      hls = new window.Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 60 });
      hls.loadSource(s.url); hls.attachMedia(video);
      hls.on(window.Hls.Events.ERROR, (_, d) => { if (d.fatal) { if (d.type === "networkError") hls.startLoad(); else if (d.type === "mediaError") hls.recoverMediaError(); } });
    } else {
      video.src = s.url;
    }
    video.addEventListener("loadedmetadata", () => { if (ctx.start > 0 && ctx.start < video.duration - 5) video.currentTime = ctx.start; }, { once: true });


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

  // ------------------------------------------------------------------ mises à jour (automatiques, sans réinstaller)
  let updateInfo = null;
  const cleanNotes = (b) => String(b || "").split("\n").map((l) => l.trim())
    .filter((l) => l && !/^- TVO-|^Co-Authored-By|^Claude-Session|^https?:\/\//i.test(l)).join("\n");
  /** Nouveautés de toutes les versions publiées depuis `fromCode` (exclu) jusqu'à `toCode` (inclus). */
  async function releaseNotes(fromCode, toCode) {
    try {
      const list = await getJson(`https://api.github.com/repos/${VERSION.repo}/releases?per_page=30`, 10000);
      return list.map((r) => ({ code: parseInt(String(r.tag_name || "").split(".").pop(), 10) || 0, name: String(r.tag_name || "").replace(/^v/, ""), notes: cleanNotes(r.body) }))
        .filter((r) => r.code > fromCode && r.code <= toCode && r.notes).sort((a, b) => b.code - a.code);
    } catch { return []; }
  }
  function showWhatsNew(list, name) {
    if (!list.length) return;
    const box = document.createElement("div"); box.className = "wn-sheet";
    box.innerHTML = `<div class="wn-card"><div class="wn-head"><img src="logo-mark.png" alt=""><div><b>Nouveautés de TVO ${esc(name)}</b><span class="small muted">Ce qui a changé depuis ta dernière ouverture</span></div></div>
      <div class="wn-list">${list.map((r) => `<div class="wn-item"><span class="tag">${esc(r.name)}</span><p>${esc(r.notes).replace(/\n/g, "<br>")}</p></div>`).join("")}</div>
      <button class="btn primary block" data-x>Super, on y va !</button></div>`;
    document.body.appendChild(box);
    box.addEventListener("click", (e) => { if (e.target === box || e.target.closest("[data-x]")) box.remove(); });
  }
  async function latestRelease() {
    const r = await getJson(`https://api.github.com/repos/${VERSION.repo}/releases/latest`, 10000);
    return { code: parseInt(String(r.tag_name || "").split(".").pop(), 10) || 0, tag: r.tag_name, name: String(r.tag_name || "").replace(/^v/, "") };
  }
  /** Télécharge le nouveau code de l'appli et le garde pour les prochains démarrages. */
  async function autoUpdate(force = false) {
    if (!VERSION.code && !force) return false;
    try {
      const rel = await latestRelease();
      if (!(rel.code > VERSION.code)) return false;
      const raw = `https://raw.githubusercontent.com/${VERSION.repo}/${rel.tag}/mobile/www/`;
      const [js, css] = await Promise.all(["app.js", "app.css"].map(async (f) => {
        const r = await fetch(raw + f, { cache: "no-store" });
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.text();
      }));
      if (js.length < 10000 || css.length < 1000) return false;
      localStorage.setItem("tvo.bundle", JSON.stringify({ code: rel.code, name: rel.name, js, css }));
      updateInfo = rel;
      return true;
    } catch { return false; }
  }
  function applyUpdate() {
    if (!$("#player").hidden) return; // ne jamais couper une vidéo
    document.body.insertAdjacentHTML("beforeend", `<div class="toast" style="bottom:50%">Mise à jour de TVO ${esc(updateInfo.name)}…</div>`);
    setTimeout(() => location.reload(), 600);
  }
  function showUpdateBanner() { /* mises à jour automatiques : plus de bannière */ }

  // ------------------------------------------------------------------ routeur
  // ------------------------------------------------------------------ animations
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const revealIO = "IntersectionObserver" in window ? new IntersectionObserver((es) => es.forEach((e) => {
    if (e.isIntersecting) { e.target.classList.add("in"); revealIO.unobserve(e.target); }
  }), { rootMargin: "0px 0px -6% 0px", threshold: 0.05 }) : null;
  function revealAll(root) {
    if (reduceMotion || !revealIO || !app.classList.contains("is-home")) return;
    root.querySelectorAll("#rows > .section").forEach((el) => {
      if (el.classList.contains("rv")) return; el.classList.add("rv"); revealIO.observe(el);
    });
  }

  let ticking = false;
  window.addEventListener("scroll", () => {
    if (ticking || reduceMotion || !app.classList.contains("is-home")) return; ticking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY;
      document.body.classList.toggle("scrolled", y > 12);

      ticking = false;
    });
  }, { passive: true });

  let introDone = false;
  function route() {
    if (!$("#player").hidden) closePlayer();
    const parts = location.hash.replace(/^#\/?/, "").split("/").map(decodeURIComponent);
    const isHome = (parts[0] || "home") === "home";
    app.classList.toggle("is-home", isHome);

    const [page, a, b, c] = parts;
    window.scrollTo(0, 0);
    if (page === "profile") return screenProfileEdit(a);
    if (page === "profiles" || !S.profile) return screenProfiles();
    switch (page) {
      case "home": case "": case undefined: return screenBrowse(null);
      case "movies": return screenBrowse("movie");
      case "live": return screenLive();
      case "discover": return screenDiscover();
      case "person": return screenPerson(a);
      case "provider": return screenProvider(a);
      case "collections": return screenCollections();
      case "collection": return screenCollection(a);
      case "catalog": return screenCatalog(a);
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

  // Pas de zoom (pincement ou double appui) sur iPhone
  ["gesturestart", "gesturechange", "gestureend"].forEach((ev) => document.addEventListener(ev, (e) => e.preventDefault(), { passive: false }));
  let lastTouchEnd = 0;
  document.addEventListener("touchend", (e) => { const n = Date.now(); if (n - lastTouchEnd < 300 && !e.target.closest("video, input, textarea")) e.preventDefault(); lastTouchEnd = n; }, { passive: false });

  // ------------------------------------------------------------------ démarrage
  const cur = LS.get("current", null);
  const curP = S.profiles.find((p) => p.id === cur);
  if (curP && S.profiles.length === 1) selectProfile(curP);
  window.addEventListener("hashchange", route);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) { const el = $("#player"); if (el._save && !el.hidden) el._save(); syncNow(); }
    else syncNow().then((ch) => { if (ch && $("#player").hidden) route(); });
  });
  if (!location.hash || location.hash === "#/home") location.hash = S.profile ? "#/home" : "#/profiles";
  route();
  const wasOn = proxyOk;
  checkProxy().then((ok) => { if (ok !== wasOn && $("#player").hidden) { cache.clear(); route(); } });
  syncNow().then((ch) => { if (ch && $("#player").hidden) route(); }).finally(() => refreshAddons());
  setInterval(() => document.hidden || syncNow(), 120000);
  // À l'ouverture : télécharge la nouvelle version et l'applique tout de suite (hors lecture vidéo).
  (async () => {
    const sp = $("#splash"), msg = $("#splashMsg"), t0 = Date.now();
    const sv = $("#splashVer"); if (sv) sv.textContent = "Version " + VERSION.name;
    const ok = await Promise.race([autoUpdate(), sleep(5000).then(() => false)]);
    await sleep(Math.max(0, 2400 - (Date.now() - t0)));
    if (ok && $("#player").hidden) { if (msg) msg.textContent = `Installation de TVO ${updateInfo.name}…`; setTimeout(() => location.reload(), 500); return; }
    if (msg) msg.textContent = "TVO est à jour ✓";
    if (sp) sp.classList.add("done");
    // Nouveautés : affichées une fois, à la première ouverture après une mise à jour (code ou appli réinstallée)
    const seen = LS.get("seen_code", null);
    if (VERSION.code) { if (seen !== null && VERSION.code > seen) releaseNotes(seen, VERSION.code).then((l) => showWhatsNew(l, VERSION.name)); LS.set("seen_code", VERSION.code); }
    setTimeout(() => {
      if (sp) { sp.classList.add("hide"); setTimeout(() => sp.remove(), 500); }
    }, 450);
  })();
  document.addEventListener("visibilitychange", () => { if (document.hidden && syncTimer) { clearTimeout(syncTimer); syncTimer = null; syncNow(); } });
  document.addEventListener("visibilitychange", () => { if (!document.hidden) autoUpdate().then((ok) => ok && applyUpdate()); });
})();
