import math, sys
from ds import *
from art import *

PI = math.pi
def sin(x): return math.sin(x)
def cos(x): return math.cos(x)

# ---------- общие рисунки ----------
def glow(ds, c, r, col, a):
    if a <= 0 or r <= 0: return
    ds.drawCircle(Brush.radial([col.copy(a), col.copy(a * 0.35), Transparent], c, r), r, c)

def star4(ds, c, r, col):
    p = Path().moveTo(c[0], c[1] - r)
    p.quadraticTo(c[0], c[1], c[0] + r, c[1]).quadraticTo(c[0], c[1], c[0], c[1] + r)
    p.quadraticTo(c[0], c[1], c[0] - r, c[1]).quadraticTo(c[0], c[1], c[0], c[1] - r).close()
    ds.drawPath(p, col)

def vignette(ds, a):
    w, h = ds.w, ds.h
    ds.drawRect(Brush.radial([Transparent, Transparent, Black.copy(a)], (w / 2, h / 2), max(w, h) * 0.75))

def heart_path(cx, cy, s):
    p = Path().moveTo(cx, cy + s * 0.9)
    p.cubicTo(cx - s * 1.5, cy - s * 0.1, cx - s * 0.7, cy - s * 1.2, cx, cy - s * 0.45)
    p.cubicTo(cx + s * 0.7, cy - s * 1.2, cx + s * 1.5, cy - s * 0.1, cx, cy + s * 0.9)
    return p.close()

def heart_pt(a, s, cx, cy):
    # классическая кривая сердца, a от 0 до 2π
    x = 16 * sin(a) ** 3
    y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
    return (cx + x * s / 16, cy - y * s / 16)

# ---------- 2. Принцесса: 5.0 с, пик 750 ----------
def princess(ds, t, peak=750.):
    w, h = ds.w, ds.h; u = min(w, h) / 100
    c = (w / 2, h * 0.46)
    ds.drawRect(Brush.vertical([Color(0xFF1A0820), Color(0xFF3A1240), Color(0xFF5A1E4E), Color(0xFF2A0A24)]))
    # мягкие «боке»-круги плывут вверх
    rnd = Rnd(5)
    for i in range(26):
        bx = rnd.nextFloat() * w; by0 = rnd.nextFloat(); sp = 0.03 + rnd.nextFloat() * 0.05
        r = u * (3 + rnd.nextFloat() * 9)
        y = ((by0 - t / 1000 * sp) % 1.2 - 0.1) * h
        col = [Color(0xFFFF8AC8), Color(0xFFC9A2FF), Color(0xFFFF6FB0)][i % 3]
        ds.drawCircle(col.copy(0.035 + 0.025 * (i % 3)), r, (bx + sin(t / 1300 + i) * u * 3, y))
    # мерцающие звёздочки
    rnd = Rnd(11)
    for i in range(40):
        sx = rnd.nextFloat() * w; sy = rnd.nextFloat() * h; ph = rnd.nextFloat() * 6.28
        tw = 0.5 + 0.5 * sin(t / 260 + ph)
        a = span(t, 0, 600) * (0.25 + 0.75 * tw)
        star4(ds, (sx, sy), u * (0.6 + 1.0 * tw), Color(0xFFFFF0FA).copy(0.8 * a))
    # маленькие сердечки разлетаются после пика
    rnd = Rnd(3)
    cols = [Color(0xFFFF69B4), Color(0xFFB57EDC), Color(0xFFFFD700), Color(0xFFFFB3D9)]
    for i in range(36):
        ang = rnd.nextFloat() * 2 * PI; spd = 0.35 + rnd.nextFloat() * 0.65; dl = rnd.nextFloat() * 300
        p = lin(t, peak + dl, peak + dl + 2600)
        if p <= 0 or p >= 1: continue
        dist = u * 70 * spd * (1 - (1 - p) ** 2)
        pos = (c[0] + cos(ang) * dist, c[1] + sin(ang) * dist - p * p * u * 25)
        s = u * (1.6 + (i % 4) * 0.9) * (1 - 0.4 * p)
        with ds.rotate(sin(t / 200 + i) * 20, pos):
            ds.drawPath(heart_path(pos[0], pos[1], s), cols[i % 4].copy((1 - p) * 0.95))
    # большое сердце: контур рисуется точками до пика, на пике вспыхивает и заливается
    S = u * 30
    draw = lin(t, 0, peak)
    n = 120
    for k in range(int(n * draw)):
        a = 2 * PI * k / n
        p = heart_pt(a, S, *c)
        ds.drawCircle(Color(0xFFFFB3DE).copy(0.9), u * 0.7, p)
    head = heart_pt(2 * PI * draw, S, *c)
    if t < peak:
        glow(ds, head, u * 8, Color(0xFFFFFFFF), 0.9)
        for k in range(1, 11):
            d_ = draw - k * 0.012
            if d_ <= 0: break
            tp = heart_pt(2 * PI * d_, S, *c)
            j = (sin(k * 2.3 + t / 90) * u * 1.2, cos(k * 1.7 + t / 110) * u * 1.2)
            star4(ds, (tp[0] + j[0], tp[1] + j[1]), u * (1.4 - k * 0.1), Color(0xFFFFE3F3).copy(0.8 * (1 - k / 11)))
    rays = span(t, peak, peak + 700) * (1 - 0.6 * span(t, 3600, 5000))
    rays_from(ds, c, 14, w * 0.9, Color(0xFFFFB3DE), 0.10 * rays, t / 5000, 0.05)
    fillA = span(t, peak, peak + 500)
    pulse = 1 + 0.04 * sin(max(0, t - peak) / 140) * (1 - lin(t, peak, peak + 2500))
    if fillA > 0:
        glow(ds, c, S * 2.2 * pulse, Color(0xFFFF69B4), 0.45 * fillA)
        hp = Path()
        for k in range(n + 1):
            a = 2 * PI * k / n; x, y = heart_pt(a, S * pulse, *c)
            (hp.moveTo if k == 0 else hp.lineTo)(x, y)
        hp.close()
        ds.drawPath(hp, Brush.vertical([Color(0xFFFF9AD5).copy(fillA), Color(0xFFE0479E).copy(fillA)], c[1] - S, c[1] + S))
        ds.drawPath(hp, Color(0xFFFFE3F3).copy(0.9 * fillA), {"width": u * 0.8})
        # блик
        ds.drawOval(White.copy(0.35 * fillA), (c[0] - S * 0.62, c[1] - S * 0.62), (S * 0.42, S * 0.22))
        # три искры кружат вокруг сердца
        for k in range(3):
            a = (t - peak) / 900 + k * 2 * PI / 3
            sp = (c[0] + cos(a) * S * 1.35, c[1] + sin(a) * S * 0.55 - S * 0.05)
            glow(ds, sp, u * 5, Color(0xFFFFE6F6), 0.6 * fillA)
            star4(ds, sp, u * (2.2 + 0.6 * sin(t / 120 + k)), White.copy(fillA))
    shock_ring(ds, t, peak, c, w * 0.85, Color(0xFFFFC2E4))
    shock_ring(ds, t, peak + 880, c, w * 0.6, Color(0xFFFFD6EE), 900, 0.6)
    shock_ring(ds, t, peak + 1760, c, w * 0.5, Color(0xFFFFD6EE), 900, 0.4)
    # вспышка на пике
    fl = 1 - lin(t, peak, peak + 450)
    if t >= peak and fl > 0: ds.drawRect(Color(0xFFFFD1EC).copy(0.35 * fl))
    vignette(ds, 0.55)

