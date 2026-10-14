"""Зеркало SplashArt.kt для превью."""
import math, random
from ds import Color, Brush, Path, White, Black, Transparent

def _u(ds): return min(ds.w, ds.h) / 100
def _p(t, a, b): return max(0., min(1., (t - a) / (b - a)))

def shock_ring(ds, t, at, c, maxR, col, dur=700., strength=1.):
    u = _u(ds)
    for k in range(2):
        p = _p(t, at + k * 130, at + k * 130 + dur)
        if p <= 0 or p >= 1: continue
        e = 1 - (1 - p) ** 3
        r = maxR * e * (1 - 0.18 * k)
        a = (1 - p) * strength * (1 - 0.35 * k)
        if r < 1: continue
        ds.drawCircle(Brush.radial([(0, Transparent), (0.78, Transparent), (0.95, col.copy(0.22 * a)), (1, Transparent)], c, r * 1.08), r * 1.08, c)
        ds.drawCircle(col.copy(0.75 * a), r, c, {"width": u * (1.8 - 1.2 * p)})

def rays_from(ds, c, n, ln, col, a, rot, spread=0.07):
    if a <= 0.003: return
    for k in range(n):
        ang = rot + k * 2 * math.pi / n
        ex, ey = c[0] + math.cos(ang) * ln, c[1] + math.sin(ang) * ln
        nx, ny = -math.sin(ang) * ln * spread, math.cos(ang) * ln * spread
        p = Path().moveTo(*c).lineTo(ex + nx, ey + ny).lineTo(ex - nx, ey - ny).close()
        ds.drawPath(p, Brush.radial([col.copy(a), Transparent], c, ln))

def flare_at(ds, src, col, a):
    if a <= 0.01: return
    u = _u(ds); c = (ds.w / 2, ds.h / 2)
    ds.drawCircle(Brush.radial([col.copy(0.45 * a), Transparent], src, u * 16), u * 16, src)
    L = u * 34
    ds.drawLine(Brush.horizontal([Transparent, col.copy(0.6 * a), White.copy(0.8 * a), col.copy(0.6 * a), Transparent], src[0] - L, src[0] + L), (src[0] - L, src[1]), (src[0] + L, src[1]), u * 0.45)
    r = u * 5 * a
    p = Path().moveTo(src[0], src[1] - r * 1.6)
    p.quadraticTo(src[0], src[1], src[0] + r, src[1]).quadraticTo(src[0], src[1], src[0], src[1] + r * 1.6)
    p.quadraticTo(src[0], src[1], src[0] - r, src[1]).quadraticTo(src[0], src[1], src[0], src[1] - r * 1.6).close()
    ds.drawPath(p, White.copy(0.85 * a))
    for g, rr in zip((0.55, 0.85, 1.35, 1.7), (2.5, 5.5, 3.5, 8)):
        q = (src[0] + (c[0] - src[0]) * g, src[1] + (c[1] - src[1]) * g)
        ds.drawCircle(col.copy(0.07 * a), u * rr, q)
        ds.drawCircle(col.copy(0.12 * a), u * rr, q, {"width": u * 0.3})

def confetti_rain(ds, t, at, cols, n=70, seed=5):
    if t < at: return
    w, h, u = ds.w, ds.h, _u(ds)
    rnd = random.Random(seed)
    for i in range(n):
        x0 = rnd.random() * w; dl = rnd.random() * 700; spd = h * (0.22 + 0.3 * rnd.random()); spin = 0.5 + rnd.random()
        p = (t - at - dl) / 1000
        if p < 0: continue
        y = -u * 4 + spd * p
        if y > h + u * 4: continue
        x = x0 + math.sin(p * 3 + i) * u * 5
        flip = math.cos(p * 9 * spin + i)
        sw = u * 1.7; sh = u * 1.0 * (0.25 + 0.75 * abs(flip))
        with ds.rotate(p * 300 * spin + i * 37, (x, y)):
            ds.drawRect(cols[i % len(cols)].copy(0.95), (x - sw / 2, y - sh / 2), (sw, sh))

def rain_streaks(ds, t, col, a, n=70, seed=12):
    if a <= 0.01: return
    w, h, u = ds.w, ds.h, _u(ds)
    rnd = random.Random(seed)
    for i in range(n):
        x0 = rnd.random() * w * 1.2; spd = 1.6 + rnd.random() * 0.9; ph = rnd.random()
        p = (t / 1000 * spd + ph) % 1
        y = -h * 0.1 + p * h * 1.2; x = x0 - p * w * 0.12
        L = u * (4 + 5 * rnd.random())
        ds.drawLine(col.copy(a * (0.5 + 0.5 * rnd.random())), (x, y), (x - L * 0.25, y + L), u * 0.18)
