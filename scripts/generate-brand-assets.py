"""
UMA Auto+ — brand asset generator.

Generates Android launcher icons (legacy + adaptive) and splash screen assets
from a single typographic design at all required densities.

Design:
  - Background: dark slate gradient (#0F172A top -> #1E293B bottom)
  - Mark:       bold "UMA" in white + accent "+" in warm orange (#FB923C)
  - Splash:     same background + larger wordmark "UMA Auto+" + subtitle

Run:
  python scripts/generate-brand-assets.py
"""

from __future__ import annotations

import json
import base64
import io
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------------------
# Project paths
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = REPO_ROOT / "android" / "app" / "src" / "main" / "res"
ASSETS_DIR = REPO_ROOT / "android" / "app" / "src" / "main" / "assets"

# Android density buckets (dp -> px multiplier).
DENSITY_DP_TO_PX = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Legacy launcher icon size in dp (Android pre-Adaptive Icon spec).
LEGACY_ICON_DP = 48
# Adaptive icon foreground/background size in dp (Android 8+ spec).
ADAPTIVE_ICON_DP = 108

# ---------------------------------------------------------------------------
# Brand palette
# ---------------------------------------------------------------------------

BG_TOP = (15, 23, 42)          # #0F172A — slate-900
BG_BOTTOM = (30, 41, 59)       # #1E293B — slate-800
TEXT_WHITE = (255, 255, 255)
ACCENT_ORANGE = (251, 146, 60) # #FB923C — orange-400
SUBTITLE_GREY = (203, 213, 225) # #CBD5E1 — slate-300


# ---------------------------------------------------------------------------
# Font discovery
# ---------------------------------------------------------------------------

def find_font(preferred: list[str], size: int) -> ImageFont.FreeTypeFont:
    """Return the first available system font from `preferred`, falling back to default."""
    candidate_dirs = [
        Path("C:/Windows/Fonts"),
        Path("/Library/Fonts"),
        Path("/usr/share/fonts"),
    ]
    for name in preferred:
        for d in candidate_dirs:
            if not d.exists():
                continue
            for p in d.rglob(name):
                try:
                    return ImageFont.truetype(str(p), size)
                except Exception:
                    continue
    # Last resort.
    return ImageFont.load_default()


# Pick the strongest sans-serif available for the wordmark + subtitle.
def bold_font(size: int) -> ImageFont.FreeTypeFont:
    return find_font([
        "InterDisplay-Black.ttf", "Inter-Black.ttf",
        "MontserratExtraBold.ttf", "Montserrat-Black.ttf",
        "segoeuib.ttf",  # Segoe UI Bold (Windows)
        "arialbd.ttf",   # Arial Bold (Windows)
        "Helvetica-Bold.ttf",
        "DejaVuSans-Bold.ttf",
    ], size)


def regular_font(size: int) -> ImageFont.FreeTypeFont:
    return find_font([
        "Inter-Regular.ttf",
        "segoeui.ttf",   # Segoe UI (Windows)
        "arial.ttf",
        "Helvetica.ttf",
        "DejaVuSans.ttf",
    ], size)


# ---------------------------------------------------------------------------
# Drawing primitives
# ---------------------------------------------------------------------------