# ---------- 3. NASCAR: 5.6 с, пик 2250 ----------
def stock_car(ds, x, y, L, body, stripe, num_bg, blur):
    """Сток-кар сбоку, едет влево. x — нос, y — линия земли, L — длина."""
    H = L * 0.24
    # шлейф размытия
    for k in range(6):
        a = 0.10 * blur * (1 - k / 6)
        ds.drawRoundRect(body.copy(a), (x + L * (0.15 + 0.12 * k), y - H * 0.9), (L * 0.9, H * 0.75), H * 0.3)
    p = Path().moveTo(x, y - H * 0.18)
    p.lineTo(x + L * 0.02, y - H * 0.5).lineTo(x + L * 0.22, y - H * 0.62)
    p.cubicTo(x + L * 0.30, y - H * 1.02, x + L * 0.36, y - H * 1.05, x + L * 0.62, y - H * 1.05)
    p.lineTo(x + L * 0.80, y - H * 0.70).lineTo(x + L * 0.98, y - H * 0.70).lineTo(x + L, y - H * 0.95)
    p.lineTo(x + L * 1.02, y - H * 0.95).lineTo(x + L * 1.0, y - H * 0.25).lineTo(x + L * 0.97, y).lineTo(x + L * 0.03, y).close()
    ds.drawPath(p, Brush.vertical([body.lerp(White, 0.25), body, body.lerp(Black, 0.45)], y - H * 1.05, y))
    # окно
    win = Path().moveTo(x + L * 0.31, y - H * 0.66).cubicTo(x + L * 0.34, y - H * 0.95, x + L * 0.38, y - H * 0.97, x + L * 0.58, y - H * 0.97).lineTo(x + L * 0.70, y - H * 0.66).close()
    ds.drawPath(win, Color(0xFF0A0F18))
    ds.drawLine(White.copy(0.35), (x + L * 0.40, y - H * 0.93), (x + L * 0.36, y - H * 0.7), u_(ds) * 0.5)
    # полоса и номер
    ds.drawRect(stripe, (x + L * 0.04, y - H * 0.42), (L * 0.92, H * 0.12))
    ds.drawCircle(num_bg, H * 0.27, (x + L * 0.52, y - H * 0.38))
    ds.drawCircle(Black.copy(0.85), H * 0.27, (x + L * 0.52, y - H * 0.38), {"width": H * 0.05})
    # колёса
    for wx in (0.2, 0.8):
        cc = (x + L * wx, y - H * 0.02)
        ds.drawCircle(Color(0xFF080808), H * 0.30, cc)
        ds.drawCircle(Color(0xFFCFCFCF).copy(0.85), H * 0.17, cc)
        ds.drawCircle(Color(0xFF202020), H * 0.07, cc)
    # фара-наклейка
    ds.drawOval(Color(0xFFFFF6C8).copy(0.9), (x + L * 0.005, y - H * 0.42), (L * 0.04, H * 0.12))

def u_(ds): return min(ds.w, ds.h) / 100

def nascar(ds, t, peak=2250.):
    w, h = ds.w, ds.h; u = u_(ds)
    lightOn = [span(t, 250 + i * 220, 420 + i * 220) for i in range(4)]
    lit = sum(lightOn) / 4
    # небо
    ds.drawRect(Brush.vertical([Color(0xFF02030A), Color(0xFF0A1024), Color(0xFF151B33)], 0, h * 0.5))
    # дымка над стадионом от прожекторов
    glow(ds, (w / 2, h * 0.32), w * 0.9, Color(0xFF9FB6FF), 0.18 * lit)
    # мачты прожекторов
    for i, fx in enumerate((0.08, 0.36, 0.64, 0.92)):
        x = w * fx; top = h * 0.07
        ds.drawLine(Color(0xFF1C2130), (x, top), (x, h * 0.36), u * 0.9)
        on = lightOn[i]
        flick = 1.0 if t > 420 + i * 220 + 200 else (0.35 if int(t / 50) % 2 else 1.0)
        a = on * flick
        ds.drawRect(Color(0xFF20263A), (x - u * 5, top - u * 3), (u * 10, u * 5))
        for r in range(2):
            for k in range(5):
                lc = (x - u * 4 + k * u * 2, top - u * 2 + r * u * 2)
                ds.drawCircle(Color(0xFFFFFBE8).copy(0.25 + 0.75 * a), u * 0.7, lc)
        glow(ds, (x, top), u * 24, Color(0xFFFFF7DA), 0.75 * a)
        beam = Path().moveTo(x - u * 4, top).lineTo(x + u * 4, top).lineTo(w / 2 + (x - w / 2) * 0.2 + u * 22, h * 0.72).lineTo(w / 2 + (x - w / 2) * 0.2 - u * 22, h * 0.72).close()
        ds.drawPath(beam, Brush.vertical([Color(0xFFFFF7DA).copy(0.16 * a), Transparent], top, h * 0.72))
    search = lit * span(t, peak - 900, peak - 200) * (1 - span(t, 4200, 4900))
    if search > 0:
        for side in (-1, 1):
            o = (w * (0.5 + side * 0.32), h * 0.5)
            a_ = -PI / 2 + sin(t / 700 + side * 1.3) * 0.55
            L = h * 0.62
            e = (o[0] + cos(a_) * L, o[1] + sin(a_) * L)
            nx, ny = -sin(a_) * L * 0.07, cos(a_) * L * 0.07
            ds.drawPath(Path().moveTo(*o).lineTo(e[0] + nx, e[1] + ny).lineTo(e[0] - nx, e[1] - ny).close(), Brush.radial([Color(0xFFE8EEFF).copy(0.16 * search), Transparent], o, L))
    # трибуны ступенями
    for r in range(5):
        y0 = h * (0.30 + r * 0.04)
        ds.drawRect(Color(0xFF0D101C).lerp(Color(0xFF1C2236), r / 5 * lit), (0, y0), (w, h * 0.04))
        ds.drawLine(Color(0xFF2A3150).copy(0.6), (0, y0), (w, y0), u * 0.3)
    rnd = Rnd(77)
    for i in range(160):
        x = rnd.nextFloat() * w; y = h * (0.305 + rnd.nextFloat() * 0.19); ph = rnd.nextFloat() * 6.28
        col = [Color(0xFFE8ECFF), Color(0xFFFF5A5A), Color(0xFF6E9BFF), Color(0xFFFFD86E)][i % 4]
        ds.drawCircle(col.copy((0.18 + 0.25 * lit) * (0.6 + 0.4 * sin(t / 230 + ph))), u * 0.55, (x, y))
    # вспышки фотокамер — чаще к пику
    rnd = Rnd(91)
    for i in range(30):
        at = rnd.nextFloat() * 5200; x = rnd.nextFloat() * w; y = h * (0.31 + rnd.nextFloat() * 0.18)
        near = 1.5 if abs(at - peak) < 700 else 1
        f = 1 - lin(t, at, at + 140 * near)
        if t >= at and f > 0: glow(ds, (x, y), u * 4 * near, White, 0.9 * f)
    # стена с рекламой
    ds.drawRect(Color(0xFF0B0D14), (0, h * 0.50), (w, h * 0.035))
    ads = [Color(0xFFE60019), Color(0xFFFFD100), Color(0xFF1E4FBF), Color(0xFFF2F2F2), Color(0xFF00A651)]
    for k in range(6):
        ds.drawRect(ads[k % 5].copy(0.75 * (0.3 + 0.7 * lit)), (k * w / 6 + u, h * 0.507), (w / 6 - u * 2, h * 0.02))
    # трасса
    ds.drawRect(Brush.vertical([Color(0xFF1B1D24).lerp(Color(0xFF2A2D38), lit), Color(0xFF0E0F13)], h * 0.535, h * 0.84), (0, h * 0.535), (w, h * 0.305))
    ds.drawRect(Color(0xFF090A0D).copy(0.6), (0, h * 0.70), (w, h * 0.05))  # накатанная «колея»
    ds.drawLine(White.copy(0.75), (0, h * 0.58), (w, h * 0.58), u * 0.6)
    ds.drawLine(Color(0xFFFFD100).copy(0.7), (0, h * 0.815), (w, h * 0.815), u * 0.6)
    # газон внутреннего поля с полосами от косилки
    ds.drawRect(Brush.vertical([Color(0xFF0C2414).lerp(Color(0xFF16402A), lit), Color(0xFF07140B)], h * 0.84, h * 0.93), (0, h * 0.84), (w, h * 0.09))
    for k in range(8):
        ds.drawRect(White.copy(0.025 * lit), (k * w / 4 - w / 8, h * 0.84), (w / 8, h * 0.09))
    # бетонный отбойник на переднем плане
    ds.drawRect(Brush.vertical([Color(0xFF9AA0AA).lerp(Color(0xFFD9DDE3), lit), Color(0xFF4A4F58)], h * 0.905, h * 0.964), (0, h * 0.905), (w, h * 0.059))
    for k in range(9):
        ds.drawRect(Color(0xFFE60019).copy(0.85) if k % 2 == 0 else White.copy(0.8), (k * w / 8 - w / 16, h * 0.93), (w / 8, h * 0.012))
    # свет фар перед появлением машины
    pre = span(t, peak - 1100, peak - 250) * (1 - span(t, peak, peak + 300))
    glow(ds, (w * 1.05, h * 0.70), w * 0.6, Color(0xFFFFF3C4), 0.55 * pre)
    # линии скорости по трассе вокруг пика
    sp = span(t, peak - 500, peak) * (1 - span(t, peak + 900, peak + 1600))
    if sp > 0:
        rnd = Rnd(13)
        for i in range(18):
            y = h * (0.6 + rnd.nextFloat() * 0.2); L = w * (0.15 + rnd.nextFloat() * 0.35)
            x = (rnd.nextFloat() - (t / 1000) * (1.5 + rnd.nextFloat())) % 1.4 * w - w * 0.2
            ds.drawLine(White.copy(0.10 * sp), (x, y), (x + L, y), u * 0.4)
    # машины
    def car_pass(t0, dur, y, L, body, stripe, nb):
        p = lin(t, t0, t0 + dur)
        if p <= 0 or p >= 1: return
        x = w + u * 5 - (w + L + u * 10) * p
        ds.drawOval(Black.copy(0.5), (x - L * 0.02, y - L * 0.02), (L * 1.05, L * 0.05))
        stock_car(ds, x, y, L, body, stripe, nb, 1.0)
        # дым из-под колёс
        for k in range(8):
            q = k / 8
            glow(ds, (x + L * (1.0 + q * 0.9), y - L * 0.03), L * (0.12 + 0.18 * q), Color(0xFFBFC4CC), 0.22 * (1 - q))
    car_pass(peak - 380, 900, h * 0.745, w * 0.95, Color(0xFFE60019), Color(0xFFFFFFFF), Color(0xFFFFD100))
    car_pass(peak - 120, 950, h * 0.665, w * 0.72, Color(0xFF1E4FBF), Color(0xFFFFD100), Color(0xFFFFFFFF))
    car_pass(peak + 700, 1000, h * 0.705, w * 0.8, Color(0xFFFFD100), Color(0xFF111111), Color(0xFFE60019))
    # искры на пике
    rnd = Rnd(41)
    for i in range(40):
        st = peak + rnd.nextFloat() * 500; p = lin(t, st, st + 450)
        if p <= 0 or p >= 1: continue
        x0 = w * (0.3 + rnd.nextFloat() * 0.6); vx = u * (20 + rnd.nextFloat() * 40); vy = -u * (8 + rnd.nextFloat() * 18)
        a = (x0 + vx * p, h * 0.76 + vy * p + u * 30 * p * p)
        b = (a[0] - vx * 0.06, a[1] - (vy + u * 60 * p) * 0.06)
        ds.drawLine(Color(0xFFFFC14D).copy(1 - p), a, b, u * 0.5, "round")
    fl = 1 - lin(t, peak, peak + 300)
    if t >= peak and fl > 0: ds.drawRect(White.copy(0.2 * fl))
    # клетчатый флаг машет сверху
    fa = span(t, 3500, 3900)
    if fa > 0:
        fx = w * 0.5; fy = h * 0.16; cell = u * 4.2
        ds.drawLine(Color(0xFFBDBDBD).copy(fa), (fx - u * 18, fy + u * 30), (fx - u * 14, fy - u * 4), u * 0.9)
        for r in range(5):
            for k in range(8):
                wave = sin(t / 90 - k * 0.8) * u * 2.2 * (k / 8)
                x = fx - u * 14 + k * cell; y = fy - u * 4 + r * cell + wave
                col = White if (r + k) % 2 == 0 else Color(0xFF111111)
                ds.drawRect(col.copy(fa), (x, y), (cell + 0.6, cell + 0.6))
    for i in (1, 2): flare_at(ds, (w * (0.08, 0.36, 0.64, 0.92)[i], h * 0.07), Color(0xFFFFF3D0), 0.45 * lightOn[i])
    confetti_rain(ds, t, 3600, [White, Color(0xFFE60019), Color(0xFFFFD100), Color(0xFF1E4FBF)], 80, 5)
    # финальная шахматная шторка
    wipe = lin(t, 4900, 5600)
    if wipe > 0:
        cell = w / 8
        for r in range(int(h / cell) + 2):
            for k in range(8):
                d_ = (k + r) / (8 + h / cell)
                s_ = max(0, min(1, (wipe * 1.8 - d_)))
                if s_ <= 0: continue
                sz = cell * s_
                col = White if (r + k) % 2 == 0 else Color(0xFF0A0A0A)
                ds.drawRect(col, (k * cell + (cell - sz) / 2, r * cell + (cell - sz) / 2), (sz, sz))
    # полосы NASCAR снизу
    bh = h * 0.012
    ds.drawRect(White, (0, h - bh * 3), (w, bh)); ds.drawRect(Color(0xFFE60019), (0, h - bh * 2), (w, bh)); ds.drawRect(Color(0xFF1E4FBF), (0, h - bh), (w, bh))
    vignette(ds, 0.5)


