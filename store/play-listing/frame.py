"""Wrap each shots/*.png in Android Studio's Pixel 10 Pro device art -> shots_framed/."""
import pathlib
from PIL import Image, ImageDraw

ART = pathlib.Path(r"C:\Program Files\Android\Android Studio\plugins\android\resources"
                   r"\device-art-resources\pixel_10_pro")
ROOT = pathlib.Path(__file__).parent
SCREEN = (1280, 2856)
RADIUS = 99
SS = 4  # supersample factor for the corner mask

back = Image.open(ART / "back.webp").convert("RGBA")
overlay = Image.open(ART / "mask.webp").convert("RGBA")

# offset verified from back.webp's own transparent screen hole, not from the layout file
_a = back.getchannel("A").load()
_w, _h = back.size
_xs = [x for x in range(_w) if _a[x, _h // 2] == 0]
_ys = [y for y in range(_h) if _a[_w // 2, y] == 0]
OFF = (_xs[0], _ys[0])
assert (_xs[-1] - _xs[0] + 1, _ys[-1] - _ys[0] + 1) == SCREEN, (_xs[0], _xs[-1], _ys[0], _ys[-1])

corners = Image.new("L", (SCREEN[0] * SS, SCREEN[1] * SS), 0)
ImageDraw.Draw(corners).rounded_rectangle([0, 0, SCREEN[0] * SS - 1, SCREEN[1] * SS - 1],
                                          radius=RADIUS * SS, fill=255)
corners = corners.resize(SCREEN, Image.LANCZOS)

out = ROOT / "shots_framed"
out.mkdir(exist_ok=True)
for src in sorted((ROOT / "shots").glob("*.png")):
    shot = Image.open(src).convert("RGBA")
    assert shot.size == SCREEN, (src.name, shot.size)
    shot.putalpha(corners)
    canvas = back.copy()
    canvas.alpha_composite(shot, OFF)
    canvas.alpha_composite(overlay, OFF)
    canvas.save(out / src.name)
    print(src.name, "->", canvas.size, "at", OFF)
