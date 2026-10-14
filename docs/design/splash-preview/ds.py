"""Мини-копия Compose DrawScope поверх libcairo (через ctypes) — для предпросмотра заставок."""
import ctypes as C, math

lib = C.CDLL("libcairo.so.2")
vp = C.c_void_p
d = C.c_double
def f(name, res, *args):
    fn = getattr(lib, name); fn.restype = res; fn.argtypes = list(args); return fn

image_surface_create = f("cairo_image_surface_create", vp, C.c_int, C.c_int, C.c_int)
create = f("cairo_create", vp, vp)
destroy = f("cairo_destroy", None, vp)
surface_destroy = f("cairo_surface_destroy", None, vp)
write_png = f("cairo_surface_write_to_png", C.c_int, vp, C.c_char_p)
save = f("cairo_save", None, vp)
restore = f("cairo_restore", None, vp)
translate_ = f("cairo_translate", None, vp, d, d)
rotate_ = f("cairo_rotate", None, vp, d)
scale_ = f("cairo_scale", None, vp, d, d)
move_to = f("cairo_move_to", None, vp, d, d)
line_to = f("cairo_line_to", None, vp, d, d)
curve_to = f("cairo_curve_to", None, vp, d, d, d, d, d, d)
close_path = f("cairo_close_path", None, vp)
new_path = f("cairo_new_path", None, vp)
arc = f("cairo_arc", None, vp, d, d, d, d, d)
rectangle = f("cairo_rectangle", None, vp, d, d, d, d)
fill = f("cairo_fill", None, vp)
fill_preserve = f("cairo_fill_preserve", None, vp)
stroke = f("cairo_stroke", None, vp)
clip = f("cairo_clip", None, vp)
paint = f("cairo_paint", None, vp)
set_rgba = f("cairo_set_source_rgba", None, vp, d, d, d, d)
set_source = f("cairo_set_source", None, vp, vp)
set_line_width = f("cairo_set_line_width", None, vp, d)
set_line_cap = f("cairo_set_line_cap", None, vp, C.c_int)
set_line_join = f("cairo_set_line_join", None, vp, C.c_int)
set_dash = f("cairo_set_dash", None, vp, C.POINTER(d), C.c_int, d)
pat_linear = f("cairo_pattern_create_linear", vp, d, d, d, d)
pat_radial = f("cairo_pattern_create_radial", vp, d, d, d, d, d, d)
pat_stop = f("cairo_pattern_add_color_stop_rgba", None, vp, d, d, d, d, d)
pat_destroy = f("cairo_pattern_destroy", None, vp)
set_fill_rule = f("cairo_set_fill_rule", None, vp, C.c_int)
set_operator = f("cairo_set_operator", None, vp, C.c_int)

class Color:
    def __init__(s, argb=None, r=0., g=0., b=0., a=1.):
        if argb is not None:
            s.a = ((argb >> 24) & 255) / 255; s.r = ((argb >> 16) & 255) / 255
            s.g = ((argb >> 8) & 255) / 255; s.b = (argb & 255) / 255
        else:
            s.r, s.g, s.b, s.a = r, g, b, a
    def copy(s, alpha=None):
        return Color(None, s.r, s.g, s.b, s.a if alpha is None else max(0., min(1., alpha)))
    def lerp(s, o, t):
        return Color(None, s.r + (o.r - s.r) * t, s.g + (o.g - s.g) * t, s.b + (o.b - s.b) * t, s.a + (o.a - s.a) * t)

White = Color(0xFFFFFFFF); Black = Color(0xFF000000); Transparent = Color(0x00000000)

class Brush:
    def __init__(s, kind, colors, **kw):
        s.kind, s.kw = kind, kw
        # colors: list of Color or list of (stop, Color)
        if colors and isinstance(colors[0], tuple): s.stops = colors
        else:
            n = len(colors); s.stops = [(i / (n - 1) if n > 1 else 0, c) for i, c in enumerate(colors)]
    @staticmethod
    def vertical(colors, startY=0., endY=math.inf): return Brush("v", colors, startY=startY, endY=endY)
    @staticmethod
    def horizontal(colors, startX=0., endX=math.inf): return Brush("h", colors, startX=startX, endX=endX)
    @staticmethod
    def linear(colors, start, end): return Brush("l", colors, start=start, end=end)
    @staticmethod
    def radial(colors, center, radius): return Brush("r", colors, center=center, radius=radius)