# ---------- 4. GT Racing: 3.2 с, пик 750 ----------
def carbon(ds, dim):
    w, h = ds.w, ds.h; u = u_(ds); cell = u * 2.2
    ds.drawRect(Color(0xFF0B0B0D))
    y = 0.0; r = 0
    while y < h:
        x = 0.0; k = 0
        while x < w:
            a = 0.06 if (r + k) % 2 == 0 else 0.025
            ds.drawRect(White.copy(a * dim), (x, y), (cell - 0.6, cell - 0.6))
            x += cell; k += 1
        y += cell; r += 1
    ds.drawRect(Brush.linear([Transparent, White.copy(0.06 * dim), Transparent], (0, 0), (w, h)))

def tach_rpm(t, peak):
    # обороты 0..9: разгон до отсечки на пике, переключение передачи, снова разгон, сброс в конце
    if t < peak: return 1 + 7.6 * ease(t / peak) ** 1.4
    if t < peak + 160: return 8.6 + 0.25 * sin((t - peak) / 18)
    if t < peak + 420: return 8.6 - 4.6 * ease((t - peak - 160) / 260)
    if t < 2500: return 4 + 3.8 * ease((t - peak - 420) / (2500 - peak - 420))
    return 7.8 - 6.8 * ease((t - 2500) / 700)

