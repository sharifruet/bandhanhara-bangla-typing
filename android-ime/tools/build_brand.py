#!/usr/bin/env python3
"""
Build every brand asset from one vector drawing of the mascot: a happy green bird holding a bamboo
fountain pen, writing অ. The design follows assets/new-logo.jpg (the reference artwork); the shapes
below are traced in that image's 1024 × 1024 coordinates and scaled into each output.

Outputs
  assets/icon.svg, assets/icon.png        app icon, rounded square, 512 px
  assets/logo.svg, assets/logo.png        wide banner: icon + wordmark
  assets/mascot.svg                       the bird alone, transparent
  android-ime/app/src/main/res/drawable/
      ic_launcher_foreground.xml          adaptive-icon foreground (art inside the 66 dp safe zone)
      ic_launcher_background.xml          adaptive-icon background (warm cream)
      ic_launcher_monochrome.xml          Android 13+ themed icon (single-colour silhouette)
      ic_logo.xml                         full-colour rounded icon for use inside the app
  android-ime/store/                      Play Store images
      icon-512.png, feature-graphic-1024x500.png
  web/public/favicon.svg                  web prototype favicon
  mobile/assets/                          Expo app: icon, adaptive-icon, splash-icon, favicon

Text is converted to outlines with HarfBuzz shaping (Noto Sans Bengali), so vowel signs and
chandrabindu sit correctly and the SVGs render the same without the font installed.

Usage:  pip install fonttools uharfbuzz resvg-py && python3 android-ime/tools/build_brand.py
"""
import math
import os
import shutil
import subprocess
import tempfile

import uharfbuzz as hb
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = os.path.join(ROOT, "android-ime", "app", "src", "main", "res", "font", "noto_sans_bengali.ttf")
RES = os.path.join(ROOT, "android-ime", "app", "src", "main", "res", "drawable")
STORE = os.path.join(ROOT, "android-ime", "store")
ASSETS = os.path.join(ROOT, "assets")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# ── Palette (sampled from assets/new-logo.jpg) ─────────────────────────────────
CREAM = "#F3E7CC"          # background
CREAM_LIGHT = "#F9F1DE"
CREAM_DARK = "#EBDAB6"
BODY = "#3C6A58"           # forest-green feathers
BODY_DARK = "#2E5646"      # wing, shading
BODY_LINE = "#244538"
BELLY = "#F8F2E3"          # white chest and eye patch
BELLY_SHADE = "#E4DCC8"
EYE = "#233A31"
BEAK = "#E6A544"
BEAK_DARK = "#C9852C"
MOUTH = "#B8433A"
PEN = "#DDB779"            # bamboo
PEN_DARK = "#A57C43"
PEN_EDGE = "#8A6534"
NIB = "#C99B4E"            # brass nib
NIB_DARK = "#5B4424"
FEET = "#2E3E37"
SHADOW = "#DCCDAA"
INK = "#1B5752"            # the letter অ
WORD_DARK = "#1E4D43"
WORD_MID = "#3C6A58"
WORD_SOFT = "#7A6E57"

# Where the art sits in its 1024 space, and the point to centre on.
ART_CENTER = (510.0, 448.0)


