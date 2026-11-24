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

  function selectProfile(p) {
    S.profile = p; LS.set("current", p.id);
    S.lib = LS.get("lib." + p.id, []);
    S.prog = LS.get("prog." + p.id, []);
  }
  const saveProfiles = () => { LS.set("profiles", S.profiles); changed(); };
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
  function catalogAddons() {
    if (!tmdbOn()) return S.addons;
    return [TMDB.addon, ...S.addons.map((a) => (a.manifest.id === "com.linvo.cinemeta" ? { ...a, manifest: { ...a.manifest, catalogs: [] } } : a))];
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
  const today = () => new Date().toISOString().slice(0, 10);
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
        const name = raw.replace(/^\d+\s*[-.:]\s*/, "");
        const id = `${M3U.P}${h}:${slugify(name)}`;
        if (!seen.has(id)) { seen.add(id); out.push({ id, name, logo: attr("tvg-logo") || guessLogo(name), url: l, group: attr("group-title") || "", chno: num, headers: { ...hdr } }); }
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
  const m3uUsesGroups = (list) => new Set(list.map((c) => c.group).filter(Boolean)).size > 1;
  const m3uCat = (c, g) => (g ? c.group || "Autres" : chanCategory(c.name));
  async function installM3u(input, name) {
    const url = String(input).trim().replace(/^m3u:/, ""); const addonUrl = M3U.P + url;
    M3U.lists.delete(addonUrl);
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
  const m3uPreview = (c) => ({ id: c.id, type: "tv", name: c.name, poster: c.logo, background: null, logo: c.logo,
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
    for (const a of S.addons.filter((x) => supports(x, "meta", type, id))) {
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
    const list = S.addons.filter((a) => supports(a, "stream", type, videoId));
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
    const list = S.addons.filter((a) => supports(a, "subtitles", type, videoId));
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
    if (!r.ok) throw new Error("HTTP " + r.status);
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
  async function approveTv(code) {
    code = String(code || "").replace(/\D/g, "");
    if (code.length !== 6) { toast("Code à 6 chiffres attendu."); return false; }
    await syncNow();
    const ok = await rpc("tvo_login_approve", { p_code: code, p_id: syncId() }).catch(() => false);
    toast(ok ? "TV connectée ✓" : "Code invalide ou expiré."); return !!ok;
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
      if (confirm("Connecter ce téléviseur à ton compte TVO ?")) approveTv(q.get("code"));
    }
  }
  try {
    const App = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.App;
    if (App) { App.addListener("appUrlOpen", (e) => handleUrl(e.url)); App.getLaunchUrl().then((r) => r && handleUrl(r.url)).catch(() => {}); }
  } catch {}

  function exportDoc() {
    const profiles = {};
    S.profiles.forEach((p) => (profiles[p.id] = { id: p.id, name: p.name, color: p.color, t: p.t || 0 }));
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
      .map((p) => ({ id: p.id, name: p.name, color: p.color, t: p.t || 0 }));
    LS.set("profiles", S.profiles);
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
  const avatar = (p, cls = "") => `<div class="avatar ${cls}" style="background:linear-gradient(135deg, ${p.color}, ${p.color}99)">${esc((p.name || "?").slice(0, 1).toUpperCase())}</div>`;
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
      if (existing) Object.assign(existing, { name: v, color, t: Date.now() });
      else S.profiles.push({ id: "p" + Date.now().toString(36), name: v, color, t: Date.now() });
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
      sec.innerHTML = `<h2 class="section-title"><span class="st plain">Continuer à regarder</span><a href="#/list">Voir tout</a></h2><div class="row">${cont.map((x) => `
        <button class="wide" data-resume="${esc(x.type)}|${esc(x.metaId)}|${esc(x.videoId)}">
          <div class="thumb">${x.background || x.poster ? `<img src="${esc(x.background || x.poster)}" alt="" loading="lazy">` : ""}<span class="playdot">${ICON.play}</span><div class="bar"><i style="width:${Math.round(fraction(x) * 100)}%"></i></div></div>
          <div class="title">${esc(x.name)}</div>
          <div class="sub">${x.season != null ? `S${x.season} : É${x.episode} · ${esc(x.episodeTitle || "")}` : `Il reste ${Math.max(1, Math.round((x.duration - x.position) / 60000))} min`}</div>
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
      const cats = [["Action", ICON.bolt, "#ef4444"], ["Comedy", ICON.smile, "#f59e0b"], ["Sci-Fi", ICON.ufo, "#8b5cf6"], ["Thriller", ICON.knife, "#14b8a6"],
        ["Romance", ICON.heart, "#ec4899"], ["Horror", ICON.ghost, "#64748b"], ["Animation", ICON.smile, "#22c55e"], ["Documentary", ICON.doc, "#0ea5e9"]];
      const sec = document.createElement("section"); sec.className = "section";
      sec.innerHTML = `<h2 class="section-title"><span class="st">${ICON.grid}Catégories</span></h2><div class="cats">${cats.map(([g, ic, c]) =>
        `<button class="cat" style="--c:${c}" data-g="${g}"><span class="ci">${ic}</span><span>${genreLabel(g)}</span></button>`).join("")}</div>`;
      sec.querySelectorAll("[data-g]").forEach((b) => b.addEventListener("click", () => go(`#/genre/movie/${enc(b.dataset.g)}`)));
      rowsEl.appendChild(sec);
      const ps = document.createElement("section"); ps.className = "section";
      ps.innerHTML = `<h2 class="section-title"><span class="st">${ICON.series}Plateformes</span></h2><div class="cats">${PROVIDERS.map(([id, n, c]) =>
        provTile(id, n, c)).join("")}</div>`;
      rowsEl.appendChild(ps);
    }
    const rows = type ? buildRows(type) : applyHome(buildRows(null));
    if (!rows.length) rowsEl.insertAdjacentHTML("beforeend", `<div class="empty"><h2>Aucun catalogue</h2>Ajoute un add-on de catalogue dans Réglages.</div>`);
    let heroDone = false;
    lazyRows(rowsEl, rows, (list) => {
      if (heroDone) return; heroDone = true;
      if (list.length) (type ? renderHero : renderCarousel)($("#hero"), list);
    });
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
    return `<a class="prov ${logo ? "has-logo" : ""}" style="--c:${c}" href="#/provider/${id}" aria-label="${esc(n)}">${logo ? `<img src="${esc(logo)}" alt="" onerror="this.remove()">` : ""}<span>${esc(n)}</span></a>`;
  }
  if (!PROV_LOGOS) provLogos().then(() => { if (/^#\/(|home|discover)?$/.test(location.hash)) document.querySelectorAll(".prov").forEach((a) => { const id = a.getAttribute("href").split("/").pop(); const p = PROVIDERS.find((x) => x[0] === id); if (p) a.outerHTML = provTile(...p); }); });

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

  /** Carrousel « Tendances pour toi » : grande affiche au centre. */
  function renderCarousel(el, list) {
    const items = list.filter((m) => m.poster).slice(0, 10);
    el.innerHTML = `<div class="carousel" id="car">${items.map((m) => `<button class="cslide" data-open="${esc(m.type)}|${esc(m.id)}" aria-label="${esc(m.name)}">
      <img src="${esc(m.poster)}" alt="" loading="lazy"><span class="cmeta"><b>${esc(m.name)}</b><span>${[m.releaseInfo, m.imdbRating ? "★ " + m.imdbRating : ""].filter(Boolean).map(esc).join(" · ")}</span></span></button>`).join("")}</div>`;
    const car = $("#car");
    const io = new IntersectionObserver((es) => es.forEach((e) => e.target.classList.toggle("center", e.intersectionRatio > 0.75)), { root: car, threshold: [0, 0.75, 1] });
    car.querySelectorAll(".cslide").forEach((x) => io.observe(x));
    requestAnimationFrame(() => { const second = car.children[1]; if (second) car.scrollLeft = second.offsetLeft - (car.clientWidth - second.clientWidth) / 2; });
  }

  /** Découvrir : recherche + Films / Séries + catégories + plateformes. */
  function screenDiscover() {
    setTabs("discover");
    app.innerHTML = topBar("Découvrir") + `<div class="pad"><button class="search-pill big" data-go="#/search">${ICON.search}<span>Film, série, acteur, chaîne…</span></button></div>
      <div class="disc-grid pad">
        <a class="disc" href="#/movies">${ICON.clap}<b>Films</b></a><a class="disc" href="#/series">${ICON.series}<b>Séries</b></a>
        <a class="disc" href="#/live">${ICON.live}<b>Live TV</b></a><a class="disc" href="#/catalog/${enc(TMDB.URL + "|movie|kids")}">${ICON.smile}<b>Enfants</b></a></div>
      <div id="rows"></div>`;
    const rowsEl = $("#rows");
    const cats = [["Action", ICON.bolt, "#ef4444"], ["Comedy", ICON.smile, "#f59e0b"], ["Sci-Fi", ICON.ufo, "#8b5cf6"], ["Thriller", ICON.knife, "#14b8a6"],
      ["Romance", ICON.heart, "#ec4899"], ["Horror", ICON.ghost, "#64748b"], ["Animation", ICON.smile, "#22c55e"], ["Documentary", ICON.doc, "#0ea5e9"]];
    rowsEl.innerHTML = `<section class="section"><h2 class="section-title"><span class="st plain">Catégories</span></h2><div class="cats">${cats.map(([g, ic, c]) =>
      `<a class="cat" style="--c:${c}" href="#/genre/movie/${enc(g)}"><span class="ci">${ic}</span><span>${genreLabel(g)}</span></a>`).join("")}</div></section>
      <section class="section"><h2 class="section-title"><span class="st plain">Plateformes</span></h2><div class="cats">${PROVIDERS.map(([id, n, c]) =>
      provTile(id, n, c)).join("")}</div></section>`;
  }

  // Live TV : toutes les catégories de chaînes
  function screenLive() {
    setTabs("live");
    const p = S.profile;
    app.innerHTML = topBar("Live TV") + `<div id="rows"></div>`;
    const rows = buildRows("tv").concat(buildRows("channel"));
    if (!rows.length) {
      $("#rows").innerHTML = `<div class="empty"><h2>Freebox TV</h2>Abonné Free ? Connecte ton téléphone au Wi-Fi de ta Freebox puis ajoute tes chaînes. Tu peux aussi ajouter ta propre liste M3U dans Réglages › Add-ons.</div>
        <div class="pad"><button class="btn blue block" id="addLive">${ICON.plus} Ajouter les chaînes Freebox</button></div>`;
      $("#addLive").addEventListener("click", async () => {
        toast("Recherche de la Freebox…");
        try { const a = await installM3u("http://mafreebox.freebox.fr/freeboxtv/playlist.m3u"); S.addons.push(a); saveAddons(); cache.clear(); screenLive(); }
        catch (e) { toast(e.message, 4000); }
      });
      return;
    }
    lazyRows($("#rows"), rows);
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
          <button id="share"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 3v12M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/></svg><span>Partager</span></button>
        </div>
      </div>
      <div class="dt-tabs" id="dtTabs"></div>
      <div id="dtPane"></div>`;

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

  /** Réglages en sections repliables (ouvertes/fermées mémorisées). */
  const SECT_INFO = { "Compte": "Google, Apple et synchronisation", "Connecter une TV": "QR code ou code à 6 chiffres", "Lecteur": "Lecteur intégré ou appli externe",
    "Titres et résumés (TMDB)": "Catalogues et fiches en français", "Add-ons": "Catalogues, sources, chaînes, sous-titres", "Page d'accueil": "Rangées affichées et ordre", "Application": "Version et mises à jour" };
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
      <h2 class="section-title section"><span>Compte</span></h2>
      <div class="list">
        ${account() ? `<div class="card" style="display:flex;align-items:center;gap:12px"><div style="flex:1;min-width:0"><b>Connecté</b><div class="small muted" style="overflow:hidden;text-overflow:ellipsis">${esc(account().email || "Compte TVO")}</div></div><button class="btn" id="signOut">Se déconnecter</button></div>`
          : `<div class="card small muted">Connecte-toi pour retrouver Ma liste, Continuer à regarder, tes profils et tes add-ons sur tous tes appareils.</div>
        <button class="btn block signin" id="gIn"><b style="font-size:17px">G</b> Continuer avec Google</button>
        <button class="btn block signin apple" id="aIn"><b style="font-size:17px"></b> Continuer avec Apple</button>`}
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
          <div class="actions">${i > 0 ? `<button class="btn" data-up="${i}">${ICON.up} Monter</button>` : ""}${a.manifest.configurable && /^https?:/.test(a.url) ? `<a class="btn" href="${esc(a.url)}/configure" target="_blank" rel="noopener">Configurer</a>` : ""}${/^https?:/.test(a.url) ? `<button class="btn" data-rf="${i}">Actualiser</button>` : ""}<button class="btn danger" data-rm="${i}">${ICON.trash} Retirer</button></div></div>`;
      }).join("")).join("")}
        <button class="btn accent block" id="addA">${ICON.plus} Ajouter un add-on</button></div>
      <h2 class="section-title section"><span>Page d'accueil</span></h2>
      <p class="pad small muted" style="margin-top:-4px">Choisis les rangées affichées et leur ordre. Chaque catalogue d'add-on peut devenir une rangée.</p>
      <div class="list" id="homeEd"></div>
      <h2 class="section-title section"><span>Application</span></h2>
      <div class="list"><div class="card">Version ${esc(VERSION.name)}<div class="small muted">TVO n'héberge aucun contenu : les vidéos viennent des add-ons que tu ajoutes.</div></div>
        <button class="btn block" id="upd">Rechercher une mise à jour</button></div>`;
      makeCollapsible(app, account() ? [] : ["Compte"]);
      $("#editP").addEventListener("click", () => go("#/profile/" + enc(p.id)));
      const drawHome = () => {
        const h = homeLayout();
        const special = [["continue", "Continuer à regarder"], ["library", "Ma liste"], ["genres", "Explorer par catégorie"]];
        const idx = (k) => { const i = h.order.indexOf(k); return i < 0 ? 1e9 : i; };
        const rows = buildRows(null).sort((a, b) => idx(a.key) - idx(b.key)).map((r) => [r.key, r.title]);
        $("#homeEd").innerHTML = [...special, ...rows].map(([k, t], n) => `<div class="card" style="display:flex;align-items:center;gap:8px;padding:10px 12px">
          <span style="flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;${h.hidden.includes(k) ? "color:var(--muted)" : ""}">${esc(t)}</span>
          <button class="chip ${h.hidden.includes(k) ? "" : "on"}" data-hv="${esc(k)}">${h.hidden.includes(k) ? "Masquée" : "Affichée"}</button>
          ${n >= special.length ? `<button class="icon-btn" style="width:36px;height:36px;background:var(--surface-2)" data-mv="${esc(k)}|-1" aria-label="Monter">${ICON.up}</button>
          <button class="icon-btn" style="width:36px;height:36px;background:var(--surface-2);transform:rotate(180deg)" data-mv="${esc(k)}|1" aria-label="Descendre">${ICON.up}</button>` : ""}</div>`).join("");
        $("#homeEd").querySelectorAll("[data-hv]").forEach((b) => b.addEventListener("click", () => {
          const k = b.dataset.hv; const hh = homeLayout();
          setHome({ ...hh, hidden: hh.hidden.includes(k) ? hh.hidden.filter((x) => x !== k) : [...hh.hidden, k] }); drawHome();
        }));
        $("#homeEd").querySelectorAll("[data-mv]").forEach((b) => b.addEventListener("click", () => {
          const [k, d] = b.dataset.mv.split("|"); const keys = rows.map((r) => r[0]);
          const i = keys.indexOf(k), j = i + Number(d); if (j < 0 || j >= keys.length) return;
          keys.splice(j, 0, keys.splice(i, 1)[0]); setHome({ ...homeLayout(), order: keys }); drawHome();
        }));
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
      $("#addA").addEventListener("click", () => go("#/addon-add"));
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
    if (reduceMotion || !revealIO) return;
    root.querySelectorAll(".section, .card, details.sect, .ep, .person-head, .grid > *:not(.rv)").forEach((el) => {
      if (el.classList.contains("rv")) return; el.classList.add("rv"); revealIO.observe(el);
    });
  }
  new MutationObserver(() => revealAll(app)).observe(app, { childList: true, subtree: true });
  let ticking = false;
  window.addEventListener("scroll", () => {
    if (ticking || reduceMotion) return; ticking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY;
      document.body.classList.toggle("scrolled", y > 12);
      const h = app.querySelector(".dt-hero img, .pv-hero > img, .hero img");
      if (h) h.style.transform = `translate3d(0, ${Math.min(y * 0.35, 220)}px, 0) scale(${1 + Math.min(y, 300) / 3000})`;
      ticking = false;
    });
  }, { passive: true });

  function route() {
    if (!reduceMotion) { app.classList.remove("page-in"); void app.offsetWidth; app.classList.add("page-in"); }
    if (!$("#player").hidden) closePlayer();
    const parts = location.hash.replace(/^#\/?/, "").split("/").map(decodeURIComponent);
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
    const ok = await Promise.race([autoUpdate(), sleep(4000).then(() => false)]);
    await sleep(Math.max(0, 1600 - (Date.now() - t0)));
    if (ok && $("#player").hidden) { if (msg) msg.textContent = `Installation de TVO ${updateInfo.name}…`; setTimeout(() => location.reload(), 500); return; }
    if (msg) msg.textContent = "TVO est à jour ✓";
    if (sp) sp.classList.add("done");
    setTimeout(() => { if (sp) { sp.classList.add("hide"); setTimeout(() => sp.remove(), 500); } }, 450);
  })();
  document.addEventListener("visibilitychange", () => { if (!document.hidden) autoUpdate().then((ok) => ok && applyUpdate()); });
})();