def gt(ds, t, peak=750.):
    w, h = ds.w, ds.h; u = u_(ds)
    carbon(ds, span(t, 0, 300))
    # диагональные гоночные полосы влетают слева
    for k, (col, off) in enumerate(((Color(0xFFE60019), 0.0), (White, 0.075), (Color(0xFFE60019), 0.13))):
        p = span(t, k * 60, 420 + k * 60)
        bw = w * (0.06 if k != 1 else 0.025)
        x0 = -w * 0.6 + (w * 0.95 + off * w) * p
        band = Path().moveTo(x0, h).lineTo(x0 + bw, h).lineTo(x0 + bw + h * 0.45, 0).lineTo(x0 + h * 0.45, 0).close()
        ds.drawPath(band, col.copy(0.9))
    c = (w / 2, h * 0.48); R = u * 38
    appear = span(t, 80, 450)
    # шевроны «>>>» бегут сверху и снизу, быстрее после пика
    run = t / 1000 * (0.6 if t < peak else 1.4)
    for row, yy in enumerate((h * 0.17, h * 0.83)):
        for k in range(9):
            x = ((k / 8 + run * (1 if row == 0 else -1)) % 1.125) * w * 1.2 - w * 0.1
            a = appear * (0.18 + 0.5 * (1 if (k + int(t / 120)) % 3 == 0 and t > peak else 0))
            ch = Path().moveTo(x, yy - u * 3).lineTo(x + u * 3, yy).lineTo(x, yy + u * 3)
            ds.drawPath(ch, (Color(0xFFE60019) if row == 0 else White).copy(a), {"width": u * 1.1, "cap": "round"})
    rpm = tach_rpm(t, peak)
    redline = rpm > 7.9
    # корпус прибора
    glow(ds, c, R * 1.7, Color(0xFFE60019) if redline else Color(0xFF6E8CFF), (0.35 if redline else 0.15) * appear)
    ds.drawCircle(Brush.radial([Color(0xFF1A1B20), Color(0xFF0A0A0C)], c, R * 1.08), R * 1.08 * appear, c)
    ds.drawCircle(Brush.linear([Color(0xFFDADDE2), Color(0xFF4A4D55), Color(0xFFDADDE2)], (c[0] - R, c[1] - R), (c[0] + R, c[1] + R)), R * 1.08 * appear, c, {"width": u * 1.6})
    if appear < 0.05: return
    # шкала: 270° от -225° до 45°
    def ang(v): return math.radians(135 + 270 * v / 9)
    for i in range(0, 91):
        v = i / 10; a = ang(v); major = i % 10 == 0; mid = i % 5 == 0
        if not (major or mid or i % 2 == 0): continue
        r0 = R * (0.78 if major else 0.86 if mid else 0.9); r1 = R * 0.96
        col = Color(0xFFFF2A2A) if v >= 7.5 else White
        lit = 1.0 if v <= rpm else 0.45
        ds.drawLine(col.copy(lit * appear), (c[0] + cos(a) * r0, c[1] + sin(a) * r0), (c[0] + cos(a) * r1, c[1] + sin(a) * r1), u * (0.9 if major else 0.45))
    # красная зона дугой
    rz = Path()
    for k in range(21):
        a = ang(7.5 + 1.5 * k / 20); (rz.moveTo if k == 0 else rz.lineTo)(c[0] + cos(a) * R * 1.0, c[1] + sin(a) * R * 1.0)
    ds.drawPath(rz, Color(0xFFE60019).copy(0.9 * appear), {"width": u * 1.6})
    # подсветка пройденной дуги
    arcp = Path()
    for k in range(41):
        a = ang(rpm * k / 40); (arcp.moveTo if k == 0 else arcp.lineTo)(c[0] + cos(a) * R * 0.72, c[1] + sin(a) * R * 0.72)
    ds.drawPath(arcp, (Color(0xFFFF2A2A) if redline else Color(0xFF6EC1FF)).copy(0.55 * appear), {"width": u * 2.2, "cap": "round"})
    for k in range(1, 6):
        ga = ang(tach_rpm(max(0, t - k * 28), peak))
        gtip = (c[0] + cos(ga) * R * 0.95, c[1] + sin(ga) * R * 0.95)
        ds.drawLine(Color(0xFFFF3B1F).copy(0.16 * (1 - k / 6) * appear), c, gtip, u * 1.1, "round")
    # стрелка
    a = ang(rpm)
    tip = (c[0] + cos(a) * R * 0.95, c[1] + sin(a) * R * 0.95)
    tail = (c[0] - cos(a) * R * 0.18, c[1] - sin(a) * R * 0.18)
    ds.drawLine(Color(0xFFFF3B1F).copy(appear), tail, tip, u * 1.3, "round")
    glow(ds, tip, u * 5, Color(0xFFFF6A3D), 0.7 * appear)
    ds.drawCircle(Color(0xFF2A2C33), u * 4, c); ds.drawCircle(Color(0xFF8A8F99), u * 4, c, {"width": u * 0.6})
    # лампы переключения передач над прибором
    n = 10
    for k in range(n):
        x = c[0] - u * 27 + k * u * 6; y = c[1] - R * 1.42
        thr = 3 + 5 * k / (n - 1)
        on = rpm >= thr
        col = Color(0xFF2BFF6A) if k < 4 else Color(0xFFFFD000) if k < 7 else Color(0xFFFF2A2A)
        if redline and int(t / 70) % 2 == 0: col = Color(0xFF3A7BFF)
        ds.drawCircle(Color(0xFF15161A).copy(appear), u * 2.2, (x, y))
        if on:
            glow(ds, (x, y), u * 5.5, col, 0.7 * appear)
            ds.drawCircle(col.copy(appear), u * 1.6, (x, y))
    # «искра» передачи: цифра заменена квадратиком-индикатором
    gear = 2 if t < peak + 300 else 3
    for g in range(gear):
        ds.drawRoundRect(Color(0xFFFF3B1F).copy(appear), (c[0] - u * 6 + g * u * 4.5, c[1] + R * 0.42), (u * 3.2, u * 1.6), u * 0.5)
    shock_ring(ds, t, peak + 160, c, w * 0.9, White, 650, 0.8)
    if redline:
        pulse = 1 if int(t / 70) % 2 == 0 else 0.55
        ds.drawRect(Brush.radial([Transparent, Color(0xFFE60019).copy(0.28 * pulse)], (w / 2, h / 2), max(w, h) * 0.7))
    # линии скорости после пика
    sp = span(t, peak, peak + 200) * (1 - span(t, 2600, 3100))
    rnd = Rnd(8)
    for i in range(26):
        y = rnd.nextFloat() * h; L = w * (0.2 + rnd.nextFloat() * 0.5)
        x = (rnd.nextFloat() + t / 1000 * (1.2 + rnd.nextFloat())) % 1.5 * w - w * 0.4
        ds.drawLine(White.copy(0.12 * sp), (x, y), (x + L, y), u * 0.35)
    fl = 1 - lin(t, peak, peak + 260)
    if t >= peak and fl > 0: ds.drawRect(White.copy(0.25 * fl))
    vignette(ds, 0.6)


# ---------- 5. Night Rider: 7.8 с, пик 2250 (звук реактивного самолёта) ----------
def jet_rear(ds, cx, cy, S, burner, t):
    """Истребитель сзади-сверху; S — размах крыла. burner 0..1 — сила форсажа."""
    u = u_(ds)
    body = Color(0xFF1B2333); edge = Color(0xFF55688A)
    # пламя форсажа: конусы вниз (к нам) с «бриллиантами»
    for side in (-1, 1):
        nx = cx + side * S * 0.07; ny = cy + S * 0.10
        L = S * (0.15 + 0.75 * burner) * (1 + 0.05 * sin(t / 23 + side))
        if burner > 0.02:
            glow(ds, (nx, ny + L * 0.3), S * 0.35 * (0.4 + burner), Color(0xFF1E6BFF), 0.45 * burner)
            for k, (col, wk) in enumerate(((Color(0xFF1240FF), 1.0), (Color(0xFF2E9BFF), 0.7), (Color(0xFF9AE6FF), 0.4), (White, 0.18))):
                fw = S * 0.045 * wk
                fl = Path().moveTo(nx - fw, ny).cubicTo(nx - fw, ny + L * 0.5, nx - fw * 0.3, ny + L * (0.9 - 0.1 * k), nx, ny + L * (1 - 0.15 * k))
                fl.cubicTo(nx + fw * 0.3, ny + L * (0.9 - 0.1 * k), nx + fw, ny + L * 0.5, nx + fw, ny).close()
                ds.drawPath(fl, col.copy((0.55 + 0.1 * k) * min(1, burner * 1.6)))
            for k in range(4):
                dy = ny + L * (0.18 + 0.2 * k)
                ds.drawOval(White.copy(0.55 * burner * (1 - k / 4)), (nx - S * 0.018, dy - S * 0.01), (S * 0.036, S * 0.02))
        ds.drawCircle(Color(0xFF9AE6FF).lerp(White, burner), S * 0.035, (nx, ny))
    # крыло
    wing = Path().moveTo(cx - S * 0.5, cy + S * 0.04).lineTo(cx - S * 0.12, cy - S * 0.08).lineTo(cx + S * 0.12, cy - S * 0.08).lineTo(cx + S * 0.5, cy + S * 0.04)
    wing.lineTo(cx + S * 0.48, cy + S * 0.07).lineTo(cx + S * 0.1, cy + S * 0.03).lineTo(cx - S * 0.1, cy + S * 0.03).lineTo(cx - S * 0.48, cy + S * 0.07).close()
    ds.drawPath(wing, Brush.vertical([edge, body], cy - S * 0.08, cy + S * 0.07))
    # стабилизаторы-кили (развалены в стороны)
    for side in (-1, 1):
        fin = Path().moveTo(cx + side * S * 0.08, cy - S * 0.02).lineTo(cx + side * S * 0.16, cy - S * 0.24).lineTo(cx + side * S * 0.19, cy - S * 0.23).lineTo(cx + side * S * 0.13, cy + S * 0.02).close()
        ds.drawPath(fin, Brush.vertical([edge, body], cy - S * 0.24, cy))
        ds.drawLine(Color(0xFF3DDCFF).copy(0.5), (cx + side * S * 0.16, cy - S * 0.24), (cx + side * S * 0.19, cy - S * 0.23), u * 0.4)
    # фюзеляж
    ds.drawOval(Brush.vertical([edge, body], cy - S * 0.16, cy + S * 0.12), (cx - S * 0.11, cy - S * 0.16), (S * 0.22, S * 0.28))
    ds.drawOval(Color(0xFF7FD8FF).copy(0.35), (cx - S * 0.04, cy - S * 0.15), (S * 0.08, S * 0.08))
    # огни на законцовках крыла
    blink = 1 if int(t / 500) % 2 == 0 else 0.3
    glow(ds, (cx - S * 0.5, cy + S * 0.05), S * 0.06, Color(0xFFFF3030), 0.8 * blink)
    glow(ds, (cx + S * 0.5, cy + S * 0.05), S * 0.06, Color(0xFF30FF7A), 0.8 * blink)