def f(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


# ── Geometry helpers (absolute path data shared by SVG and VectorDrawable) ─────
def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 1,0 {f(cx + r)},{f(cy)} A{f(r)},{f(r)} 0 1,0 {f(cx - r)},{f(cy)} Z"


def ellipse(cx, cy, rx, ry, rot=0.0):
    a = math.radians(rot)
    dx, dy = rx * math.cos(a), rx * math.sin(a)
    return (f"M{f(cx - dx)},{f(cy - dy)} A{f(rx)},{f(ry)} {f(rot)} 1,0 {f(cx + dx)},{f(cy + dy)} "
            f"A{f(rx)},{f(ry)} {f(rot)} 1,0 {f(cx - dx)},{f(cy - dy)} Z")


def poly(*pts):
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"


def shape(d, fill=None, alpha=1.0, stroke=None, width=0.0, evenodd=False):
    return dict(d=d, fill=fill, alpha=alpha, stroke=stroke, width=width, evenodd=evenodd)


# ── Text → outlines ────────────────────────────────────────────────────────────
_tt = TTFont(FONT)
_order = _tt.getGlyphOrder()
_blob = hb.Blob.from_file_path(FONT)
_face = hb.Face(_blob)


def text_path(text, size, x, y, weight=400, anchor="start", width_axis=100):
    """Shaped outline of [text] at baseline (x, y). Returns (path data, width)."""
    font = hb.Font(_face)
    font.set_variations({"wght": weight, "wdth": width_axis})
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf)
    scale = size / _face.upem
    width = sum(p.x_advance for p in buf.glyph_positions) * scale
    x0 = x - (width / 2 if anchor == "middle" else width if anchor == "end" else 0)
    glyphs = _tt.getGlyphSet(location={"wght": weight, "wdth": width_axis})
    parts, pen_x = [], 0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        pen = SVGPathPen(glyphs, ntos=f)
        gx = x0 + (pen_x + pos.x_offset) * scale
        gy = y - pos.y_offset * scale
        glyphs[_order[info.codepoint]].draw(TransformPen(pen, (scale, 0, 0, -scale, gx, gy)))
        parts.append(pen.getCommands())
        pen_x += pos.x_advance
    return " ".join(p for p in parts if p), width


# ── The mascot, in the reference image's 1024 × 1024 space ────────────────────
def mascot():
    s = []

    # Ground shadow under the feet
    s.append(shape(ellipse(600, 652, 112, 13), SHADOW, alpha=0.8))

    # Tail (behind the body)
    s.append(shape("M690,488 C715,500 742,508 770,512 C752,534 724,552 694,560 Z", BODY_DARK))

    # Body: a round head flowing into a plump body
    s.append(shape(
        "M572,316 C628,314 668,356 670,414 C671,446 684,470 700,494 "
        "C716,524 706,560 684,582 C660,606 626,618 592,614 "
        "C548,610 512,584 500,544 C491,512 494,482 486,452 "
        "C476,414 480,370 506,342 C524,324 547,317 572,316 Z", BODY))

    # White chest
    s.append(shape(
        "M498,488 C522,512 556,530 596,542 C616,550 616,580 598,600 "
        "C566,604 532,588 514,562 C502,544 496,516 498,488 Z", BELLY))
    s.append(shape("M598,600 C614,582 616,562 606,548 C624,560 626,584 610,598 Z", BELLY_SHADE))

    # Folded wing with two feather lines
    s.append(shape(
        "M606,468 C646,442 700,456 724,506 C730,520 726,532 714,536 "
        "C688,556 648,574 610,566 C592,536 590,494 606,468 Z", BODY_DARK))
    for d in ("M628,500 C656,494 684,502 704,516", "M624,530 C652,528 678,534 700,544"):
        s.append(shape(d, stroke=BODY_LINE, width=5))

    # Feet
    for x in (592, 628):
        s.append(shape(f"M{x + 6},606 L{x},638", stroke=FEET, width=7))
        s.append(shape(f"M{x},638 L{x - 22},650 M{x},638 L{x - 6},654 M{x},638 L{x + 12},651", stroke=FEET, width=6))

    # Eye patch and the closed, smiling eye
    s.append(shape(ellipse(535, 408, 40, 44), BELLY))
    s.append(shape("M515,412 Q532,392 550,410", stroke=EYE, width=7))

    # Fountain pen: bamboo barrel from the top down to a brass nib touching the headline
    tx, ty = 526, 196          # top end
    nx, ny = 352, 552          # nib tip
    L = math.hypot(nx - tx, ny - ty)
    ux, uy = (nx - tx) / L, (ny - ty) / L
    px, py = -uy, ux
    w = 18                     # half-width of the barrel

    def at(d, side):
        return (tx + ux * d + px * side, ty + uy * d + py * side)

    barrel_end = L - 78
    def pt(p):
        return f"{f(p[0])},{f(p[1])}"
    # Barrel with a rounded top end
    s.append(shape(f"M{pt(at(0, w))} L{pt(at(barrel_end, w))} L{pt(at(barrel_end, -w))} L{pt(at(0, -w))} "
                   f"A{w},{w} 0 0,0 {pt(at(0, w))} Z", PEN, stroke=PEN_EDGE, width=4))
    s.append(shape(poly(at(0, -w), at(barrel_end, -w), at(barrel_end, -w * 0.45), at(0, -w * 0.45)), PEN_DARK, alpha=0.35))
    for d in (L * 0.2, L * 0.45):            # bamboo nodes
        s.append(shape(poly(at(d - 4, -w - 1), at(d - 4, w + 1), at(d + 4, w + 1), at(d + 4, -w - 1)), PEN_DARK))
    s.append(shape(poly(at(barrel_end, -w), at(barrel_end, w), at(barrel_end + 14, w * 0.8), at(barrel_end + 14, -w * 0.8)), PEN_EDGE))
    nib_base = barrel_end + 14
    s.append(shape(poly(at(nib_base, -w * 0.8), at(nib_base, w * 0.8), at(L, 0)), NIB))
    s.append(shape(f"M{f(at(nib_base + 22, 0)[0])},{f(at(nib_base + 22, 0)[1])} L{f(at(L - 4, 0)[0])},{f(at(L - 4, 0)[1])}",
                   stroke=NIB_DARK, width=3))
    s.append(shape(circle(*at(nib_base + 20, 0), 4), NIB_DARK))

    # Arm and wing-hand wrapped round the barrel
    s.append(shape("M492,470 C460,466 432,452 414,432 L434,410 C452,428 474,440 500,446 Z", BODY))
    s.append(shape(ellipse(404, 410, 36, 42, -28), BODY))
    # Fingers curling over the barrel
    s.append(shape("M412,386 C432,378 446,390 440,402 C436,410 424,410 416,404 Z", BODY))
    s.append(shape("M414,408 C436,402 448,414 440,426 C434,432 422,430 416,424 Z", BODY))
    for d in ("M376,400 C388,394 402,396 412,404", "M374,420 C386,414 400,416 410,424", "M380,438 C392,434 404,436 412,442"):
        s.append(shape(d, stroke=BODY_LINE, width=5))

    # Open beak, singing along
    s.append(shape("M504,424 L462,436 L500,452 Z", MOUTH))
    s.append(shape("M510,396 C486,396 458,404 440,414 C462,420 488,424 508,428 Z", BEAK))
    s.append(shape("M506,430 C488,436 472,444 462,452 C478,462 494,464 504,458 Z", BEAK_DARK))

    # The letter it is writing: অ, its headline right under the nib
    letter, _ = text_path("অ", 250, 262, 692, weight=560, width_axis=78)
    s.append(shape(letter, INK))
    return s