class Path:
    def __init__(s): s.ops = []
    def moveTo(s, x, y): s.ops.append(("m", x, y)); return s
    def lineTo(s, x, y): s.ops.append(("l", x, y)); return s
    def cubicTo(s, a, b, c_, d_, e, f_): s.ops.append(("c", a, b, c_, d_, e, f_)); return s
    def quadraticTo(s, x1, y1, x2, y2): s.ops.append(("q", x1, y1, x2, y2)); return s
    def close(s): s.ops.append(("z",)); return s
    def addOval(s, l, t, r, b): s.ops.append(("o", l, t, r, b)); return s

class DS:
    def __init__(s, w, h):
        s.w, s.h = w, h
        s.surf = image_surface_create(0, w, h)  # ARGB32
        s.cr = create(s.surf)
        set_rgba(s.cr, 0, 0, 0, 1); paint(s.cr)
    def png(s, path):
        write_png(s.surf, path.encode()); destroy(s.cr); surface_destroy(s.surf)

    def _src(s, paint_, bounds):
        cr = s.cr
        if isinstance(paint_, Color):
            set_rgba(cr, paint_.r, paint_.g, paint_.b, paint_.a); return None
        b = paint_; kw = b.kw
        if b.kind == "v":
            y1 = kw["endY"] if kw["endY"] != math.inf else s.h
            p = pat_linear(0, kw["startY"], 0, y1)
        elif b.kind == "h":
            x1 = kw["endX"] if kw["endX"] != math.inf else s.w
            p = pat_linear(kw["startX"], 0, x1, 0)
        elif b.kind == "l":
            p = pat_linear(kw["start"][0], kw["start"][1], kw["end"][0], kw["end"][1])
        else:
            cx, cy = kw["center"]; p = pat_radial(cx, cy, 0, cx, cy, max(kw["radius"], 0.01))
        for st, c in b.stops: pat_stop(p, st, c.r, c.g, c.b, c.a)
        set_source(cr, p); return p

    def _finish(s, paint_, style, alpha=1.0):
        p = s._src(paint_, None)
        cr = s.cr
        if style is None:
            fill(cr)
        else:
            set_line_width(cr, style.get("width", 1.0)); set_line_cap(cr, {"butt": 0, "round": 1, "square": 2}[style.get("cap", "butt")])
            set_line_join(cr, 1)
            dash = style.get("dash")
            if dash:
                arr = (d * len(dash[0]))(*dash[0]); set_dash(cr, arr, len(dash[0]), dash[1])
            stroke(cr)
            set_dash(cr, None, 0, 0)
        if p: pat_destroy(p)

    def drawRect(s, paint_, topLeft=(0, 0), size=None, style=None):
        size = size or (s.w - topLeft[0], s.h - topLeft[1])
        new_path(s.cr); rectangle(s.cr, topLeft[0], topLeft[1], size[0], size[1]); s._finish(paint_, style)
    def drawRoundRect(s, paint_, topLeft, size, r, style=None):
        x, y = topLeft; w, h = size; r = min(r, w / 2, h / 2); cr = s.cr
        new_path(cr)
        arc(cr, x + w - r, y + r, r, -math.pi / 2, 0); arc(cr, x + w - r, y + h - r, r, 0, math.pi / 2)
        arc(cr, x + r, y + h - r, r, math.pi / 2, math.pi); arc(cr, x + r, y + r, r, math.pi, 1.5 * math.pi); close_path(cr)
        s._finish(paint_, style)
    def drawCircle(s, paint_, radius, center, style=None):
        if radius <= 0: return
        new_path(s.cr); arc(s.cr, center[0], center[1], radius, 0, 2 * math.pi); s._finish(paint_, style)
    def drawOval(s, paint_, topLeft, size, style=None):
        cr = s.cr; save(cr); new_path(cr)
        translate_(cr, topLeft[0] + size[0] / 2, topLeft[1] + size[1] / 2); scale_(cr, size[0] / 2, size[1] / 2)
        arc(cr, 0, 0, 1, 0, 2 * math.pi); restore(cr); s._finish(paint_, style)
    def drawLine(s, paint_, start, end, strokeWidth=1.0, cap="butt", dash=None):
        new_path(s.cr); move_to(s.cr, *start); line_to(s.cr, *end)
        s._finish(paint_, {"width": strokeWidth, "cap": cap, "dash": dash})
    def drawPath(s, path, paint_, style=None):
        cr = s.cr; new_path(cr)
        cx = cy = 0
        for op in path.ops:
            if op[0] == "m": move_to(cr, op[1], op[2]); cx, cy = op[1], op[2]
            elif op[0] == "l": line_to(cr, op[1], op[2]); cx, cy = op[1], op[2]
            elif op[0] == "c": curve_to(cr, *op[1:]); cx, cy = op[5], op[6]
            elif op[0] == "q":
                x1, y1, x2, y2 = op[1:]
                curve_to(cr, cx + 2 / 3 * (x1 - cx), cy + 2 / 3 * (y1 - cy), x2 + 2 / 3 * (x1 - x2), y2 + 2 / 3 * (y1 - y2), x2, y2); cx, cy = x2, y2
            elif op[0] == "z": close_path(cr)
            elif op[0] == "o":
                l, t, r, b = op[1:]; save(cr); translate_(cr, (l + r) / 2, (t + b) / 2); scale_(cr, (r - l) / 2, (b - t) / 2)
                arc(cr, 0, 0, 1, 0, 2 * math.pi); restore(cr)
        s._finish(paint_, style)

    # трансформации как контекстные менеджеры
    class _T:
        def __init__(s, ds, fn): s.ds, s.fn = ds, fn
        def __enter__(s): save(s.ds.cr); s.fn(); return s.ds
        def __exit__(s, *a): restore(s.ds.cr)
    def translate(s, x, y): return DS._T(s, lambda: translate_(s.cr, x, y))
    def rotate(s, deg, pivot):
        def fn():
            translate_(s.cr, pivot[0], pivot[1]); rotate_(s.cr, math.radians(deg)); translate_(s.cr, -pivot[0], -pivot[1])
        return DS._T(s, fn)
    def scale(s, sx, sy, pivot):
        def fn():
            translate_(s.cr, pivot[0], pivot[1]); scale_(s.cr, sx, sy); translate_(s.cr, -pivot[0], -pivot[1])
        return DS._T(s, fn)
    def clipRect(s, l, t, r, b):
        def fn():
            new_path(s.cr); rectangle(s.cr, l, t, r - l, b - t); clip(s.cr)
        return DS._T(s, fn)
    def additive(s):
        return DS._T(s, lambda: set_operator(s.cr, 12))  # CAIRO_OPERATOR_ADD