def night(ds, t, peak=2250.):
    w, h = ds.w, ds.h; u = u_(ds)
    hz = h * 0.56
    ds.drawRect(Brush.vertical([Color(0xFF01030A), Color(0xFF061232), Color(0xFF0E2350)], 0, hz))
    glow(ds, (w * 0.82, h * 0.12), w * 0.45, Color(0xFF9DB8FF), 0.20)
    ds.drawCircle(Color(0xFFE6EEFF).copy(0.85), u * 5, (w * 0.82, h * 0.12))
    rnd = Rnd(19)
    for i in range(90):
        sx = rnd.nextFloat() * w; sy = rnd.nextFloat() * hz * 0.95; ph = rnd.nextFloat() * 6.28
        ds.drawCircle(White.copy(0.35 + 0.35 * sin(t / 400 + ph)), u * (0.25 + rnd.nextFloat() * 0.35), (sx, sy))
    # огни города на горизонте
    rnd = Rnd(23)
    for i in range(70):
        x = rnd.nextFloat() * w; y = hz - rnd.nextFloat() * h * 0.02
        ds.drawCircle(Color(0xFFFFC66E).copy(0.5), u * 0.35, (x, y))
    glow(ds, (w / 2, hz), w * 0.8, Color(0xFF2E5BFF), 0.18)
    # земля и полоса
    ds.drawRect(Brush.vertical([Color(0xFF050A16), Color(0xFF02040A)], hz, h), (0, hz), (w, h - hz))
    vx = w / 2
    def rp(z, side):  # z: 0 у горизонта .. 1 у нас; side -1/1 край полосы
        y = hz + (h - hz) * z * z
        half = w * 0.015 + w * 0.6 * z * z
        return (vx + side * half, y)
    strip = Path().moveTo(*rp(0, -1)).lineTo(*rp(0, 1)).lineTo(*rp(1.1, 1)).lineTo(*rp(1.1, -1)).close()
    ds.drawPath(strip, Brush.vertical([Color(0xFF0C1424), Color(0xFF1A2438)], hz, h))
    # разметка: осевая и «зебра» порога
    for k in range(12):
        z0 = (k + 0.2) / 12; z1 = (k + 0.65) / 12
        a0 = rp(z0, -0.012); b0 = rp(z0, 0.012); a1 = rp(z1, 0.012); b1 = rp(z1, -0.012)
        ds.drawPath(Path().moveTo(*a0).lineTo(*b0).lineTo(*a1).lineTo(*b1).close(), White.copy(0.28))
    for k in range(8):
        sd = -0.85 + k * 0.24 + (0.1 if k >= 4 else 0)
        z0, z1 = 0.88, 0.97
        ds.drawPath(Path().moveTo(*rp(z0, sd)).lineTo(*rp(z0, sd + 0.13)).lineTo(*rp(z1, sd + 0.13)).lineTo(*rp(z1, sd)).close(), White.copy(0.3))
    # синие огни рулёжки в стороне
    for k in range(14):
        z = k / 13
        for side in (-1, 1):
            ds.drawCircle(Color(0xFF3D7BFF).copy(0.55 * span(t, 300 + z * 600, 500 + z * 600)), u * (0.2 + 0.5 * z), rp(z, side * 1.9))
    # бегущие огни подхода и боковые огни: зажигаются от горизонта к нам
    for k in range(28):
        z = k / 27
        on = span(t, 100 + z * 600, 250 + z * 600)
        chase = 1 if int((t / 60 - k)) % 10 == 0 else 0
        for side in (-1, 1):
            p = rp(z, side * 1.06)
            r = u * (0.25 + 1.0 * z)
            glow(ds, p, r * 3.5, Color(0xFFBFD6FF), 0.35 * on)
            ds.drawCircle(Color(0xFFE6F0FF).copy(on), r, p)
        cl = rp(z, 0)
        if k % 2 == 0: ds.drawCircle((Color(0xFF3DDCFF) if chase else White).copy(0.6 * on), u * (0.2 + 0.6 * z), cl)
    # самолёт: стоит, раскручивает двигатели, на пике уходит вдаль и взлетает
    burner = span(t, 900, 1700) * 0.45 + span(t, peak - 300, peak) * 0.55
    burner *= 1 - 0.6 * span(t, 4200, 6500)
    go = span(t, peak - 100, 4600) ** 1.6
    lift = span(t, 3300, 5200)
    S = w * (0.95 - 0.88 * go)
    cy = h * 0.80 - (h * 0.80 - hz) * go - h * 0.30 * lift
    cx = w / 2 + sin(t / 900) * u * 1.5 * (1 - go) + w * 0.22 * lift ** 1.3
    # тряска и марево на пике
    q = (1 - lin(t, peak, peak + 600)) * u * 1.2 if t >= peak else 0
    if S > u * 2:
        with ds.translate(sin(t / 11) * q, cos(t / 13) * q):
            # след после взлёта
            if lift > 0:
                tr = Path().moveTo(w / 2, hz).quadraticTo(w / 2 + (cx - w / 2) * 0.2, hz - (hz - cy) * 0.6, cx, cy)
                ds.drawPath(tr, Color(0xFF3DA5FF).copy(0.35 * (1 - span(t, 5200, 7600))), {"width": u * 1.2, "cap": "round"})
            glow(ds, (cx, cy + S * 0.22), S * 0.7, Color(0xFF1E6BFF), 0.22 * burner)
            jet_rear(ds, cx, cy, S, burner, t)
            shock_ring(ds, t, peak, (cx, cy), w * 0.95, Color(0xFFA8DCFF), 800, 0.9)
    fl = 1 - lin(t, peak, peak + 500)
    if t >= peak and fl > 0: ds.drawRect(Color(0xFF3DA5FF).copy(0.25 * fl))
    flare_at(ds, (cx, cy + S * 0.1), Color(0xFF6EB8FF), 0.6 * lift * (1 - span(t, 5600, 7400)))
    flare_at(ds, (w * 0.82, h * 0.12), Color(0xFF9DB8FF), 0.3)
    vignette(ds, 0.6)