def monochrome():
    """Themed-icon silhouette: one colour; the eye patch and chest are left out as negative space."""
    keep = {BODY, BODY_DARK, BEAK, BEAK_DARK, PEN, PEN_EDGE, NIB, INK, MOUTH}
    out = []
    for sh in mascot():
        if sh["fill"] in keep:
            out.append(dict(sh, fill="#000000", alpha=1.0))
        elif sh["stroke"] == FEET:
            out.append(dict(sh, stroke="#000000"))
    return out


# ── Writers ────────────────────────────────────────────────────────────────────
def svg_shapes(shapes, indent="  "):
    out = []
    for sh in shapes:
        a = [f'd="{sh["d"]}"']
        a.append(f'fill="{sh["fill"]}"' if sh["fill"] else 'fill="none"')
        if sh["alpha"] < 1:
            a.append(f'fill-opacity="{sh["alpha"]}"')
        if sh["evenodd"]:
            a.append('fill-rule="evenodd"')
        if sh["stroke"]:
            a.append(f'stroke="{sh["stroke"]}" stroke-width="{sh["width"]}" stroke-linecap="round" stroke-linejoin="round"')
        out.append(f"{indent}<path {' '.join(a)}/>")
    return "\n".join(out)


def vd_color(hex_, alpha=1.0):
    return "#" + f"{round(alpha * 255):02X}" + hex_.lstrip("#").upper()


def vd_shapes(shapes, indent="    "):
    out = []
    for sh in shapes:
        a = [f'android:pathData="{sh["d"]}"']
        if sh["fill"]:
            a.append(f'android:fillColor="{vd_color(sh["fill"], sh["alpha"])}"')
        if sh["evenodd"]:
            a.append('android:fillType="evenOdd"')
        if sh["stroke"]:
            a.append(f'android:strokeColor="{vd_color(sh["stroke"])}" android:strokeWidth="{sh["width"]}" '
                     'android:strokeLineCap="round" android:strokeLineJoin="round"')
        out.append(f"{indent}<path\n{indent}    " + f"\n{indent}    ".join(a) + " />")
    return "\n".join(out)


