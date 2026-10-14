"""Видео-превью заставок с общим «кинослоем» (как SplashStage.kt): камера, вспышка, плёнка, кинополосы, уход в приложение.
Запуск: python3 preview.py <тема 1..7> [кадры/с]  → out/splash_<тема>.mp4
"""
import sys, os, re, math, random, subprocess, types, ctypes as C
import ds as dsm
from ds import DS, Color, Brush, Path, White, Black, Transparent, ease, span, lin
from PIL import Image, ImageDraw, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
W, H = 360, 780
DENS = 2.0  # пикселей на dp в превью

# сцены без собственных вспышек (их теперь делает кинослой)
src = open(os.path.join(HERE, 'scenes.py')).read()
src = re.sub(r"\n    fl = 1 - lin\(t, peak, peak \+ \d+\)\n    if t >= peak and fl > 0: ds\.drawRect\([^\n]*\n", "\n", src)
src = src.split('def render(')[0]
scenes = types.ModuleType('scenes_nf'); scenes.__dict__['__file__'] = os.path.join(HERE, 'scenes.py')
exec(compile(src, 'scenes_nf', 'exec'), scenes.__dict__)

get_data = dsm.f("cairo_image_surface_get_data", C.c_void_p, C.c_void_p)
get_stride = dsm.f("cairo_image_surface_get_stride", C.c_int, C.c_void_p)
dsm.lib.cairo_surface_flush.argtypes = [C.c_void_p]

def to_img(d):
    dsm.lib.cairo_surface_flush(d.surf)
    st = get_stride(d.surf); ptr = get_data(d.surf)
    buf = C.string_at(ptr, st * d.h)
    im = Image.frombuffer("RGBA", (d.w, d.h), buf, "raw", "BGRa", st, 1).copy()
    dsm.destroy(d.cr); dsm.surface_destroy(d.surf)
    return im

class Clear(DS):
    def __init__(s, w, h):
        s.w, s.h = w, h
        s.surf = dsm.image_surface_create(0, w, h); s.cr = dsm.create(s.surf)

THEMES = {
    1: dict(fn='princess', total=5000, peak=750, exit='HEART', tex='BOKEH', shake=2, flash=.45, streak=False, lb=False, acc=0xFFFF69B4, acc2=0xFFB98BFF, origin=(.5, .46)),
    2: dict(fn='nascar', total=5600, peak=2250, exit='CHECKER', tex='GRAIN', shake=7, flash=.5, streak=True, lb=True, acc=0xFFFFFFFF, acc2=0xFFE60019),
    3: dict(fn='gt', total=3200, peak=750, exit='CHECKER', tex='SCAN', shake=6, flash=.5, streak=True, lb=False, acc=0xFFE60019, acc2=0xFFFF5A1F),
    4: dict(fn='night', total=7800, peak=2250, exit='WARP', tex='GRAIN', shake=8, flash=.55, streak=True, lb=True, acc=0xFF00D9FF, acc2=0xFF1F6BFF),
    5: dict(fn='street', total=3300, peak=500, exit='GLITCH', tex='SCAN', shake=5, flash=.45, streak=True, lb=False, acc=0xFFFFB000, acc2=0xFFFF3B1F),
    6: dict(fn='rally', total=7100, peak=2500, exit='CHECKER', tex='GRAIN', shake=4, flash=.35, streak=False, lb=True, acc=0xFFD97830, acc2=0xFFFFB45A),
    7: dict(fn='drift', total=3850, peak=1250, exit='WARP', tex='GRAIN', shake=4, flash=.4, streak=True, lb=True, acc=0xFF5C7CFF, acc2=0xFFB04BE8),
}
HOLD = 500
EXIT = 640

def smooth(x): x = max(0., min(1., x)); return x * x * (3 - 2 * x)
def ramp(t, a, b): return max(0., min(1., (t - a) / (b - a)))
def fos(x):  # FastOutSlowIn ≈ cubic-bezier(.4,0,.2,1)
    lo, hi = 0., 1.
    for _ in range(30):
        m = (lo + hi) / 2
        bx = 3 * (1 - m) ** 2 * m * .4 + 3 * (1 - m) * m * m * .2 + m ** 3
        if bx < x: lo = m
        else: hi = m
    m = (lo + hi) / 2
    return 3 * (1 - m) * m * m * 1 + m ** 3

def camera(img, t, cfg, o):
    p = min(1., max(0., t / cfg['total']))
    push = 1.05 + 0.05 * smooth(p) + (0.25 * o * o if cfg['exit'] == 'WARP' else 0)
    tx = math.sin(t / 1900) * 3 * DENS; ty = math.cos(t / 2300) * 2.5 * DENS
    since = t - cfg['peak']
    if cfg['shake'] > 0 and 0 <= since < 560:
        k = 1 - since / 560; amp = cfg['shake'] * DENS * k * k
        tx += math.sin(since * 0.11) * amp; ty += math.cos(since * 0.137) * amp * 0.8
    rot = math.radians(math.sin(t / 2700) * 0.35)
    cx, cy = W / 2, H / 2
    # обратное преобразование: из точки экрана в точку кадра
    ca, sa = math.cos(-rot), math.sin(-rot)
    a = ca / push; b = -sa / push; d = sa / push; e = ca / push
    c = cx - a * (cx + tx) - b * (cy + ty)
    f = cy - d * (cx + tx) - e * (cy + ty)
    return img.transform((W, H), Image.AFFINE, (a, b, c, d, e, f), resample=Image.BICUBIC, fillcolor=(0, 0, 0, 255))