# ---------- 6. Street Underground: 3.3 с, пик 500 (палитра Most Wanted) ----------
def street(ds, t, peak=500.):
    w, h = ds.w, ds.h; u = u_(ds)
    amber = Color(0xFFFFB000); haze = Color(0xFFD9A441)
    q = (1 - lin(t, peak, peak + 450)) * u * 2.2 if t >= peak else 0
    hz = h * 0.47; vx = w * 0.5
    lampsOn = span(t, 0, 380)
    with ds.translate(sin(t / 14) * q, cos(t / 17) * q):
        ds.drawRect(Brush.vertical([Color(0xFF0B0904), Color(0xFF2A2210), Color(0xFF4A3A16)], 0, hz))
        glow(ds, (vx, hz), w * 0.9, haze, 0.35 * lampsOn)
        # дома по сторонам в перспективе с окнами
        def gy(z): return hz + (h - hz) * z * z
        for side in (-1, 1):
            for k in range(7):
                z0 = k / 7; z1 = (k + 0.9) / 7
                def px(z): return vx + side * (w * 0.05 + w * 0.95 * z * z)
                H = h * (0.20 + 0.18 * ((k * 5 + (side > 0) * 3) % 4) / 3)
                def top(z): return gy(z) - H * (0.15 + 0.85 * z * z) * 2.2
                bld = Path().moveTo(px(z0), gy(z0)).lineTo(px(z0), top(z0)).lineTo(px(z1), top(z1)).lineTo(px(z1), gy(z1)).close()
                ds.drawPath(bld, Color(0xFF0B0905).lerp(Color(0xFF1A140B), z0))
                rnd = Rnd(k * 10 + (side > 0))
                for wv in range(14):
                    zz = z0 + (z1 - z0) * rnd.nextFloat(); fx = px(zz)
                    fy = gy(zz) - (gy(zz) - top(zz)) * (0.12 + 0.8 * rnd.nextFloat())
                    if rnd.nextFloat() < 0.55:
                        ds.drawRect(amber.copy(0.2 + 0.45 * lampsOn * rnd.nextFloat()), (fx - u * 0.6 * zz, fy), (u * (0.4 + 2.2 * zz), u * (0.4 + 1.8 * zz)))
        # мокрый асфальт
        ds.drawRect(Brush.vertical([Color(0xFF1A140A), Color(0xFF070604)], hz, h), (0, hz), (w, h - hz))
        def rp(z, sd):
            return (vx + sd * (w * 0.03 + w * 0.7 * z * z), hz + (h - hz) * z * z)
        road = Path().moveTo(*rp(0, -1)).lineTo(*rp(0, 1)).lineTo(*rp(1.05, 1)).lineTo(*rp(1.05, -1)).close()
        ds.drawPath(road, Color(0xFF14110B))
        # фонари: ряды по обе стороны, свет и отражения на мокрой дороге
        for k in range(9):
            z = (k + 1) / 9
            for side in (-1, 1):
                b = rp(z, side * 1.08)
                top_ = (b[0] - side * u * 5 * z, b[1] - h * 0.9 * z * z - u * 2)
                ds.drawLine(Color(0xFF2A2214), b, top_, u * (0.3 + 0.9 * z))
                fl = lampsOn if k % 4 != 1 else lampsOn * (0.6 + 0.4 * (1 if int(t / 90 + k) % 3 else 0))
                glow(ds, top_, u * (3 + 14 * z), amber, 0.8 * fl)
                ds.drawCircle(Color(0xFFFFE2A0).copy(fl), u * (0.4 + 1.1 * z), top_)
                if z > 0.55: star4(ds, top_, u * (1.5 + 3 * z) * fl, Color(0xFFFFF0C8).copy(0.7 * fl))
                # отражение — вытянутое пятно вниз по асфальту
                ry = b[1] + (b[1] - hz) * 0.08
                ds.drawOval(amber.copy(0.16 * fl), (b[0] - u * (1 + 2 * z), ry), (u * (2 + 4 * z), u * (3 + 22 * z)))
        # разметка
        for k in range(10):
            z0 = (k + 0.1) / 10 + (t / 1000 * 0.9) % 0.1; z1 = z0 + 0.04
            if z1 > 1.05: continue
            ds.drawPath(Path().moveTo(*rp(z0, -0.015)).lineTo(*rp(z0, 0.015)).lineTo(*rp(z1, 0.015)).lineTo(*rp(z1, -0.015)).close(), Color(0xFFFFD27A).copy(0.45))
        # машина уезжает вдаль: стоп-сигналы и красные следы
        go = span(t, peak - 120, 2400)
        z = 0.8 - 0.74 * go
        if z > 0.05:
            c = rp(z, 0); cw = w * 0.36 * z * z + u * 2
            for k in range(1, 7):
                zz = min(1.05, z + 0.05 * k * (0.3 + go))
                tp = rp(zz, 0); tw = w * 0.36 * zz * zz
                for sd in (-0.38, 0.38):
                    ds.drawLine(Color(0xFFFF2020).copy(0.10 * (1 - k / 7)), (c[0] + sd * cw, c[1]), (tp[0] + sd * tw, tp[1]), u * 0.6 + tw * 0.04)
            body = Path().moveTo(c[0] - cw * 0.5, c[1]).lineTo(c[0] - cw * 0.46, c[1] - cw * 0.26).lineTo(c[0] - cw * 0.3, c[1] - cw * 0.44)
            body.lineTo(c[0] + cw * 0.3, c[1] - cw * 0.44).lineTo(c[0] + cw * 0.46, c[1] - cw * 0.26).lineTo(c[0] + cw * 0.5, c[1]).close()
            ds.drawPath(body, Color(0xFF0B0B0D))
            ds.drawRect(Color(0xFF2A2F3A).copy(0.8), (c[0] - cw * 0.26, c[1] - cw * 0.40), (cw * 0.52, cw * 0.12))
            for sd in (-1, 1):
                tl = (c[0] + sd * cw * 0.36, c[1] - cw * 0.2)
                glow(ds, tl, cw * 0.35, Color(0xFFFF1A1A), 0.8)
                ds.drawRoundRect(Color(0xFFFF4040), (tl[0] - cw * 0.08, tl[1] - cw * 0.03), (cw * 0.16, cw * 0.06), cw * 0.02)
        # полицейские мигалки по краям после пика
        pol = span(t, peak, peak + 150) * (1 - span(t, 2700, 3300) * 0.7)
        if pol > 0:
            ph = int(t / 110) % 4
            red = ph in (0, 1); blue = ph in (2, 3)
            glow(ds, (-w * 0.05, h * 0.62), w * 0.75, Color(0xFFFF1E2E), 0.55 * pol * (1 if red else 0.15))
            glow(ds, (w * 1.05, h * 0.62), w * 0.75, Color(0xFF2E5BFF), 0.55 * pol * (1 if blue else 0.15))
            ds.drawRect(Brush.vertical([Transparent, Color(0xFFFF1E2E).copy(0.20 * pol * (1 if red else 0.15)), Transparent], hz, h), (0, hz), (w * 0.35, h - hz))
            ds.drawRect(Brush.vertical([Transparent, Color(0xFF2E5BFF).copy(0.20 * pol * (1 if blue else 0.15)), Transparent], hz, h), (w * 0.65, hz), (w * 0.35, h - hz))
        # толпа силуэтами по низу
        for row in range(2):
            rnd = Rnd(21 + row)
            for k in range(14):
                bx = rnd.nextFloat(); side = -1 if k % 2 == 0 else 1
                x = (bx * 0.32 if side < 0 else 1 - bx * 0.32) * w
                hh = h * (0.08 + rnd.nextFloat() * 0.03) * (1 + 0.4 * row)
                jump = max(0, sin(t / 160 + k)) * u * 1.5 * span(t, peak, peak + 200)
                y = h + h * 0.01 - jump
                col = Color(0xFF050403).copy(0.97 if row else 0.8)
                ds.drawRoundRect(col, (x - hh * 0.28, y - hh), (hh * 0.56, hh), hh * 0.2)
                ds.drawCircle(col, hh * 0.17, (x, y - hh * 1.1))
                if (k + row) % 3 == 0:
                    arm = (x + side * -hh * 0.1, y - hh * 0.85)
                    ds.drawLine(col, arm, (arm[0] - side * hh * 0.25, arm[1] - hh * 0.55 - jump), hh * 0.12, "round")
    fl = 1 - lin(t, peak, peak + 250)
    if t >= peak and fl > 0: ds.drawRect(amber.copy(0.25 * fl))
    rain_streaks(ds, t, Color(0xFFFFD27A), 0.22 * lampsOn, 80, 12)
    # зеленовато-янтарный фильтр Most Wanted
    ds.drawRect(Color(0xFF6B5A1A).copy(0.12))
    vignette(ds, 0.65)


# ---------- 7. Rally Gravel: 7.1 с, пик 2500 ----------
def pine(ds, x, base, H, col):
    W = H * 0.32
    p = Path().moveTo(x, base - H)
    for k in range(4):
        y = base - H + H * (k + 1) / 4.2; ww = W * (0.35 + 0.65 * (k + 1) / 4)
        p.lineTo(x + ww, y).lineTo(x + ww * 0.45, y)
    p.lineTo(x + W * 0.12, base).lineTo(x - W * 0.12, base)
    for k in range(3, -1, -1):
        y = base - H + H * (k + 1) / 4.2; ww = W * (0.35 + 0.65 * (k + 1) / 4)
        p.lineTo(x - ww * 0.45, y).lineTo(x - ww, y)
    ds.drawPath(p.close(), col)

def rally_car_front(ds, cx, cy, S, lamps):
    """Раллийная машина спереди: S — ширина."""
    u = u_(ds)
    body = Path().moveTo(cx - S * 0.5, cy).lineTo(cx - S * 0.48, cy - S * 0.22).lineTo(cx - S * 0.32, cy - S * 0.30)
    body.lineTo(cx - S * 0.24, cy - S * 0.52).lineTo(cx + S * 0.24, cy - S * 0.52).lineTo(cx + S * 0.32, cy - S * 0.30)
    body.lineTo(cx + S * 0.48, cy - S * 0.22).lineTo(cx + S * 0.5, cy).close()
    ds.drawPath(body, Brush.vertical([Color(0xFFF2F2F2), Color(0xFF1E64C8), Color(0xFF0E2E66)], cy - S * 0.52, cy))
    ds.drawPath(Path().moveTo(cx - S * 0.21, cy - S * 0.33).lineTo(cx - S * 0.17, cy - S * 0.49).lineTo(cx + S * 0.17, cy - S * 0.49).lineTo(cx + S * 0.21, cy - S * 0.33).close(), Color(0xFF101820))
    ds.drawRect(Color(0xFFFFD100), (cx - S * 0.5, cy - S * 0.16), (S, S * 0.04))
    ds.drawRect(Color(0xFF0A0A0A), (cx - S * 0.2, cy - S * 0.12), (S * 0.4, S * 0.06))  # решётка
    ds.drawRect(White, (cx - S * 0.09, cy - S * 0.06), (S * 0.18, S * 0.05))  # номер
    for sd in (-1, 1):
        ds.drawRect(Color(0xFF0A0A0A), (cx + sd * S * 0.42 - S * 0.06, cy - S * 0.02), (S * 0.12, S * 0.12))  # колёса
        ds.drawOval(Color(0xFFFFF4D0).copy(0.9), (cx + sd * S * 0.37 - S * 0.06, cy - S * 0.24), (S * 0.12, S * 0.05))
    # люстра из 4 фар на капоте
    for k in range(4):
        lx = cx - S * 0.27 + k * S * 0.18; ly = cy - S * 0.26
        glow(ds, (lx, ly), S * 0.5, Color(0xFFFFF2C0), 0.5 * lamps)
        ds.drawCircle(Color(0xFF111111), S * 0.07, (lx, ly))
        ds.drawCircle(Color(0xFFFFFBE6).copy(0.3 + 0.7 * lamps), S * 0.055, (lx, ly))