def fit(size, cx, cy, scale):
    """Transform placing the art's centre at (cx, cy) with 1024-space → target [scale]."""
    return scale, cx - ART_CENTER[0] * scale, cy - ART_CENTER[1] * scale


def svg_group(shapes, t, indent="  "):
    s, tx, ty = t
    return (f'{indent}<g transform="matrix({f(s)} 0 0 {f(s)} {f(tx)} {f(ty)})">\n'
            f"{svg_shapes(shapes, indent + '  ')}\n{indent}</g>")


def vd_group(shapes, t, indent="    "):
    s, tx, ty = t
    return (f'{indent}<group android:scaleX="{s:.5f}" android:scaleY="{s:.5f}" '
            f'android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n'
            f"{vd_shapes(shapes, indent + '    ')}\n{indent}</group>")


def svg_defs(uid=""):
    return f"""  <defs>
    <radialGradient id="bg{uid}" cx="0.45" cy="0.4" r="0.75">
      <stop offset="0" stop-color="{CREAM_LIGHT}"/>
      <stop offset="0.6" stop-color="{CREAM}"/>
      <stop offset="1" stop-color="{CREAM_DARK}"/>
    </radialGradient>
  </defs>"""


ICON_SCALE = 0.1245      # 1024-space → 108 grid for the rounded-square icon (no launcher mask)
LAUNCHER_SCALE = 0.1045  # smaller for launcher masks: everything stays inside the 66 dp safe zone


def icon_svg(size=512, radius=24):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">
{svg_defs()}
  <rect width="108" height="108" rx="{radius}" fill="url(#bg)"/>
{svg_group(mascot(), fit(108, 54, 54, ICON_SCALE))}
</svg>
"""


def mascot_svg():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="240 170 550 550" width="512" height="512">
{svg_shapes(mascot())}
</svg>
"""


def banner_svg(w, h, icon_size, title_size, sub_size, tag_size, tagline, radius=28):
    pad = (h - icon_size) / 2
    tx = pad * 1.5 + icon_size
    avail = w - tx - pad * 1.2

    def fitted(text, size, weight):
        _, width = text_path(text, size, 0, 0, weight=weight)
        return min(size, size * avail / width)

    title_size = fitted("বাঁধনহারা বাংলা", title_size, 680)
    sub_size = fitted("Bandhanhara Bangla", sub_size, 560)
    tag_size = fitted(tagline, tag_size, 420)
    base = h * 0.5 - title_size * 0.15
    title, _ = text_path("বাঁধনহারা বাংলা", title_size, tx, base, weight=680)
    sub, _ = text_path("Bandhanhara Bangla", sub_size, tx + 2, base + sub_size * 1.45, weight=560)
    tag, _ = text_path(tagline, tag_size, tx + 2, base + sub_size * 1.45 + tag_size * 1.8, weight=420)
    art = fit(108, pad + icon_size / 2, h / 2, icon_size / 108 * ICON_SCALE * 1.02)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">
{svg_defs("b")}
  <rect width="{w}" height="{h}" rx="{radius}" fill="url(#bgb)"/>
{svg_group(mascot(), art)}
  <path d="{title}" fill="{WORD_DARK}"/>
  <path d="{sub}" fill="{WORD_MID}"/>
  <path d="{tag}" fill="{WORD_SOFT}"/>
</svg>
"""


def vd(body, size=108, extra_ns=""):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by android-ime/tools/build_brand.py — edit the script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra_ns}
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
"""


def cream_gradient(indent="        "):
    return f"""{indent}<aapt:attr name="android:fillColor">
{indent}    <gradient android:type="radial" android:centerX="48" android:centerY="43" android:gradientRadius="80">
{indent}        <item android:offset="0" android:color="{vd_color(CREAM_LIGHT)}" />
{indent}        <item android:offset="0.6" android:color="{vd_color(CREAM)}" />
{indent}        <item android:offset="1" android:color="{vd_color(CREAM_DARK)}" />
{indent}    </gradient>
{indent}</aapt:attr>"""


