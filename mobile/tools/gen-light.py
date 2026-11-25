#!/usr/bin/env python3
"""Génère le bloc « thème clair » de mobile/www/app.css.

Le thème sombre est écrit à la main dans app.css. Ce script lit chaque règle de couleur
et écrit l'équivalent clair sous `html[data-mode="light"]`, entre deux marqueurs.
À relancer après toute modification des couleurs du thème sombre :
    python3 mobile/tools/gen-light.py
"""
import re, colorsys, pathlib, sys

CSS = pathlib.Path(__file__).resolve().parent.parent / "www" / "app.css"
BEGIN = "/* >>> THEME-CLAIR (généré par mobile/tools/gen-light.py — ne pas modifier à la main) */"
END = "/* <<< THEME-CLAIR */"

src = CSS.read_text()
TAIL = ""   # réglages faits à la main après le bloc généré : conservés tels quels
if BEGIN in src:
    if END in src: TAIL = src[src.index(END) + len(END):]
    src = src[: src.index(BEGIN)].rstrip() + "\n"
raw = re.sub(r"/\*.*?\*/", "", src, flags=re.S)

# ---------------------------------------------------------------- analyse du CSS
rules = []  # (media, selector, [(prop, value, important)])
def parse(s, ctx=""):
    i, n = 0, len(s)
    while i < n:
        j = s.find("{", i)
        if j < 0: break
        sel = s[i:j].strip(); d = 1; k = j + 1
        while k < n and d:
            d += (s[k] == "{") - (s[k] == "}"); k += 1
        body = s[j + 1 : k - 1]
        if sel.startswith("@media") or sel.startswith("@supports"):
            parse(body, (ctx + " " + sel).strip())
        elif sel.startswith("@"):
            pass
        else:
            decls = []
            for x in re.split(r";(?![^()]*\))", body):
                if ":" not in x: continue
                p, v = x.split(":", 1)
                p, v = p.strip(), v.strip()
                imp = v.endswith("!important")
                if imp: v = v[:-10].strip()
                decls.append((p, v, imp))
            rules.append((ctx, sel, decls))
        i = k
parse(raw)

# ---------------------------------------------------------------- couleurs
def parse_color(t):
    t = t.strip()
    m = re.fullmatch(r"#([0-9a-fA-F]{3,8})", t)
    if m:
        h = m.group(1)
        if len(h) in (3, 4): h = "".join(c * 2 for c in h)
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return r, g, b, a
    m = re.fullmatch(r"rgba?\(\s*([\d.]+)[ ,]+([\d.]+)[ ,]+([\d.]+)(?:[ ,/]+([\d.]+%?))?\s*\)", t)
    if m:
        a = m.group(4)
        a = 1.0 if a is None else (float(a[:-1]) / 100 if a.endswith("%") else float(a))
        return float(m.group(1)), float(m.group(2)), float(m.group(3)), a
    if t == "white": return 255, 255, 255, 1.0
    if t == "black": return 0, 0, 0, 1.0
    return None

def lum(r, g, b): return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
def hls(r, g, b): return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
def fmt(a): return ("%.3f" % a).rstrip("0").rstrip(".") or "0"
def rgba(r, g, b, a): return "rgba(%d,%d,%d,%s)" % (round(r), round(g), round(b), fmt(a))
def hexc(r, g, b): return "#%02x%02x%02x" % (round(r), round(g), round(b))

INK = (15, 23, 42)   # encre (bleu nuit) utilisée à la place du blanc translucide
COLOR_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|\bwhite\b|\bblack\b")

def sat_dark(r, g, b):
    """Couleur vive claire (vert, jaune, orange…) → même teinte, plus foncée, lisible sur fond clair."""
    h, l, s = hls(r, g, b)
    l = min(l, 0.30)
    rr, gg, bb = colorsys.hls_to_rgb(h, l, min(1, s + 0.05))
    return hexc(rr * 255, gg * 255, bb * 255)

def conv_text(tok):
    c = parse_color(tok)
    if not c: return tok
    r, g, b, a = c
    h, l, s = hls(r, g, b)
    L = lum(r, g, b)
    if a < 1 and L > 0.9 and s < 0.2:               # blanc translucide → encre translucide
        return rgba(*INK, min(1, a * 0.95))
    if s > 0.3 and 0.5 <= h <= 0.82 and L >= 0.8:     # lavande / bleu pâle (texte secondaire sombre) → gris encre
        return "var(--text-2)"
    if s > 0.3 and 0.5 <= h <= 0.82 and L >= 0.55:    # bleu clair (liens) → bleu lisible sur fond clair
        return "#2457c5"
    if s > 0.3 and L > 0.45 and L < 0.99:            # vif et clair
        return sat_dark(r, g, b)
    if s <= 0.3 and L >= 0.9: return "var(--text)"
    if s <= 0.3 and L >= 0.7: return "var(--text-2)"
    if s <= 0.3 and L >= 0.45: return "var(--muted)"
    if s > 0.3 and L >= 0.9: return "var(--text)"
    if L >= 0.6: return "var(--muted)"
    return tok