def make_gradient(width: int, height: int, top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    """Vertical linear gradient between `top` and `bottom`."""
    img = Image.new("RGB", (width, height), top)
    px = img.load()
    for y in range(height):
        t = y / max(height - 1, 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        for x in range(width):
            px[x, y] = (r, g, b)
    return img


def measure_text(font: ImageFont.FreeTypeFont, text: str) -> tuple[int, int]:
    """Return (width, height) of `text` rendered with `font`."""
    bbox = font.getbbox(text)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def draw_icon_mark(img: Image.Image, *, width_safe_ratio: float = 0.72) -> None:
    """
    Draw the icon mark — bold white 'U' with an orange '+' to its upper-right.

    Sized to occupy `width_safe_ratio` of the canvas width so it never clips
    against the rounded-corner mask or adaptive icon safe zone (66/108 ≈ 61%
    for adaptive masks; we stay under for round-mask launchers).

    Why 'U+' instead of 'UMA+': at 48dp (~48px on mdpi) the full wordmark
    becomes illegible. 'U+' reads cleanly even at the smallest density and
    keeps brand identity (the '+' badge differentiates Auto+ from generic 'U').
    """
    draw = ImageDraw.Draw(img)
    w, h = img.size

    # Iteratively size the 'U' glyph so the U + plus combo fits within the safe width.
    target_total_w = int(w * width_safe_ratio)
    size = target_total_w  # initial guess
    for _ in range(10):
        f_u = bold_font(size)
        f_plus = bold_font(int(size * 0.55))
        u_w, _ = measure_text(f_u, "U")
        plus_w, _ = measure_text(f_plus, "+")
        spacing = int(size * 0.08)
        total = u_w + spacing + plus_w
        if total == 0:
            break
        size = max(8, int(size * target_total_w / total))
    f_u = bold_font(size)
    f_plus = bold_font(int(size * 0.55))

    u_w, u_h = measure_text(f_u, "U")
    plus_w, plus_h = measure_text(f_plus, "+")
    spacing = int(size * 0.08)

    total_w = u_w + spacing + plus_w
    base_x = (w - total_w) // 2
    base_y = (h - u_h) // 2 - int(size * 0.04)  # slight optical lift

    bbox_u = f_u.getbbox("U")
    bbox_plus = f_plus.getbbox("+")
    u_x = base_x - bbox_u[0]
    u_y = base_y - bbox_u[1]
    # Plus aligns to the top quarter of the U for a 'badge' feel.
    plus_x = base_x + u_w + spacing - bbox_plus[0]
    plus_y = base_y - bbox_plus[1] + int(u_h * 0.05)

    draw.text((u_x, u_y), "U", fill=TEXT_WHITE, font=f_u)
    draw.text((plus_x, plus_y), "+", fill=ACCENT_ORANGE, font=f_plus)


def draw_full_wordmark(img: Image.Image, *, top_y_ratio: float = 0.32, target_width_ratio: float = 0.78) -> None:
    """
    Draw the full 'UMA Auto+' wordmark for the splash screen.

    Sized to fit `target_width_ratio` of canvas width, anchored at `top_y_ratio`
    of canvas height. White 'UMA Auto', accent-color '+'.
    """
    draw = ImageDraw.Draw(img)
    w, h = img.size

    target_w = int(w * target_width_ratio)
    size = int(h * 0.18)  # initial guess
    for _ in range(10):
        f = bold_font(size)
        # Match the rendered combo width.
        uma_w, _ = measure_text(f, "UMA")
        space_w = int(size * 0.30)
        f_auto = bold_font(int(size * 0.85))
        auto_w, _ = measure_text(f_auto, "Auto")
        f_plus = bold_font(int(size * 0.95))
        plus_w, _ = measure_text(f_plus, "+")
        total = uma_w + space_w + auto_w + plus_w
        if total == 0:
            break
        size = max(12, int(size * target_w / total))

    f_uma = bold_font(size)
    f_auto = bold_font(int(size * 0.85))
    f_plus = bold_font(int(size * 0.95))

    uma_w, uma_h = measure_text(f_uma, "UMA")
    space_w = int(size * 0.30)
    auto_w, auto_h = measure_text(f_auto, "Auto")
    plus_w, plus_h = measure_text(f_plus, "+")

    total_w = uma_w + space_w + auto_w + plus_w
    base_x = (w - total_w) // 2
    base_y = int(h * top_y_ratio)

    bb = f_uma.getbbox("UMA")
    draw.text((base_x - bb[0], base_y - bb[1]), "UMA", fill=TEXT_WHITE, font=f_uma)

    bb = f_auto.getbbox("Auto")
    draw.text(
        (base_x + uma_w + space_w - bb[0], base_y - bb[1] + (uma_h - auto_h) // 2),
        "Auto",
        fill=TEXT_WHITE,
        font=f_auto,
    )

    bb = f_plus.getbbox("+")
    draw.text(
        (base_x + uma_w + space_w + auto_w - bb[0], base_y - bb[1] + int(uma_h * 0.05)),
        "+",
        fill=ACCENT_ORANGE,
        font=f_plus,
    )


# ---------------------------------------------------------------------------
# Icon generation
# ---------------------------------------------------------------------------

def make_legacy_icon(size_px: int) -> Image.Image:
    """Full square icon: gradient background + 'U+' mark + rounded corners."""
    img = make_gradient(size_px, size_px, BG_TOP, BG_BOTTOM).convert("RGBA")
    draw_icon_mark(img, width_safe_ratio=0.72)

    # Apply rounded corners for a softer look on home screens that don't apply
    # adaptive icon masking.
    corner_radius = int(size_px * 0.18)
    mask = Image.new("L", (size_px, size_px), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size_px, size_px), radius=corner_radius, fill=255)
    out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def make_round_icon(size_px: int) -> Image.Image:
    """Circular icon variant for launchers that prefer round masks."""
    img = make_gradient(size_px, size_px, BG_TOP, BG_BOTTOM).convert("RGBA")
    draw_icon_mark(img, width_safe_ratio=0.66)  # tighter safe zone for circle

    mask = Image.new("L", (size_px, size_px), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size_px, size_px), fill=255)
    out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def make_adaptive_foreground(size_px: int) -> Image.Image:
    """
    Adaptive icon foreground: 'U+' mark only, on transparent background.

    Android's adaptive icon spec guarantees only the inner 66dp circle (out of 108dp)
    is visible across all OEM masks. We use 0.55 width ratio to stay safely inside.
    """
    img = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    draw_icon_mark(img, width_safe_ratio=0.55)
    return img


def write_icon_set() -> None:
    """Write all 15 launcher PNGs (3 file types × 5 densities)."""
    print("\n=== Launcher icons ===")
    for density, mult in DENSITY_DP_TO_PX.items():
        out_dir = RES_DIR / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        legacy_px = int(LEGACY_ICON_DP * mult)
        adaptive_px = int(ADAPTIVE_ICON_DP * mult)

        legacy = make_legacy_icon(legacy_px)
        legacy.save(out_dir / "ic_launcher.png", optimize=True)

        round_icon = make_round_icon(legacy_px)
        round_icon.save(out_dir / "ic_launcher_round.png", optimize=True)

        fg = make_adaptive_foreground(adaptive_px)
        fg.save(out_dir / "ic_launcher_foreground.png", optimize=True)

        print(f"  {density:8s}: legacy {legacy_px}px, foreground {adaptive_px}px")


def write_adaptive_background_xml() -> None:
    """
    Replace the default Android-Studio mascot grid background with a clean
    vector gradient. Vector drawables let one file scale to any density.
    """
    print("\n=== Adaptive background (vector gradient) ===")
    xml = """<?xml version="1.0" encoding="utf-8"?>
<!--
  UMA Auto+ adaptive icon background.

  Solid fallback path (#0F172A) ensures the icon remains correct on any launcher
  that doesn't render the gradient (rare). The gradient overlays it for OEMs that
  do (Pixel, OneUI, etc).
-->
<vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"   android:startY="0"
                android:endX="0"     android:endY="108"
                android:startColor="#FF0F172A"
                android:endColor="#FF1E293B"/>
        </aapt:attr>
    </path>
</vector>
"""
    out = RES_DIR / "drawable" / "ic_launcher_background.xml"
    out.write_text(xml, encoding="utf-8")
    print(f"  wrote {out.relative_to(REPO_ROOT)}")


# ---------------------------------------------------------------------------
# Splash generation
# ---------------------------------------------------------------------------

# Master splash logo image (square). Used as the splash centerpiece.
# Big enough to look sharp on largest devices, small enough to embed.
SPLASH_LOGO_PX = 512


def make_splash_logo() -> Image.Image:
    """
    Splash centerpiece: gradient panel + 'UMA Auto+' wordmark + subtitle.

    Uses a single composed image (rather than separate native splash layers)
    so it slots into the existing expo-splash-screen `splash.json` pipeline
    without requiring native Android splash work.
    """
    w = h = SPLASH_LOGO_PX
    img = make_gradient(w, h, BG_TOP, BG_BOTTOM).convert("RGBA")

    # Soft radial highlight in upper third for visual interest.
    highlight = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hl = ImageDraw.Draw(highlight)
    hl.ellipse((-w // 4, -h // 3, w + w // 4, h // 2), fill=(251, 146, 60, 30))
    highlight = highlight.filter(ImageFilter.GaussianBlur(radius=40))
    img = Image.alpha_composite(img, highlight)

    # Wordmark "UMA Auto+" — sized via the dedicated helper so it never clips.
    draw_full_wordmark(img, top_y_ratio=0.32, target_width_ratio=0.78)

    # Subtitle — explain the build flavor at a glance.
    draw = ImageDraw.Draw(img)
    subtitle_size = int(h * 0.045)
    f_sub = regular_font(subtitle_size)
    sub_text = "Trackblazer Automation"
    sw, sh = measure_text(f_sub, sub_text)
    sb = f_sub.getbbox(sub_text)
    draw.text(
        ((w - sw) // 2 - sb[0], int(h * 0.55) - sb[1]),
        sub_text,
        fill=SUBTITLE_GREY,
        font=f_sub,
    )

    # Thin accent rule under the subtitle.
    rule_w = int(w * 0.18)
    rule_y = int(h * 0.62)
    draw.rectangle(
        ((w - rule_w) // 2, rule_y, (w + rule_w) // 2, rule_y + 2),
        fill=ACCENT_ORANGE,
    )

    # Small version stamp at bottom (matches package.json).
    ver_size = int(h * 0.032)
    f_ver = regular_font(ver_size)
    ver_text = "v1.2.5"
    vw, _ = measure_text(f_ver, ver_text)
    vb = f_ver.getbbox(ver_text)
    draw.text(
        ((w - vw) // 2 - vb[0], int(h * 0.92) - vb[1]),
        ver_text,
        fill=SUBTITLE_GREY,
        font=f_ver,
    )

    return img


def png_to_data_uri(img: Image.Image) -> str:
    """Encode a PIL image as a base64 data: URI suitable for splash.json."""
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    return f"data:image/png;base64,{b64}"


def write_splash_json(logo: Image.Image) -> None:
    """
    Replace splash.json with a single-asset bundle containing the new logo.

    The expo-splash-screen format: `{"assets":[{"h": <height>, "id": "0", "p": "<data uri>"}]}`
    where `h` is the rendered height in dp (we use the master width for a 1:1 logo).
    """
    print("\n=== Splash bundle ===")
    bundle = {
        "assets": [
            {
                "h": SPLASH_LOGO_PX,
                "id": "0",
                "p": png_to_data_uri(logo),
            }
        ]
    }
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    out = ASSETS_DIR / "splash.json"
    out.write_text(json.dumps(bundle), encoding="utf-8")
    size_kb = out.stat().st_size / 1024
    print(f"  wrote {out.relative_to(REPO_ROOT)} ({size_kb:.1f} KB)")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    print("UMA Auto+ brand asset generator")
    print(f"  repo: {REPO_ROOT}")

    write_icon_set()
    write_adaptive_background_xml()

    print("\n=== Splash logo ===")
    logo = make_splash_logo()
    preview_path = REPO_ROOT / "scripts" / "splash-preview.png"
    logo.save(preview_path, optimize=True)
    print(f"  preview: {preview_path.relative_to(REPO_ROOT)}")
    write_splash_json(logo)

    print("\nDone.")


if __name__ == "__main__":
    main()
