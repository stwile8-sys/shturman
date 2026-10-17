"""Логотип APEX: буква A — шпилька (вид сверху) с красной точкой апекса, геометрия снята с видео-заставки.
Делает: адаптивную иконку приложения (фон, передний план, монохром), значок для уведомлений,
PNG-иконки для старых экранов и картинку-превью. Все размеры — в долях высоты буквы (H = 1).
Запуск: python3 apexlogo.py <папка res> <папка превью>
"""
import math, os, sys

# ---------- геометрия буквы A (H = 1, начало — левый верх рамки буквы) ----------
S = 0.1765                       # толщина линии
C = (0.4685, 0.2580)             # центр дуги шпильки (по средней линии)
R = 0.1693                       # радиус дуги по средней линии
LT, RT = (0.3050, 0.2130), (0.6320, 0.2130)   # точки касания дуги с ножками
LB, RB = (0.0880, 1.0), (0.8490, 1.0)         # низ ножек по средней линии (на базовой линии)
DOT, DOT_R = (0.4685, 0.5130), 0.0798         # точка апекса
A_W = 0.937                                    # ширина буквы

def ext(p, q, k):
    """Продлевает отрезок q→p за точку p на k (чтобы потом ровно обрезать по базовой линии)."""
    dx, dy = p[0] - q[0], p[1] - q[1]; L = math.hypot(dx, dy)
    return (p[0] + dx / L * k, p[1] + dy / L * k)

LBX, RBX = ext(LB, LT, 0.15), ext(RB, RT, 0.15)

CHALK = '#F3F1EA'; RED = '#FF2B2B'; BG0 = '#1C1B18'; BG1 = '#060605'

def f(v): return ('%.3f' % v).rstrip('0').rstrip('.')

def a_path(ox, oy, h):
    """pathData средней линии буквы A для vector drawable: ножка — дуга — ножка."""
    P = lambda p: (ox + p[0] * h, oy + p[1] * h)
    lb, lt, rt, rb = P(LBX), P(LT), P(RT), P(RBX)
    r = R * h
    return f'M{f(lb[0])},{f(lb[1])} L{f(lt[0])},{f(lt[1])} A{f(r)},{f(r)} 0 0,1 {f(rt[0])},{f(rt[1])} L{f(rb[0])},{f(rb[1])}'

def circle(cx, cy, r):
    return f'M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z'

def mark_xml(size_dp, vb, h, ox, oy, color, dot_color, glow, mono=False):
    base = oy + h
    p = a_path(ox, oy, h)
    dx, dy = ox + DOT[0] * h, oy + DOT[1] * h
    out = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"' if glow else '',
           f'    android:width="{size_dp}dp" android:height="{size_dp}dp"',
           f'    android:viewportWidth="{vb}" android:viewportHeight="{vb}">']
    out = [l for l in out if l]
    if glow:
        gr = DOT_R * h * 3.2
        out.append(f'''    <path android:pathData="{circle(dx, dy, gr)}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{f(dx)}" android:centerY="{f(dy)}" android:gradientRadius="{f(gr)}"
                android:startColor="#73FF2B2B" android:centerColor="#26FF2B2B" android:endColor="#00FF2B2B" />
        </aapt:attr>
    </path>''')
    out.append(f'''    <group>
        <clip-path android:pathData="M0,{f(oy - 1)} H{vb} V{f(base)} H0 Z" />
        <path android:pathData="{p}" android:strokeColor="{color}" android:strokeWidth="{f(S * h)}"
            android:strokeLineCap="butt" android:strokeLineJoin="round" />
    </group>''')
    out.append(f'    <path android:pathData="{circle(dx, dy, DOT_R * h)}" android:fillColor="{dot_color}" />')
    out.append('</vector>\n')
    return '\n'.join(out)

def background_xml():
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="50" android:gradientRadius="78"
                android:startColor="{BG0}" android:endColor="{BG1}" />
        </aapt:attr>
    </path>
