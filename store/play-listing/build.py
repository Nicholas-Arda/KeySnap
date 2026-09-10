"""Inline every local image of template.html as a data URI -> deck.html (artifact + export source)."""
import base64, io, re, sys, pathlib
from PIL import Image
root=pathlib.Path(__file__).parent
html=(root/"template.html").read_text(encoding="utf-8")
missing=[]
def data_uri(path):
    p=root/path
    if not p.exists(): missing.append(path); return path
    if p.suffix==".svg": return "data:image/svg+xml;base64,"+base64.b64encode(p.read_bytes()).decode()
    if path.startswith("shots/") or path.startswith("shots_framed/"):
        # framed shots carry the device-art alpha; RGB would paint a black box round the phone
        im=Image.open(p); im=im.convert("RGBA" if path.startswith("shots_framed/") else "RGB")
        buf=io.BytesIO(); im.save(buf,"WEBP",quality=92,method=6)
        return "data:image/webp;base64,"+base64.b64encode(buf.getvalue()).decode()
    return "data:image/png;base64,"+base64.b64encode(p.read_bytes()).decode()
out=re.sub(r'src="((?:shots_framed|shots|assets)/[^"]+)"', lambda m: 'src="%s"'%data_uri(m.group(1)), html)
(root/"deck.html").write_text(out,encoding="utf-8")
print("deck.html", len(out)//1024, "KB; missing:", missing or "none")