def conv_fill(tok):
    """Fond / dégradé : blanc translucide → encre translucide ; sombre → clair."""
    c = parse_color(tok)
    if not c: return tok
    r, g, b, a = c
    L = lum(r, g, b); h, l, s = hls(r, g, b)
    if L > 0.92 and s < 0.15:
        return rgba(*INK, a * (0.4 if a <= 0.2 else 0.6)) if a < 0.9 else tok
    if L < 0.3 and a >= 0.5:                          # fond sombre → fond clair de même opacité
        return rgba(255, 255, 255, a) if a < 1 else "var(--panel)"
    if L < 0.3 and a < 0.5 and not (r == g == b == 0):
        return rgba(*INK, a * 0.7)
    if 0.3 <= L < 0.55 and s < 0.3 and a >= 0.9:      # gris moyens (#5a567d…) → gris clair
        return "#c4c9d2"
    if 0.3 <= L < 0.75 and s < 0.35 and a < 0.9:      # gris translucides (boutons) → encre légère
        return rgba(*INK, a * 0.3)
    return tok

def conv_border(tok):
    c = parse_color(tok)
    if not c: return tok
    r, g, b, a = c
    L = lum(r, g, b); h, l, s = hls(r, g, b)
    if L > 0.85 and a < 1: return rgba(*INK, min(1, a * 1.0))
    if s > 0.2 and b > r and a < 0.6 and L > 0.5: return rgba(70, 90, 140, min(1, a * 1.5))   # bords bleutés
    if L < 0.3 and a >= 0.9: return "var(--line)"
    return tok

def conv_shadow(tok):
    c = parse_color(tok)
    if not c: return tok
    r, g, b, a = c
    L = lum(r, g, b)
    if r == g == b == 0 or (L < 0.15 and a >= 0.2): return rgba(*INK, min(0.28, a * 0.38))
    return tok

def sub(value, fn): return COLOR_RE.sub(lambda m: fn(m.group(0)), value)

# ---------------------------------------------------------------- zones qui restent sombres
# (photos, lecteur vidéo, bannières, boutons bleus : le texte y reste blanc)
SKIP = re.compile(r"""
 \.hero|\.dhero|\.dt-hero|\.detail-top|\.pv-hero|\.pv-brand|\.pv-logo|\.nf-|\.pl-|\#player|\.player|\.ploading|\.pflash|\.pbtn|\.playic|
 \.cslide|\.ccard|\.colcard|\.lscard|\.ktile|\.gtile|\.cat\b|\.cat[.:\s,]|\.genre|\.splash|\.tr-|\.shade|\.cshade|\.cmeta|\.cinfo|
 \.badge|\.rate\b|\.rate[.:\s,]|\.llive|\.playdot|\.pplay|\.cplay|\.cbar|\.thumb|\.ep\s\.th|\.ptile|\.prov|\.sgb|\.dx\b|\.disc|\.ybadge|\.age\b|
 \.dt-hero|\.cap\b|\.land\.channel|\.pimg\.channel|\.tvl-qr|\.sw\ i|\.swatch|\.avatar|\.ph\b|\.theme-pick\s\.t-|\.land\b|\.pimg|\.nf\b|\.pin-sheet|\.wn-sheet|\.as-sheet|
 \.btn\.primary|\.btn\.blue|\.btn\.accent|\.btn\.danger|\.btn\.pill|\.btn\.signin|\.btn\.sm\.primary|\.signin|\.upd-banner|\.ub-ic|\.sect-ic|\.next\b|
 \.pmeter|\.llogo|\.wn-card\s\.btn\.primary|\.pill\b|::cue|\.poster|\.pcard\s\.img|\.ncard|\.nchip\.on|\.iconpick\.on|\.snum\sbutton\.on|\.chip\.on|
 \.seg\sbutton\.on|\.tab\.active\s\.ico|\.dhero|\.mv-list\sbutton\.on|\.sw\b|\.bubble|\.colcard|\.hmeta|\.hero3|\.hero2|
 \.avatar|\.profile-av|\.pav|\.pm\s\.av|\.bar\b|\.lbar|\.ep\s\.th
""", re.X)