</vector>
'''

ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''

# Иконка: буква высотой 46 из 108 (безопасная зона адаптивной иконки — круг диаметром 66)
IH = 46.0
IOX = 54 - A_W * IH / 2
IOY = 54.5 - IH / 2

def write_res(res):
    d = os.path.join(res, 'drawable'); os.makedirs(d, exist_ok=True)
    open(os.path.join(d, 'ic_launcher_background.xml'), 'w').write(background_xml())
    open(os.path.join(d, 'ic_launcher_foreground.xml'), 'w').write(mark_xml(108, 108, IH, IOX, IOY, CHALK, RED, True))
    open(os.path.join(d, 'ic_launcher_monochrome.xml'), 'w').write(mark_xml(108, 108, IH, IOX, IOY, '#FFFFFFFF', '#FFFFFFFF', False))
    # значок уведомлений: белый, 24 dp, буква на всю высоту
    h = 20.0
    open(os.path.join(d, 'ic_stat_apex.xml'), 'w').write(mark_xml(24, 24, h, 12 - A_W * h / 2, 2, '#FFFFFFFF', '#FFFFFFFF', False))
    m = os.path.join(res, 'mipmap-anydpi-v26'); os.makedirs(m, exist_ok=True)
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        open(os.path.join(m, n), 'w').write(ADAPTIVE)

# ---------- растровые иконки (для старых лаунчеров) и превью через cairo ----------
def render(path, px, shape):
    sys.path.insert(0, os.environ.get('DS_PATH', os.path.dirname(os.path.abspath(__file__))))
    import ds as dsm
    from ds import Color, Brush, Path
    d = dsm.DS(px, px)
    cr = d.cr
    k = px / 108.0
    dsm.set_operator(cr, 0); dsm.paint(cr); dsm.set_operator(cr, 2)  # прозрачный фон
    # форма плитки
    dsm.new_path(cr)
    if shape == 'circle':
        dsm.arc(cr, px / 2, px / 2, px / 2, 0, 2 * math.pi)
    elif shape == 'squircle':
        r = px * 0.22
        dsm.arc(cr, px - r, r, r, -math.pi / 2, 0); dsm.arc(cr, px - r, px - r, r, 0, math.pi / 2)
        dsm.arc(cr, r, px - r, r, math.pi / 2, math.pi); dsm.arc(cr, r, r, r, math.pi, 1.5 * math.pi); dsm.close_path(cr)
    else:
        dsm.rectangle(cr, 0, 0, px, px)
    dsm.clip(cr)
    # иконка в лаунчере показывает 72 из 108: увеличиваем, как система
    z = 108.0 / 72.0
    kk = k * z
    off = -(108 - 72) / 2 * kk
    def T(x, y): return (off + x * kk, off + y * kk)
    c0 = Color(0xFF1C1B18); c1 = Color(0xFF060605)
    d.drawRect(Brush.radial([c0, c1], T(54, 50), 78 * kk))
    h = IH
    def P(p): return T(IOX + p[0] * h, IOY + p[1] * h)
    dot = P(DOT); gr = DOT_R * h * 3.2 * kk
    d.drawCircle(Brush.radial([(0, Color(0x73FF2B2B)), (0.5, Color(0x26FF2B2B)), (1, Color(0x00FF2B2B))], dot, gr), gr, dot)
    base = T(0, IOY + h)[1]
    dsm.save(cr); dsm.new_path(cr); dsm.rectangle(cr, 0, 0, px, base); dsm.clip(cr)
    pth = Path().moveTo(*P(LBX)).lineTo(*P(LT))
    # дуга через середину (точки по окружности)
    cx, cy = C; a0 = math.atan2(LT[1] - cy, LT[0] - cx) % (2 * math.pi); a1 = math.atan2(RT[1] - cy, RT[0] - cx) % (2 * math.pi)
    for i in range(1, 41):
        a = a0 + (a1 - a0) * i / 40
        pth.lineTo(*P((cx + math.cos(a) * R, cy + math.sin(a) * R)))
    pth.lineTo(*P(RBX))
    d.drawPath(pth, Color(0xFFF3F1EA), {"width": S * h * kk})
    dsm.restore(cr)
    d.drawCircle(Color(0xFFFF2B2B), DOT_R * h * kk, dot)
    d.png(path)

if __name__ == '__main__':
    res, prev = sys.argv[1], sys.argv[2]
    write_res(res)
    for dname, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        m = os.path.join(res, f'mipmap-{dname}'); os.makedirs(m, exist_ok=True)
        render(os.path.join(m, 'ic_launcher.png'), px, 'squircle')
        render(os.path.join(m, 'ic_launcher_round.png'), px, 'circle')
    os.makedirs(prev, exist_ok=True)
    for shape in ('circle', 'squircle', 'square'):
        render(os.path.join(prev, f'icon_{shape}.png'), 512, shape)
    print('ok')