def post(t, cfg, o):
    d = Clear(W, H)
    acc = Color(cfg['acc'])
    dark = 1 - ramp(t, 0, 380)
    if dark > 0: d.drawRect(Black.copy(dark))
    tex = cfg['tex']
    if tex == 'GRAIN':
        r = random.Random(int(t / 42)); dot = 1.1 * DENS
        for _ in range(150):
            c = White.copy(0.055) if r.random() < .5 else Black.copy(0.09)
            d.drawRect(c, (r.random() * W, r.random() * H), (dot, dot))
    elif tex == 'SCAN':
        step = 3 * DENS; y = 0.
        while y < H: d.drawRect(Black.copy(0.13), (0, y), (W, step * 0.4)); y += step
        by = ((t * 0.32) % (H * 1.3)) - H * 0.15
        d.drawRect(Brush.vertical([Transparent, White.copy(0.05), Transparent], by - 40 * DENS, by + 40 * DENS), (0, by - 40 * DENS), (W, 80 * DENS))
    elif tex == 'BOKEH':
        for i in range(9):
            seed = (i * 0.618034) % 1
            x = W * ((seed + 0.08 * math.sin(t / 2100 + i)) % 1)
            y = H * (1.1 - ((t / 9000 * (0.6 + seed) + seed) % 1.2))
            rr = (26 + 30 * ((i * 0.37) % 1)) * DENS
            col = acc if i % 2 == 0 else White
            d.drawCircle(Brush.radial([col.copy(0.10), Transparent], (x, y), rr), rr, (x, y))
    pk = cfg['peak']; k = ramp(t, pk, pk + 460)
    if t >= pk and k < 1 and cfg['flash'] > 0:
        fl = (1 - k) ** 2 * cfg['flash']; c = (W / 2, H * 0.46); rr = max(W, H) * 0.75
        d.drawCircle(Brush.radial([White.copy(0.55 * fl), acc.copy(0.35 * fl), Transparent], c, rr), rr, c)
        if cfg['streak']:
            half = 2.2 * DENS
            d.drawRect(Brush.horizontal([Transparent, acc.copy(fl), White.copy(fl), acc.copy(fl), Transparent]), (0, c[1] - half), (W, half * 2))
            band = 34 * DENS
            d.drawRect(Brush.vertical([Transparent, acc.copy(0.22 * fl), Transparent], c[1] - band, c[1] + band), (0, c[1] - band), (W, band * 2))
    if cfg['exit'] == 'WARP' and o > 0:
        c = (W / 2, H / 2); r = random.Random(9); reach = math.hypot(W, H) * 0.6
        for _ in range(48):
            a = r.random() * 2 * math.pi
            st = reach * (0.15 + 0.5 * r.random()) * (0.4 + o)
            ln = reach * (0.2 + 0.5 * o) * (0.5 + r.random())
            dx, dy = math.cos(a), math.sin(a)
            p0 = (c[0] + dx * st, c[1] + dy * st); p1 = (c[0] + dx * (st + ln), c[1] + dy * (st + ln))
            d.drawLine(Brush.linear([Transparent, acc.copy(0.55 * (1 - o)), White.copy(0.7 * (1 - o))], p0, p1), p0, p1, strokeWidth=(1.2 + 1.8 * r.random()) * DENS)
    if cfg['lb']:
        bar = H * 0.065 * (1 - smooth(o * 1.6))
        if bar > 0.5:
            d.drawRect(Black, (0, 0), (W, bar)); d.drawRect(Black, (0, H - bar), (W, bar))
    return to_img(d)

def heart_poly(cx, cy, s, n=80):
    pts = []
    def bez(p0, p1, p2, p3, q):
        u = 1 - q
        return (u ** 3 * p0[0] + 3 * u * u * q * p1[0] + 3 * u * q * q * p2[0] + q ** 3 * p3[0],
                u ** 3 * p0[1] + 3 * u * u * q * p1[1] + 3 * u * q * q * p2[1] + q ** 3 * p3[1])
    a = (cx, cy + s * 0.9); m = (cx, cy - s * 0.45)
    for i in range(n): pts.append(bez(a, (cx - s * 1.5, cy - s * 0.1), (cx - s * 0.7, cy - s * 1.2), m, i / n))
    for i in range(n + 1): pts.append(bez(m, (cx + s * 0.7, cy - s * 1.2), (cx + s * 1.5, cy - s * 0.1), a, i / n))
    return pts