# ---------- общие утилиты, как в Kotlin ----------
def ease(x):
    t = max(0., min(1., x)); return t * t * (3 - 2 * t)
def span(t, a, b): return ease((t - a) / (b - a))
def lin(t, a, b): return max(0., min(1., (t - a) / (b - a)))

class Rnd:
    """Повторяет kotlin.random.Random(seed).nextFloat() не нужно — достаточно детерминированности."""
    def __init__(s, seed): s.x = (seed * 2654435761 + 12345) & 0xFFFFFFFF
    def nextFloat(s):
        s.x ^= (s.x << 13) & 0xFFFFFFFF; s.x ^= s.x >> 17; s.x ^= (s.x << 5) & 0xFFFFFFFF
        return (s.x & 0xFFFFFF) / float(0x1000000)

def sheet(frames, out, cols=4):
    """Склеивает кадры в один лист для просмотра."""
    from PIL import Image, ImageDraw
    ims = [Image.open(p) for p, _ in frames]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    S = Image.new("RGB", (cols * w, rows * (h + 22)), (40, 40, 40))
    dr = ImageDraw.Draw(S)
    for i, (im, (_, label)) in enumerate(zip(ims, frames)):
        x, y = (i % cols) * w, (i // cols) * (h + 22)
        S.paste(im, (x, y + 22)); dr.text((x + 6, y + 4), label, fill=(255, 255, 255))
    S.save(out)