def rally(ds, t, peak=2500.):
    w, h = ds.w, ds.h; u = u_(ds)
    def quake(a, at): return (1 - lin(t, at, at + 600)) * a if at <= t < at + 600 else 0
    q = (quake(u * 2.5, peak) + quake(u * 1.2, peak + 700)) 
    hz = h * 0.5; vx = w * 0.5
    with ds.translate(sin(t / 16) * q, cos(t / 13) * q):
        ds.drawRect(Brush.vertical([Color(0xFF3A2A1C), Color(0xFFC98A4B), Color(0xFFF2C27A)], 0, hz))
        sun = (w * 0.68, h * 0.30)
        glow(ds, sun, w * 0.6, Color(0xFFFFE2A0), 0.55)
        ds.drawCircle(Color(0xFFFFF4D6), u * 6, sun)
        ground = hz + h * 0.03
        def rx(z): return vx + sin(z * 3.2 + 0.6) * w * 0.16 * (1 - z * 0.5)
        def ry(z): return ground + (h - ground + h * 0.06) * z * z
        def rw(z): return w * 0.012 + w * 0.55 * z * z
        def forest(li, sc, col, n, seed, base):
            rnd = Rnd(seed)
            for k in range(n):
                x = (k + rnd.nextFloat() * 0.6) / n * w
                zb = ((base - ground) / (h - ground + h * 0.06)) ** 0.5
                gap = rw(min(zb, 1)) * 1.3 + w * 0.02
                cxr = rx(min(zb, 1))
                if abs(x - cxr) < gap: x = cxr + gap * (1 if x > cxr else -1) + (x - cxr) * 0.3
                H = h * (0.10 + 0.22 * sc) * (0.7 + 0.5 * rnd.nextFloat())
                pine(ds, x, base, H, col)
        # дальний лес в дымке
        forest(0, 0.15, Color(0xFF8A6A48), 26, 3, ground + h * 0.005)
        ds.drawRect(Brush.vertical([Color(0xFFF2C27A).copy(0.0), Color(0xFFE8B06A).copy(0.35)], hz - h * 0.08, ground), (0, hz - h * 0.08), (w, h * 0.11))
        forest(1, 0.30, Color(0xFF4E3A26), 20, 7, ground + h * 0.02)
        # лесная подстилка и гравийная дорога змейкой к нам
        ds.drawRect(Brush.vertical([Color(0xFF3A2814), Color(0xFF120C06)], ground + h * 0.02, h), (0, ground + h * 0.02), (w, h))
        road = Path(); N = 28
        for k in range(N + 1):
            z = k / N; (road.moveTo if k == 0 else road.lineTo)(rx(z) - rw(z), ry(z))
        for k in range(N, -1, -1):
            z = k / N; road.lineTo(rx(z) + rw(z), ry(z))
        ds.drawPath(road.close(), Brush.vertical([Color(0xFFB08A60), Color(0xFF6A4E30)], ground, h))
        rnd = Rnd(55)
        for k in range(140):
            z = rnd.nextFloat() ** 0.7; x = rx(z) + (rnd.nextFloat() * 2 - 1) * rw(z)
            ds.drawCircle(Color(0xFF3A2A18).copy(0.5), u * (0.15 + 0.6 * z), (x, ry(z)))
        # ближний лес по краям
        forest(2, 0.55, Color(0xFF22180E), 12, 11, ground + h * 0.10)
        forest(3, 1.0, Color(0xFF0E0A06), 8, 17, ground + h * 0.28)
        # лучи солнца сквозь лес
        for k in range(6):
            a = -0.9 + k * 0.32 + sin(t / 2000 + k) * 0.03
            L = h * 0.9
            ray = Path().moveTo(*sun).lineTo(sun[0] + math.cos(a + 1.9) * L - u * 6, sun[1] + math.sin(a + 1.9) * L).lineTo(sun[0] + math.cos(a + 1.9) * L + u * 6, sun[1] + math.sin(a + 1.9) * L).close()
            ds.drawPath(ray, Brush.radial([Color(0xFFFFE9B8).copy(0.16), Transparent], sun, L))
        # машина: из глубины к нам, на пике прыжок и вспышка
        cp = lin(t, peak - 1400, peak + 150)
        if 0 < cp < 1:
            z = 0.05 + 0.95 * cp ** 2.2
            jump = sin(PI * lin(t, peak - 500, peak + 150)) * h * 0.08 * z
            S = rw(z) * 1.25
            dust0 = (rx(z), ry(z))
            for k in range(10):
                glow(ds, (dust0[0] + (k - 4.5) * S * 0.12, dust0[1] - S * 0.05 * k), S * (0.25 + 0.05 * k), Color(0xFFD8B28A), 0.25)
            rally_car_front(ds, rx(z), ry(z) - jump, S, span(t, peak - 1400, peak - 1000))
        with ds.scale(1, 0.32, (rx(0.95), h * 0.86)):
            shock_ring(ds, t, peak + 150, (rx(0.95), h * 0.86), w * 0.7, Color(0xFFE8C9A0), 800, 0.8)
        # облако пыли и гравий после пика
        for burst, t0 in enumerate((peak - 60, peak + 650)):
            rnd = Rnd(30 + burst)
            for i in range(60 if burst == 0 else 35):
                p = lin(t, t0 + rnd.nextFloat() * 200, t0 + 2600 + rnd.nextFloat() * 1600)
                ang = rnd.nextFloat() * PI + PI; spd = (0.25 + rnd.nextFloat() * 0.7) * w
                r = u * (8 + rnd.nextFloat() * 22) * (0.6 + p * 2.2)
                if p <= 0 or p >= 1: continue
                d_ = spd * (1 - (1 - p) ** 3)
                ds.drawCircle(Color(0xFFD9B48C).copy((1 - p) * 0.30), r, (w / 2 + cos(ang) * d_, h * 0.85 + sin(ang) * d_ * 0.5))
        rnd = Rnd(61)
        for i in range(45):
            st = peak - 50 + rnd.nextFloat() * 300; p = lin(t, st, st + 700)
            if p <= 0 or p >= 1: continue
            ang = -PI / 2 + (rnd.nextFloat() - 0.5) * 2.4; sp = u * (40 + rnd.nextFloat() * 70)
            x = w / 2 + cos(ang) * sp * p; y = h * 0.86 + sin(ang) * sp * p + u * 90 * p * p
            ds.drawCircle(Color(0xFF2A1E12).copy(1 - p * 0.5), u * (0.6 + rnd.nextFloat() * 1.4) * (1 + p), (x, y))
        # пылинки в лучах
        rnd = Rnd(70)
        for i in range(40):
            x = (rnd.nextFloat() + t / 9000 * (0.5 + rnd.nextFloat())) % 1 * w
            y = (rnd.nextFloat() + sin(t / 1500 + i) * 0.01) * h * 0.8
            ds.drawCircle(Color(0xFFFFE7B0).copy(0.35 * (0.5 + 0.5 * sin(t / 300 + i))), u * 0.35, (x, y))
        flare_at(ds, sun, Color(0xFFFFE2A0), 0.55)
    fl = 1 - lin(t, peak, peak + 380)
    if t >= peak and fl > 0: ds.drawRect(Color(0xFFFFF2D0).copy(0.45 * fl))
    vignette(ds, 0.6)


# ---------- 8. Drift Night: 3.85 с, пик 1250 ----------
def bez(p0, p1, p2, p3, q):
    m = 1 - q
    x = m*m*m*p0[0] + 3*m*m*q*p1[0] + 3*m*q*q*p2[0] + q*q*q*p3[0]
    y = m*m*m*p0[1] + 3*m*m*q*p1[1] + 3*m*q*q*p2[1] + q*q*q*p3[1]
    return (x, y)

def drift_road(w, h, s_):
    """Точка горной дороги: s_=0 вдали наверху, s_=1 у нас внизу."""
    A = ((w * 0.5, h * 0.20), (w * 0.38, h * 0.26), (w * 0.86, h * 0.30), (w * 0.62, h * 0.42))
    B = ((w * 0.62, h * 0.42), (w * 0.30, h * 0.58), (w * -0.05, h * 0.62), (w * 0.42, h * 0.78))
    Cc = ((w * 0.42, h * 0.78), (w * 0.75, h * 0.88), (w * 0.70, h * 0.95), (w * 0.55, h * 1.08))
    if s_ < 0.3: return bez(*A, s_ / 0.3)
    if s_ < 0.75: return bez(*B, (s_ - 0.3) / 0.45)
    return bez(*Cc, (s_ - 0.75) / 0.25)