def exit_comp(frame, app, cfg, o):
    if o <= 0: return frame
    acc = tuple(int(x) for x in ((cfg['acc'] >> 16) & 255, (cfg['acc'] >> 8) & 255, cfg['acc'] & 255))
    acc2 = tuple(int(x) for x in ((cfg['acc2'] >> 16) & 255, (cfg['acc2'] >> 8) & 255, cfg['acc2'] & 255))
    ex = cfg['exit']
    out = app.copy()
    if ex == 'WARP':
        s = 1 + 1.5 * o * o; alpha = 1 - smooth(ramp(o, 0.25, 1))
        fw, fh = int(W * s), int(H * s)
        big = frame.resize((fw, fh), Image.BICUBIC).crop(((fw - W) // 2, (fh - H) // 2, (fw - W) // 2 + W, (fh - H) // 2 + H))
        return Image.blend(app, big, alpha)
    mask = Image.new('L', (W, H), 0)
    md = ImageDraw.Draw(mask)
    ov = Image.new('RGBA', (W, H), (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
    if ex in ('IRIS', 'HEART'):
        c = (W * cfg['origin'][0], H * cfg['origin'][1])
        far = math.hypot(max(c[0], W - c[0]), max(c[1], H - c[1]))
        e = smooth(o)
        md.rectangle((0, 0, W, H), fill=255)
        if ex == 'IRIS':
            r = far * 1.02 * e
            md.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), fill=0)
            wdt = int((10 * (1 - o) + 1.5) * DENS)
            od.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), outline=acc + (int(217 * (1 - o)),), width=max(1, wdt))
        else:
            s = far * 1.9 * e
            poly = heart_poly(c[0], c[1] + s * 0.15, s)
            md.polygon(poly, fill=0)
            wdt = int((12 * (1 - o) + 1.5) * DENS)
            od.line(poly + [poly[0]], fill=(255, 209, 236, int(230 * (1 - o))), width=max(1, wdt))
        out.paste(frame, (0, 0), mask)
        out.alpha_composite(ov)
    elif ex == 'CHECKER':
        cols = 7; cell = W / cols; rows = math.ceil(H / cell); sp = cols + rows
        for r in range(rows):
            for ci in range(cols):
                dd = (ci + r) / sp
                p = max(0., min(1., (o - dd * 0.55) / 0.45))
                s = 1 - smooth(p)
                if s <= 0: continue
                cx, cy = ci * cell + cell / 2, r * cell + cell / 2; half = cell / 2 * s + 0.5
                md.rectangle((cx - half, cy - half, cx + half, cy + half), fill=255)
        out.paste(frame, (0, 0), mask)
    elif ex == 'GLITCH':
        bands = 14; bh = H / bands; rnd = random.Random(21)
        for i in range(bands):
            lag = rnd.random() * 0.45
            p = max(0., min(1., (o - lag) / (1 - 0.45)))
            dr = 1 if i % 2 == 0 else -1
            dx = dr * W * p * p * 1.05
            top = int(i * bh); bot = int((i + 1) * bh) + 1
            if p >= 1: continue
            strip = frame.crop((0, top, W, bot))
            out.paste(strip, (int(dx), top))
            if p > 0:
                ex_ = dx if dr > 0 else W + dx
                col = acc2 if i % 3 == 0 else acc
                od.rectangle((ex_ - 3 * DENS, top, ex_ + 3 * DENS, bot), fill=col + (int(191 * (1 - p)),))
        out.alpha_composite(ov)
    return out

def run(theme, fps=30):
    cfg = THEMES[theme]
    fn = getattr(scenes, cfg['fn'])
    appp = os.path.join(HERE, 'app', f'V3_{theme}_map.png')
    app = Image.open(appp).convert('RGBA').resize((W, H), Image.LANCZOS)
    fdir = os.path.join(HERE, 'vid', str(theme)); os.makedirs(fdir, exist_ok=True)
    total = cfg['total']; dur = HOLD + total + EXIT + 600
    n = int(dur / 1000 * fps)
    for i in range(n):
        T = i * 1000 / fps
        if T < HOLD:
            fr = Image.new('RGBA', (W, H), (0, 0, 0, 255))
        else:
            t = min(T - HOLD, total)
            o = 0.
            if T - HOLD > total:
                x = min(1., (T - HOLD - total) / EXIT)
                o = 4 * x ** 3 if x < .5 else 1 - (-2 * x + 2) ** 3 / 2
            d = DS(W, H); fn(d, t, float(cfg['peak'])); sc = to_img(d)
            fr = camera(sc, t, cfg, o)
            fr.alpha_composite(post(t, cfg, o))
            fr = exit_comp(fr, app, cfg, o)
        fr.convert('RGB').save(os.path.join(fdir, f'{i:04d}.png'))
    os.makedirs(os.path.join(HERE, 'out'), exist_ok=True)
    outp = os.path.join(HERE, 'out', f'splash_{theme}.mp4')
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', str(fps), '-i', os.path.join(fdir, '%04d.png'),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', outp], check=True)
    print(outp)

if __name__ == '__main__':
    run(int(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 30)
