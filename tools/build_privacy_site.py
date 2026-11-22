#!/usr/bin/env python3
"""Generates privacy-site/ from app/src/main/assets/privacy (the single source of truth).

Usage: python3 tools/build_privacy_site.py [--check]
--check fails if privacy-site/ is out of date.
"""
import html, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/assets/privacy"
OUT = ROOT / "privacy-site"
LANGS = [("en", "English"), ("ru", "Русский"), ("uk", "Українська"), ("it", "Italiano"),
         ("de", "Deutsch"), ("fr", "Français"), ("es", "Español"), ("zh-CN", "简体中文")]
URL = re.compile(r"https://[^\s)]+")

def config():
    c = {}
    for line in (SRC / "config.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1); c[k.strip()] = v.strip()
    return c

def sub(t, c):
    for key, ph in (("version", "[VERSION]"), ("effectiveDate", "[EFFECTIVE_DATE]"), ("contactEmail", "[CONTACT_EMAIL]")):
        if c.get(key): t = t.replace(ph, c[key])
    return t

def inline(t):
    esc = html.escape(t, quote=True)
    return URL.sub(lambda m: f'<a href="{m.group(0)}" rel="noopener">{m.group(0)}</a>', esc)

def body(md, c):
    out, para, meta = [], [], 0
    def flush():
        if para: out.append("<p>" + "<br>".join(inline(l) for l in para) + "</p>"); para.clear()
    bullets = []
    def fb():
        if bullets: out.append("<ul>" + "".join(f"<li>{inline(b)}</li>" for b in bullets) + "</ul>"); bullets.clear()
    for raw in md.splitlines():
        l = sub(raw.rstrip(), c)
        if not l.strip(): flush(); fb()
        elif l.startswith("## "): flush(); fb(); out.append(f"<h2>{inline(l[3:])}</h2>")
        elif l.startswith("# "): flush(); fb(); out.append(f"<h1>{inline(l[2:])}</h1>"); meta = 2
        elif l.startswith("- "): flush(); bullets.append(l[2:])
        elif meta > 0: flush(); out.append(f'<p class="meta">{inline(l)}</p>'); meta -= 1
        else: fb(); para.append(l)
    flush(); fb()
    return "\n".join(out)

def switcher(cur, prefix):
    items = []
    for tag, name in LANGS:
        cls = ' aria-current="true"' if tag == cur else ""
        items.append(f'<a href="{prefix}{tag}/index.html" hreflang="{tag}"{cls}>{name}</a>')
    return '<nav class="lang" aria-label="Language">' + " ".join(items) + "</nav>"

CSS = """:root{color-scheme:light dark;--bg:#fff;--fg:#14213d;--muted:#5a6578;--accent:#8a6a00;--card:#f4f6fb}
@media(prefers-color-scheme:dark){:root{--bg:#0b1530;--fg:#e8edf8;--muted:#a3aec6;--accent:#f2c14e;--card:#13204a}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:1rem 1.25rem 3rem}
h1{font-size:1.75rem}h2{font-size:1.15rem;margin-top:2rem}.meta{color:var(--muted);margin:.2rem 0}
a{color:var(--accent);word-break:break-word}
.lang{display:flex;flex-wrap:wrap;gap:.5rem;padding:.75rem 0}.lang a{padding:.25rem .6rem;border-radius:.5rem;background:var(--card);text-decoration:none}
.lang a[aria-current]{font-weight:700;outline:2px solid var(--accent)}
.draft{background:var(--card);padding:.75rem 1rem;border-left:4px solid var(--accent);border-radius:.25rem}
"""

def page(lang, content, prefix, c):
    draft = '<p class="draft" lang="en">DRAFT: pending legal review and publisher details.</p>' if (not c.get("contactEmail") or "DRAFT" in c.get("version", "").upper()) else ""
    return f'''<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Logic Mix — Privacy</title><link rel="stylesheet" href="{prefix}style.css"></head>
<body><main>{switcher(lang, prefix)}
{draft}
{content}
</main></body></html>
'''

def build():
    c = config(); files = {"style.css": CSS}
    for tag, _ in LANGS:
        files[f"{tag}/index.html"] = page(tag, body((SRC / f"policy_{tag}.md").read_text(encoding="utf-8"), c), "../", c)
    files["index.html"] = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Logic Mix — Privacy</title><link rel="stylesheet" href="style.css"></head>
<body><main><h1>Logic Mix — Privacy Policy</h1>{switcher("", "")}</main></body></html>
'''
    return files

if __name__ == "__main__":
    files = build()
    if "--check" in sys.argv:
        bad = [n for n, t in files.items() if not (OUT / n).exists() or (OUT / n).read_text(encoding="utf-8") != t]
        if bad: print("Out of date:", *bad); sys.exit(1)
        print("privacy-site is up to date"); sys.exit(0)
    for n, t in files.items():
        p = OUT / n; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(t, encoding="utf-8")
    print(f"Wrote {len(files)} files")