def drift(ds, t, peak=1250.):
    w, h = ds.w, ds.h; u = u_(ds)
    ds.drawRect(Brush.vertical([Color(0xFF04051A), Color(0xFF0F1440), Color(0xFF1C1F55)], 0, h * 0.45))
    rnd = Rnd(4)
    for i in range(70):
        ds.drawCircle(White.copy(0.25 + 0.3 * sin(t / 300 + i)), u * 0.3, (rnd.nextFloat() * w, rnd.nextFloat() * h * 0.35))
    glow(ds, (w * 0.22, h * 0.10), w * 0.4, Color(0xFFB7C4FF), 0.25)
    ds.drawCircle(Color(0xFFEDF0FF), u * 4.5, (w * 0.22, h * 0.10))
    flare_at(ds, (w * 0.22, h * 0.10), Color(0xFFB7C4FF), 0.3)
    # хребты гор слоями с туманом
    ridges = ((0.24, Color(0xFF232867), 0.06, 1), (0.30, Color(0xFF161A48), 0.09, 2), (0.40, Color(0xFF0B0D2A), 0.12, 3))
    for base, col, amp, seed in ridges:
        rnd = Rnd(seed * 31)
        pk = [rnd.nextFloat() for _ in range(9)]
        rp = Path().moveTo(0, h)
        for k in range(9):
            rp.lineTo(k / 8 * w, h * base - pk[k] * h * amp)
        rp.lineTo(w, h).close()
        ds.drawPath(rp, col)
        ds.drawRect(Brush.vertical([Transparent, Color(0xFF7D86D9).copy(0.10)], h * (base - 0.05), h * (base + 0.04)), (0, h * (base - 0.05)), (w, h * 0.09))
    # склон горы под дорогой
    ds.drawRect(Brush.vertical([Color(0xFF080A20), Color(0xFF04050F)], h * 0.40, h), (0, h * 0.4), (w, h * 0.6))
    # дорога: широкая лента, отбойник со светоотражателями, осевая
    N = 60
    pts = [drift_road(w, h, k / N) for k in range(N + 1)]
    for k in range(N):
        z = k / N; a, b = pts[k], pts[k + 1]
        ds.drawLine(Color(0xFF1A1D33), a, b, u * (2 + 16 * z), "round")
    for k in range(N):
        z = k / N
        if k % 3 == 0:
            a, b = pts[k], pts[k + 1]
            ds.drawLine(Color(0xFFE8E8F0).copy(0.35), a, (a[0] + (b[0] - a[0]) * 0.5, a[1] + (b[1] - a[1]) * 0.5), u * (0.2 + 0.5 * z))
    for k in range(0, N, 2):
        z = k / N; a, b = pts[k], pts[min(N, k + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]; L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L; off = u * (1.2 + 8.5 * z)
        for sd in (-1, 1):
            ds.drawCircle(Color(0xFFFFB347).copy(0.55), u * (0.15 + 0.45 * z), (a[0] + nx * off * sd, a[1] + ny * off * sd))
    # машина едет к нам, в повороте идёт в занос
    sp = lin(t, 150, 3300) ** 1.1
    def head(s_):
        a = drift_road(w, h, max(0, s_ - 0.004)); b = drift_road(w, h, min(1, s_ + 0.004))
        return math.atan2(b[1] - a[1], b[0] - a[0])
    # световые следы стоп-сигналов
    tail = max(0, sp - 0.28)
    for k in range(24):
        s0 = tail + (sp - tail) * k / 24; s1 = tail + (sp - tail) * (k + 1) / 24
        a, b = drift_road(w, h, s0), drift_road(w, h, s1)
        z = s1
        for sd in (-1, 1):
            hh = head(s0); nx, ny = -math.sin(hh), math.cos(hh); off = u * (0.6 + 2.6 * z) * sd
            ds.drawLine(Color(0xFFFF2244).copy(0.55 * k / 24), (a[0] + nx * off, a[1] + ny * off), (b[0] + nx * off, b[1] + ny * off), u * (0.25 + 0.7 * z), "round")
    # дым из-под колёс
    for k in range(16):
        s0 = sp - 0.012 * k
        if s0 <= 0: continue
        p = drift_road(w, h, s0); age = k / 16
        r = u * (2 + 9 * s0) * (1 + age * 2.2)
        glow(ds, (p[0] + sin(k * 1.7) * u * 2, p[1] - age * u * 4), r, Color(0xFFDDE2FF), 0.28 * (1 - age) * span(t, 500, 900))
    burst = span(t, peak - 80, peak + 120) * (1 - span(t, peak + 400, peak + 2200))
    if burst > 0:
        pc = drift_road(w, h, lin(peak, 150, 3300) ** 1.1)
        for k in range(14):
            ang = k * 0.45; d_ = u * (6 + 22 * span(t, peak, peak + 1800)) * (0.6 + 0.4 * (k % 3) / 2)
            glow(ds, (pc[0] + cos(ang) * d_, pc[1] + sin(ang) * d_ * 0.6 - u * 6 * span(t, peak, peak + 2000)), u * (7 + 2 * (k % 5)) * (1 + span(t, peak, peak + 2000)), Color(0xFFE6EAFF), 0.16 * burst)
    if 0 < sp < 1:
        c = drift_road(w, h, sp); z = sp
        hdg = head(sp)
        # угол заноса растёт к пику и уходит после
        slip = math.radians(38) * span(t, 500, peak) * (1 - span(t, peak + 500, peak + 1300))
        ang = hdg + slip
        L = u * (4 + 12 * z); W = L * 0.5
        # фары: два конуса света вперёд по направлению машины
        fx, fy = math.cos(ang), math.sin(ang)
        for sd in (-1, 1):
            o = (c[0] + fx * L * 0.5 - fy * W * 0.32 * sd, c[1] + fy * L * 0.5 + fx * W * 0.32 * sd)
            far = (o[0] + fx * L * 4, o[1] + fy * L * 4)
            cone = Path().moveTo(*o).lineTo(far[0] - fy * L * 1.2, far[1] + fx * L * 1.2).lineTo(far[0] + fy * L * 1.2, far[1] - fx * L * 1.2).close()
            ds.drawPath(cone, Brush.radial([Color(0xFFE8F0FF).copy(0.35), Transparent], o, L * 4.2))
            near = span(sp, 0.45, 0.8)
            if near > 0:
                glow(ds, o, L * 0.9, Color(0xFFE8F0FF), 0.35 * near)
                star4(ds, o, L * 0.3 * near, White.copy(0.75 * near))
        with ds.rotate(math.degrees(ang), c):
            ds.drawRoundRect(Color(0xFF0E0F16), (c[0] - L / 2, c[1] - W / 2), (L, W), W * 0.3)
            ds.drawRoundRect(Color(0xFFB4FF00).copy(0.85), (c[0] - L / 2, c[1] - W / 2), (L, W), W * 0.3, {"width": u * 0.25})
            ds.drawRect(Color(0xFF2A3350), (c[0] + L * 0.05, c[1] - W * 0.36), (L * 0.18, W * 0.72))
            for sd in (-1, 1):
                ds.drawCircle(Color(0xFFFF2244), W * 0.11, (c[0] - L * 0.48, c[1] + sd * W * 0.3))
                ds.drawCircle(Color(0xFFF4F8FF), W * 0.1, (c[0] + L * 0.48, c[1] + sd * W * 0.3))
            glow(ds, (c[0] - L * 0.55, c[1]), W * 1.2, Color(0xFFFF2244), 0.5)
    # туман: сначала закрывает, на пике расступается, потом ползёт полосами
    inward = span(t, 0, 600); out = span(t, 700, 1800)
    k_ = inward * (1 - out)
    for i, o in enumerate(((0, h * 0.5), (w, h * 0.55), (w * 0.5, h * 0.2), (w * 0.5, h), (0, h * 0.15), (w, h * 0.9))):
        m = (w / 2, h / 2); f = 0.7 * k_
        pos = (o[0] + (m[0] - o[0]) * f, o[1] + (m[1] - o[1]) * f)
        glow(ds, pos, w * (0.6 + 0.05 * (i % 3)), Color(0xFFC8CEF0), 0.45 * k_)
    for i in range(4):
        y = h * (0.35 + i * 0.16); x = ((t / 1000 * (0.06 + 0.03 * i) + i * 0.37) % 1.6 - 0.3) * w
        with ds.scale(1, 0.14, (x, y)):
            glow(ds, (x, y), w * 0.55, Color(0xFFB8C0F0), 0.16)
    fl = 1 - lin(t, peak, peak + 300)
    if t >= peak and fl > 0: ds.drawRect(Color(0xFFDCE4FF).copy(0.14 * fl))
    vignette(ds, 0.6)

def render(name, fn, times, total):
    frames = []
    for tt in times:
        ds = DS(360, 780)
        fn(ds, tt)
        p = f"out/{name}_{int(tt)}.png"; ds.png(p); frames.append((p, f"{name} {int(tt)}ms"))
    sheet(frames, f"out/{name}_sheet.png", cols=len(times) if len(times) <= 6 else 6)

if __name__ == "__main__":  # noqa
    import os; os.makedirs("out", exist_ok=True)
    which = sys.argv[1]
    fn = globals()[which]
    times = [float(x) for x in sys.argv[2].split(",")]
    render(which, fn, times, None)