AAPT = '\n    xmlns:aapt="http://schemas.android.com/aapt"'


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("  wrote", os.path.relpath(path, ROOT))


def render(svg_text, w, h, out):
    """Rasterise an SVG: with resvg if installed (pip install resvg-py), else headless Chrome."""
    try:
        import resvg_py
        png = resvg_py.svg_to_bytes(svg_string=svg_text, width=w, height=h)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as fh:
            fh.write(bytes(png))
        print("  wrote", os.path.relpath(out, ROOT))
        return
    except ImportError:
        pass
    if not os.path.exists(CHROME):
        print("  (Chrome not found — skipped", os.path.relpath(out, ROOT) + ")")
        return
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "p.html")
        with open(page, "w", encoding="utf-8") as fh:
            fh.write(f"<html><body style='margin:0;background:transparent'>{svg_text}</body></html>")
        shot = os.path.join(tmp, "shot.png")
        args = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                f"--user-data-dir={os.path.join(tmp, 'profile')}",
                "--default-background-color=00000000", f"--window-size={w},{h}",
                f"--screenshot={shot}", "file://" + page]
        for attempt in range(2):  # headless Chrome occasionally hangs; give it a second chance
            try:
                subprocess.run(args, check=True, capture_output=True, timeout=90)
                break
            except subprocess.TimeoutExpired:
                if attempt == 1:
                    raise
        os.makedirs(os.path.dirname(out), exist_ok=True)
        shutil.move(shot, out)
    print("  wrote", os.path.relpath(out, ROOT))


def main():
    print("Brand assets:")
    art = mascot()
    tagline = "Big keys · Smart suggestions · Made for Bangla"

    # Repo artwork
    write(os.path.join(ASSETS, "icon.svg"), icon_svg())
    write(os.path.join(ASSETS, "mascot.svg"), mascot_svg())
    banner = banner_svg(900, 260, 210, 64, 24, 17, tagline)
    write(os.path.join(ASSETS, "logo.svg"), banner)

    # Android
    launcher = fit(108, 54, 54, LAUNCHER_SCALE)
    write(os.path.join(RES, "ic_launcher_foreground.xml"), vd(vd_group(art, launcher)))
    write(os.path.join(RES, "ic_launcher_monochrome.xml"), vd(vd_group(monochrome(), launcher)))
    write(os.path.join(RES, "ic_launcher_background.xml"), vd(
        f"""    <path android:pathData="M0,0h108v108h-108z">
{cream_gradient()}
    </path>""", extra_ns=AAPT))
    rounded = "M24,0 H84 A24,24 0 0 1 108,24 V84 A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z"
    write(os.path.join(RES, "ic_logo.xml"), vd(
        f"""    <path android:pathData="{rounded}">
{cream_gradient()}
    </path>
{vd_group(art, fit(108, 54, 54, ICON_SCALE))}""", size=96, extra_ns=AAPT))

    # Store images and PNG previews
    render(icon_svg(512, radius=0), 512, 512, os.path.join(STORE, "icon-512.png"))
    render(banner_svg(1024, 500, 360, 96, 36, 25, tagline, radius=0), 1024, 500,
           os.path.join(STORE, "feature-graphic-1024x500.png"))
    render(icon_svg(512), 512, 512, os.path.join(ASSETS, "icon.png"))
    render(banner, 900, 260, os.path.join(ASSETS, "logo.png"))

    # Web prototype and Expo mobile app
    write(os.path.join(ROOT, "web", "public", "favicon.svg"), icon_svg(64))
    mobile = os.path.join(ROOT, "mobile", "assets")
    render(icon_svg(1024, radius=0), 1024, 1024, os.path.join(mobile, "icon.png"))
    render(icon_svg(48, radius=10), 48, 48, os.path.join(mobile, "favicon.png"))
    bare = lambda scale: f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">
{svg_group(art, fit(108, 54, 54, scale))}
</svg>
"""
    render(bare(LAUNCHER_SCALE), 1024, 1024, os.path.join(mobile, "adaptive-icon.png"))
    render(bare(ICON_SCALE * 0.8), 1024, 1024, os.path.join(mobile, "splash-icon.png"))


if __name__ == "__main__":
    main()