# couples « fond blanc / texte sombre » : boutons inversés → en clair fond encre, texte blanc
INVERT_WHITE_BG = re.compile(r"\.dt-play|\.dt-tabs\sbutton\.on|\.pv-seg\sbutton\.on|\.toast$|\.btn\.primary$|\.dhero-btn|\.lscard\s\.btn\.primary|\.btn\.pill|\.car-act\s\.btn\.primary")

def is_white_solid(v):
    return re.fullmatch(r"(#fff|#ffffff|white)", v.strip()) is not None

PROPS_TEXT = {"color", "caret-color", "-webkit-text-fill-color", "text-decoration-color"}
PROPS_FILL = {"background", "background-color", "background-image"}
PROPS_BORDER = {"border", "border-top", "border-bottom", "border-left", "border-right", "border-color", "border-top-color",
                "border-bottom-color", "border-left-color", "border-right-color", "outline", "outline-color"}

def convert(sel_part, prop, val):
    if prop in PROPS_TEXT:
        c = parse_color(val)
        if c and INVERT_WHITE_BG.search(sel_part) and lum(*c[:3]) < 0.4: return "#fff"   # texte sombre d'un bouton blanc → blanc
        return conv_text(val)
    if prop in ("fill", "stroke"):
        return conv_text(val) if prop == "fill" else val
    if prop in PROPS_FILL:
        if is_white_solid(val): return "var(--text)" if INVERT_WHITE_BG.search(sel_part) else val
        return sub(val, conv_fill)
    if prop in PROPS_BORDER: return sub(val, conv_border)
    if prop in ("box-shadow", "text-shadow"): return sub(val, conv_shadow)
    return val

# ---------------------------------------------------------------- génération
PREFIX = 'html[data-mode="light"]:root'
STRIP = re.compile(r"^(html\.glass|html\.classic|html|:root)\b\s*")
def prefixed(sel):
    parts, depth, cur = [], 0, ""
    for ch in sel:
        if ch in "([": depth += 1
        if ch in ")]": depth -= 1
        if ch == "," and depth == 0: parts.append(cur); cur = ""
        else: cur += ch
    parts.append(cur)
    out = []
    for p in parts:
        p = p.strip()
        if not p: continue
        q = STRIP.sub("", p)
        out.append((p, (PREFIX + " " + q).strip() if q else PREFIX))
    return out

blocks, seen = [], set()
for media, sel, decls in rules:
    if sel in ("*", ":root") and not decls: continue
    keep = []
    for orig, new in prefixed(sel):
        if SKIP.search(orig): continue
        body = []
        for prop, val, imp in decls:
            if prop.startswith("--"): continue
            if not (COLOR_RE.search(val) or prop in PROPS_TEXT): continue
            nv = convert(orig, prop, val)
            if nv != val:
                body.append("%s:%s%s" % (prop, nv, " !important" if imp else ""))
        if body:
            keep.append((new, body))
    # regrouper par corps identique
    for new, body in keep:
        key = (media, new, tuple(body))
        if key in seen: continue
        seen.add(key)
        blocks.append((media, new, body))

# regrouper par media consécutif
out = [BEGIN]
out.append("""
html[data-mode="light"] { color-scheme: light; }
html[data-mode="light"]:root {
  --bg: #f2f3f6; --panel: #ffffff; --surface: #ffffff; --surface-2: #eceef2; --line: #d8dce3;
  --text: #12151a; --text-2: #3b434d; --muted: #5d6773;
  --blue-2: #2563eb; --cyan: #2563c9;
  --success: #15803d; --danger: #dc2626;
  --g-fill: linear-gradient(160deg, rgba(255,255,255,.98) 0%, rgba(255,255,255,.92) 100%);
  --g-fill-strong: linear-gradient(160deg, #fff 0%, rgba(255,255,255,.96) 100%);
  --g-edge: rgba(15,23,42,.10); --g-edge-top: rgba(15,23,42,.14);
  --g-shine: inset 0 1px 0 rgba(255,255,255,.9), inset 0 -1px 0 rgba(15,23,42,.04);
  --g-shadow: 0 6px 20px rgba(15,23,42,.08);
}
html[data-mode="light"] body, html[data-mode="light"].glass body::before { background: var(--bg); color: var(--text); }
""")
cur_media = None
for media, new, body in blocks:
    if media != cur_media:
        if cur_media: out.append("}")
        if media: out.append(media + " {")
        cur_media = media
    out.append("%s{%s}" % (new, ";".join(body)))
if cur_media: out.append("}")
out.append("")
out.append(END)
CSS.write_text(src.rstrip() + "\n\n" + "\n".join(out) + TAIL.rstrip("\n") + "\n")
print("règles générées :", len(blocks))
