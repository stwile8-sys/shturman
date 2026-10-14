# Генератор страниц с вариантами кнопок: 4 варианта x 8 тем.
# Делает .dc.html для холста и обычные .html для локальных снимков (playwright).
import json, os, sys

OUT = os.path.join(os.path.dirname(__file__), 'project')
PREV = os.path.join(os.path.dirname(__file__), 'prev')
os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)

T = [
    dict(name='Классика', acc='#00F0FF', acc2='#FF00A8', bg='#050608', panel='#0D1015', text='#FFFFFF', sec='#9AA1AB', f='neon', road='#1a2230', land='#0a1a14', water='#0b1d2e'),
    dict(name='Принцесса', acc='#FF69B4', acc2='#B98BFF', bg='#0F0510', panel='#1A0F1A', text='#FFEAF5', sec='#C9A0C4', f='glass', road='#2e1a2c', land='#1a0e1a', water='#1d1030'),
    dict(name='NASCAR', acc='#FFFFFF', acc2='#E60019', bg='#0A0A0A', panel='#141414', text='#FFFFFF', sec='#B0B0B0', f='tel', road='#262626', land='#121212', water='#10161f'),
    dict(name='GT Racing', acc='#E60019', acc2='#FF5A1F', bg='#0A0505', panel='#1A0A0A', text='#FFFFFF', sec='#C4A8A8', f='tel', road='#2c1616', land='#140808', water='#120d18'),
    dict(name='Night Rider', acc='#00D9FF', acc2='#1F6BFF', bg='#050A18', panel='#0A1428', text='#E8F8FF', sec='#7FA6C8', f='depth', road='#14223f', land='#071228', water='#06183a'),
    dict(name='Street', acc='#FFB000', acc2='#FF3B1F', bg='#0A0A0B', panel='#17171A', text='#F4F0E6', sec='#A8A296', f='neon', road='#29282a', land='#121113', water='#101722'),
    dict(name='Rally', acc='#D97830', acc2='#FFB45A', bg='#1A0F08', panel='#2A1A0A', text='#FFF1E4', sec='#C4A688', f='tel', road='#3d2814', land='#1f1a0b', water='#12202a'),
    dict(name='Drift', acc='#5C7CFF', acc2='#B04BE8', bg='#0A0F1E', panel='#141A2E', text='#EAF0FF', sec='#9AA8D2', f='depth', road='#1e2742', land='#0c1424', water='#0b1630'),
]

V = {
    'V1': dict(file='Main.dc.html', title='1 · Чистое стекло'),
    'V2': dict(file='Solid.dc.html', title='2 · Монолит'),
    'V3': dict(file='Capsule.dc.html', title='3 · Капсула'),
    'V4': dict(file='Rings.dc.html', title='4 · Светящиеся кольца'),
}

ICONS = {
    'menu': '<path d="M4 7h16M4 12h16M4 17h11"/>',
    'star': '<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"/>',
    'search': '<circle cx="11" cy="11" r="6.2"/><path d="M20 20l-4.4-4.4"/>',
    'music': '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.6"/><circle cx="16.5" cy="16" r="2.6"/>',
    'radar': '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3.6"/><path d="M12 12l5.6-5.6"/>',
    'loc': '<circle cx="12" cy="12" r="3.2"/><circle cx="12" cy="12" r="7.2"/><path d="M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4"/>',
    'heart': '<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>',
    'x': '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
}
def ic(name, cls=''):
    return f'<svg class="ic {cls}" viewBox="0 0 24 24" aria-hidden="true">{ICONS[name]}</svg>'

BASE_CSS = r"""
body{margin:0}
.ph{position:relative;width:390px;height:844px;overflow:hidden;background:var(--bg);font-family:'Manrope',system-ui,sans-serif;color:var(--text);
 --spring:cubic-bezier(.34,1.56,.64,1);--soft:cubic-bezier(.22,1,.36,1);-webkit-tap-highlight-color:transparent;user-select:none}
.ph *{box-sizing:border-box}
:where(.ph button){font:inherit;color:inherit;border:0;padding:0;background:none;cursor:pointer;outline:none}
.ic{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;position:relative;z-index:2;display:block}
.ic-star,.ic-heart{display:none}
.th1 .ic-star,.th1 .ic-heart{display:block}
.th1 .ic-menu,.th1 .ic-loc{display:none}
.map{position:absolute;inset:0}
.map svg.base{position:absolute;inset:0;width:390px;height:844px}
.rd{fill:none;stroke:var(--road);stroke-linecap:round;stroke-linejoin:round}
.route{fill:none;stroke:var(--acc);stroke-width:7;stroke-linecap:round;stroke-linejoin:round}
.rglow{fill:none;stroke:var(--acc);stroke-width:20;opacity:.35;stroke-linecap:round;stroke-linejoin:round;filter:blur(8px)}
.puck{position:absolute;left:180px;top:610px;width:30px;height:34px;filter:drop-shadow(0 0 10px var(--acc))}
/* выбор темы в правом верхнем углу */
.tp{position:absolute;right:12px;top:14px;z-index:20;display:flex;flex-direction:column;align-items:flex-end;gap:6px}
.tp .dots{display:grid;grid-template-columns:repeat(4,22px);gap:7px;padding:8px;border-radius:14px;background:rgba(0,0,0,.55)}
.tp .sw{width:22px;height:22px;border-radius:50%;border:2px solid rgba(255,255,255,.18);transition:transform .25s var(--spring)}
.tp .sw.on{border-color:#fff;transform:scale(1.15)}
.tp .nm{font-size:12px;font-weight:700;color:#fff;background:rgba(0,0,0,.55);padding:3px 9px;border-radius:9px}
.col{position:absolute;left:14px;top:50px;display:flex;flex-direction:column;gap:12px;z-index:5}
.pill{position:absolute;left:16px;right:16px;bottom:26px;height:62px;display:flex;align-items:center;gap:12px;padding:0 9px 0 20px;z-index:5;
 transition:transform .45s var(--soft),opacity .3s}
.pill .t{flex:1;font-size:17px;font-weight:600;text-align:left;opacity:.9}
.pill .sic{color:var(--acc)}
.m-route .pill{transform:translateY(40px);opacity:0;pointer-events:none}
.locb{position:absolute;right:14px;bottom:432px;z-index:5;opacity:0;transform:scale(.6);transition:transform .45s var(--spring),opacity .3s;pointer-events:none}
.m-route .locb{opacity:1;transform:none;pointer-events:auto}
.sheet{position:absolute;left:0;right:0;bottom:0;height:410px;z-index:8;padding:12px 18px 22px;transform:translateY(105%);transition:transform .55s var(--soft);
 background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 90%,#fff 10%),var(--panel));border-radius:30px 30px 0 0;
 box-shadow:0 -10px 40px rgba(0,0,0,.5);border-top:1px solid rgba(255,255,255,.14)}
.f-tel .sheet{border-radius:0;clip-path:polygon(0 0,calc(100% - 26px) 0,100% 26px,100% 100%,0 100%)}
.m-route .sheet{transform:none}
.grab{width:40px;height:5px;border-radius:3px;background:rgba(255,255,255,.25);margin:0 auto 12px}
.hd{display:flex;align-items:center;gap:12px;margin-bottom:12px}
.hd .tt{flex:1}
.hd b{display:block;font-size:19px;font-weight:800}
.hd span{font-size:13px;color:var(--sec)}
.opt{display:flex;align-items:center;gap:14px;padding:11px 14px;border-radius:16px;margin-bottom:8px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.06)}
.f-tel .opt{border-radius:4px}
.opt.sel{background:color-mix(in srgb,var(--acc) 14%,transparent);border-color:color-mix(in srgb,var(--acc) 55%,transparent)}
.opt .tm{font-size:20px;font-weight:800;min-width:74px}
.opt.sel .tm{color:var(--acc)}
.opt .nmx{flex:1;font-size:14px;font-weight:700}
.opt .nmx i{display:block;font-style:normal;font-weight:500;font-size:12px;color:var(--sec);margin-top:2px}
.chips{display:flex;gap:8px;margin:12px 0 14px}
.chip{height:38px;padding:0 15px;font-size:14px;font-weight:700;display:flex;align-items:center;white-space:nowrap;position:relative}
.chip span{position:relative;z-index:2}
.go{width:100%;height:58px;font-size:18px;font-weight:800;letter-spacing:.3px;position:relative;display:flex;align-items:center;justify-content:center}
.go span{position:relative;z-index:2}
/* общая основа кнопок: фон-рамка + заливка во внутреннем слое (так рамка видна и на срезанных углах) */
.b{position:relative;display:grid;place-items:center;transition:transform .35s var(--spring),filter .2s}
.b::before{content:"";position:absolute;inset:1px;border-radius:inherit;clip-path:inherit;z-index:1}
.b:active{transform:scale(.9)}
.f-tel .b:active{transform:translateX(3px) scale(.95)}
.f-depth .b:active{transform:perspective(300px) rotateX(14deg) scale(.95)}
.note{position:absolute;left:14px;right:14px;top:330px;z-index:4;font-size:12.5px;line-height:1.45;color:#fff;background:rgba(0,0,0,.62);border-radius:14px;padding:10px 12px;pointer-events:none;transition:opacity .3s}
.m-route .note{opacity:0}
.note b{color:var(--acc)}
"""

VCSS = {}

# ---------- 1. Чистое стекло: одна ровная полупрозрачная кнопка, тема видна по форме и цвету ----------
VCSS['V1'] = r"""
.b.m{width:54px;height:54px;border-radius:50%;background:rgba(255,255,255,.13);color:var(--text);box-shadow:0 8px 22px rgba(0,0,0,.45)}
.b.m::before{background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 82%,#fff 18%),var(--panel));opacity:.9}
.f-neon .b.m{border-radius:17px;background:color-mix(in srgb,var(--acc) 55%,transparent);box-shadow:0 8px 22px rgba(0,0,0,.45),0 0 14px color-mix(in srgb,var(--acc) 30%,transparent)}
.f-tel .b.m{border-radius:0;clip-path:polygon(0 0,calc(100% - 12px) 0,100% 12px,100% 100%,12px 100%,0 calc(100% - 12px));box-shadow:none}
.f-tel .col{filter:drop-shadow(0 8px 14px rgba(0,0,0,.5))}
.f-tel .b.m::after{content:"";position:absolute;left:9px;top:0;width:18px;height:3px;background:var(--acc);z-index:3}
.f-depth .b.m{box-shadow:0 14px 26px rgba(0,0,0,.6),0 2px 0 rgba(0,0,0,.4)}
.f-depth .b.m::before{background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 70%,#fff 30%),var(--panel) 55%,color-mix(in srgb,var(--panel) 80%,#000 20%))}
.b.m.acc{background:transparent;color:var(--bg)}
.b.m.acc::before{inset:0;background:linear-gradient(135deg,var(--acc),var(--acc2));opacity:1}
.b.m.acc{box-shadow:0 8px 24px color-mix(in srgb,var(--acc) 45%,transparent)}
.f-tel .b.m.acc::after{display:none}
.pill{border-radius:31px;background:rgba(255,255,255,.13);box-shadow:0 10px 28px rgba(0,0,0,.5)}
.pill::before{content:"";position:absolute;inset:1px;border-radius:inherit;background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 85%,#fff 15%),var(--panel));opacity:.92;clip-path:inherit}
.pill>*{position:relative;z-index:2}
.f-neon .pill{border-radius:17px;background:color-mix(in srgb,var(--acc) 55%,transparent);box-shadow:0 10px 28px rgba(0,0,0,.5),0 0 16px color-mix(in srgb,var(--acc) 28%,transparent)}
.f-tel .pill{border-radius:0;clip-path:polygon(0 0,calc(100% - 16px) 0,100% 16px,100% 100%,16px 100%,0 calc(100% - 16px))}
.pill .b.l{width:44px;height:44px;border-radius:50%;color:var(--bg)}
.f-neon .pill .b.l{border-radius:13px}
.f-tel .pill .b.l{border-radius:0;clip-path:polygon(0 0,calc(100% - 9px) 0,100% 9px,100% 100%,9px 100%,0 calc(100% - 9px))}
.pill .b.l::before{inset:0;background:linear-gradient(135deg,var(--acc),var(--acc2))}
.go{border-radius:22px;color:var(--bg);box-shadow:0 12px 30px color-mix(in srgb,var(--acc) 40%,transparent)}
.go::before{content:"";position:absolute;inset:0;border-radius:inherit;background:linear-gradient(135deg,var(--acc),var(--acc2))}
.f-neon .go{border-radius:15px}
.f-tel .go{border-radius:0;clip-path:polygon(0 0,calc(100% - 16px) 0,100% 16px,100% 100%,16px 100%,0 calc(100% - 16px));box-shadow:none}
.go:active{transform:scale(.97)}
.chip{border-radius:19px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);color:var(--sec)}
.f-neon .chip{border-radius:11px}
.f-tel .chip{border-radius:2px}
.chip.on{background:color-mix(in srgb,var(--acc) 18%,transparent);border-color:var(--acc);color:var(--acc)}
.b.x{width:40px;height:40px;border-radius:50%;background:rgba(255,255,255,.08);color:var(--text)}
.f-tel .b.x{border-radius:2px}
.b.x .ic{width:20px;height:20px}
"""

# ---------- 2. Монолит: плотные непрозрачные кнопки, крупная иконка, как на магнитоле ----------
VCSS['V2'] = r"""
.b.m{width:58px;height:58px;border-radius:16px;color:var(--text);background:color-mix(in srgb,var(--panel) 70%,#fff 30%);
 box-shadow:0 5px 0 color-mix(in srgb,var(--panel) 60%,#000 40%),0 10px 20px rgba(0,0,0,.45)}
.b.m::before{background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 80%,#fff 20%),color-mix(in srgb,var(--panel) 92%,#fff 8%))}
.b.m .ic{width:27px;height:27px;stroke-width:2.3}
.b.m:active{transform:translateY(4px);box-shadow:0 1px 0 color-mix(in srgb,var(--panel) 60%,#000 40%),0 4px 10px rgba(0,0,0,.4)}
.f-tel .b.m{border-radius:0;clip-path:polygon(0 0,calc(100% - 12px) 0,100% 12px,100% 100%,12px 100%,0 calc(100% - 12px));box-shadow:none}
.f-tel .col{filter:drop-shadow(0 6px 10px rgba(0,0,0,.55))}
.f-tel .b.m:active{transform:translateY(3px)}
.f-glass .b.m{border-radius:20px}
.b.m.acc{color:var(--bg);background:color-mix(in srgb,var(--acc) 70%,#000 30%);box-shadow:0 5px 0 color-mix(in srgb,var(--acc) 50%,#000 50%),0 10px 22px rgba(0,0,0,.45)}
.b.m.acc::before{background:var(--acc)}
.f-tel .b.m.acc{box-shadow:none}
.pill{border-radius:18px;background:color-mix(in srgb,var(--panel) 92%,#fff 8%);box-shadow:0 5px 0 color-mix(in srgb,var(--panel) 60%,#000 40%),0 12px 26px rgba(0,0,0,.5);border:1px solid rgba(255,255,255,.10)}
.f-glass .pill{border-radius:24px}
.f-tel .pill{border-radius:0;clip-path:polygon(0 0,calc(100% - 16px) 0,100% 16px,100% 100%,16px 100%,0 calc(100% - 16px));box-shadow:none;border:0}
.pill .b.l{width:46px;height:46px;border-radius:12px;color:var(--bg)}
.f-tel .pill .b.l{border-radius:0;clip-path:polygon(0 0,calc(100% - 9px) 0,100% 9px,100% 100%,9px 100%,0 calc(100% - 9px))}
.pill .b.l::before{inset:0;background:var(--acc)}
.go{border-radius:14px;color:var(--bg);text-transform:uppercase;letter-spacing:1.2px;background:color-mix(in srgb,var(--acc) 55%,#000 45%);
 box-shadow:0 5px 0 color-mix(in srgb,var(--acc) 45%,#000 55%);transition:transform .15s,box-shadow .15s}
.go::before{content:"";position:absolute;inset:0 0 2px 0;border-radius:inherit;background:var(--acc)}
.go:active{transform:translateY(4px);box-shadow:0 1px 0 color-mix(in srgb,var(--acc) 45%,#000 55%)}
.f-glass .go{border-radius:20px}
.f-tel .go{border-radius:0;clip-path:polygon(0 0,calc(100% - 16px) 0,100% 16px,100% 100%,16px 100%,0 calc(100% - 16px));box-shadow:none}
.chip{border-radius:10px;background:color-mix(in srgb,var(--panel) 80%,#fff 20%);color:var(--text)}
.f-tel .chip{border-radius:0}
.chip.on{background:var(--acc);color:var(--bg)}
.b.x{width:42px;height:42px;border-radius:12px;background:color-mix(in srgb,var(--panel) 75%,#fff 25%);color:var(--text)}
.f-tel .b.x{border-radius:0}
.b.x .ic{width:21px;height:21px}
"""

# ---------- 3. Капсула: меню, музыка и антирадар в одной стеклянной капсуле, поиск — отдельная яркая кнопка ----------
VCSS['V3'] = VCSS['V1'] + r"""
.cap{position:relative;display:flex;flex-direction:column;width:56px;border-radius:28px;background:rgba(255,255,255,.14);box-shadow:0 10px 26px rgba(0,0,0,.5)}
.cap::before{content:"";position:absolute;inset:1px;border-radius:inherit;clip-path:inherit;background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 82%,#fff 18%),var(--panel));opacity:.92}
.cap .b.c{width:56px;height:56px;color:var(--text);border-radius:28px}
.cap .b.c::before{display:none}
.cap .b.c:active{transform:scale(.86)}
.cap .sep{position:relative;z-index:2;height:1px;margin:0 12px;background:rgba(255,255,255,.12)}
.f-neon .cap{border-radius:18px;background:color-mix(in srgb,var(--acc) 55%,transparent);box-shadow:0 10px 26px rgba(0,0,0,.5),0 0 16px color-mix(in srgb,var(--acc) 28%,transparent)}
.f-neon .cap .sep{background:color-mix(in srgb,var(--acc) 30%,transparent)}
.f-tel .cap{border-radius:0;box-shadow:none;clip-path:polygon(0 0,calc(100% - 14px) 0,100% 14px,100% 100%,14px 100%,0 calc(100% - 14px))}
.f-tel .cap::after{content:"";position:absolute;left:10px;top:0;width:20px;height:3px;background:var(--acc);z-index:3}
.f-depth .cap{box-shadow:0 16px 30px rgba(0,0,0,.6),0 2px 0 rgba(0,0,0,.4)}
.f-depth .cap::before{background:linear-gradient(180deg,color-mix(in srgb,var(--panel) 70%,#fff 30%),var(--panel) 40%,color-mix(in srgb,var(--panel) 80%,#000 20%))}
.col .b.m.acc{width:56px;height:56px}
"""

# ---------- 4. Светящиеся кольца: тёмная кнопка с цветным кольцом и подсветкой, как игровой HUD ----------
VCSS['V4'] = r"""
.b.m{width:54px;height:54px;border-radius:50%;color:var(--acc);background:linear-gradient(135deg,var(--acc),var(--acc2));
 box-shadow:0 0 16px color-mix(in srgb,var(--acc) 45%,transparent),0 8px 18px rgba(0,0,0,.5)}
.b.m::before{inset:2px;background:radial-gradient(circle at 50% 35%,color-mix(in srgb,var(--panel) 75%,#fff 10%),color-mix(in srgb,var(--bg) 88%,#000 12%));opacity:.94}
.f-neon .b.m::after{content:"";position:absolute;inset:-5px;border-radius:50%;border:1px solid color-mix(in srgb,var(--acc2) 70%,transparent)}
.f-tel .b.m{border-radius:0;clip-path:polygon(25% 2%,75% 2%,100% 50%,75% 98%,25% 98%,0 50%);box-shadow:none;width:58px}
.f-tel .b.m::before{clip-path:polygon(25% 2%,75% 2%,100% 50%,75% 98%,25% 98%,0 50%);inset:2.5px}
.f-tel .col{filter:drop-shadow(0 0 8px color-mix(in srgb,var(--acc) 45%,transparent))}
.f-depth .b.m{background:linear-gradient(180deg,#fff,var(--acc) 35%,color-mix(in srgb,var(--acc2) 60%,#000 40%));box-shadow:0 12px 22px rgba(0,0,0,.65),0 0 14px color-mix(in srgb,var(--acc) 35%,transparent)}
.f-depth .b.m::before{inset:3px;background:radial-gradient(circle at 50% 30%,color-mix(in srgb,var(--panel) 65%,#fff 15%),color-mix(in srgb,var(--bg) 80%,#000 20%) 75%)}
.f-glass .b.m{box-shadow:0 0 20px color-mix(in srgb,var(--acc) 40%,transparent),0 8px 18px rgba(0,0,0,.4)}
.b.m.acc{color:var(--bg)}
.b.m.acc::before{background:linear-gradient(135deg,var(--acc),var(--acc2));inset:4px;opacity:1}
.b.m.acc{background:color-mix(in srgb,var(--acc) 60%,#fff 40%);box-shadow:0 0 24px color-mix(in srgb,var(--acc) 65%,transparent),0 8px 18px rgba(0,0,0,.5)}
.pill{border-radius:31px;background:linear-gradient(90deg,var(--acc),var(--acc2));box-shadow:0 0 18px color-mix(in srgb,var(--acc) 35%,transparent),0 10px 24px rgba(0,0,0,.5)}
.pill::before{content:"";position:absolute;inset:2px;border-radius:inherit;clip-path:inherit;background:color-mix(in srgb,var(--bg) 85%,var(--panel) 15%);opacity:.95}
.pill>*{position:relative;z-index:2}
.f-tel .pill{border-radius:0;clip-path:polygon(16px 0,calc(100% - 16px) 0,100% 50%,calc(100% - 16px) 100%,16px 100%,0 50%);box-shadow:none}
.pill .b.l{width:44px;height:44px;border-radius:50%;color:var(--bg)}
.f-tel .pill .b.l{border-radius:0;clip-path:polygon(25% 2%,75% 2%,100% 50%,75% 98%,25% 98%,0 50%);width:48px}
.pill .b.l::before{inset:0;background:linear-gradient(135deg,var(--acc),var(--acc2))}
.go{border-radius:29px;color:var(--bg);text-transform:uppercase;letter-spacing:2px;box-shadow:0 0 0 2px var(--bg),0 0 0 4px color-mix(in srgb,var(--acc) 70%,transparent),0 0 30px color-mix(in srgb,var(--acc) 55%,transparent)}
.go::before{content:"";position:absolute;inset:0;border-radius:inherit;background:linear-gradient(90deg,var(--acc),var(--acc2))}
.f-tel .go{border-radius:0;clip-path:polygon(18px 0,calc(100% - 18px) 0,100% 50%,calc(100% - 18px) 100%,18px 100%,0 50%);box-shadow:none}
.go:active{transform:scale(.97)}
.chip{border-radius:19px;color:var(--acc);background:transparent;border:1.5px solid color-mix(in srgb,var(--acc) 55%,transparent)}
.f-tel .chip{border-radius:0}
.chip.on{background:color-mix(in srgb,var(--acc) 22%,transparent);border-color:var(--acc);box-shadow:0 0 12px color-mix(in srgb,var(--acc) 45%,transparent);color:var(--text)}
.b.x{width:40px;height:40px;border-radius:50%;color:var(--acc);border:1.5px solid color-mix(in srgb,var(--acc) 60%,transparent)}
.f-tel .b.x{border-radius:0}
.b.x .ic{width:20px;height:20px}
"""

NOTES = {
    'V1': '<b>Чистое стекло.</b> Без текстур и полосок: ровная полупрозрачная кнопка. Тема видна по форме (круг, скруглённый квадрат, срезанные углы) и цвету. Нажми «Куда едем?» — откроется панель маршрута.',
    'V2': '<b>Монолит.</b> Плотные непрозрачные кнопки чуть крупнее, иконка жирнее, при нажатии кнопка «вдавливается». Лучше всего видно на солнце и на магнитоле.',
    'V3': '<b>Капсула.</b> То же стекло, но меню, музыка и антирадар собраны в одну капсулу — на карте меньше отдельных плиток. Поиск — отдельная яркая кнопка.',
    'V4': '<b>Светящиеся кольца.</b> Тёмная кнопка с цветным кольцом и подсветкой, как в игре. У гоночных тем — шестиугольники.',
}

def map_svg():
    return """
    <svg class="base" viewBox="0 0 390 844" aria-hidden="true">
      <defs><radialGradient id="vg" cx="50%" cy="50%" r="75%"><stop offset="55%" stop-color="#000" stop-opacity="0"/><stop offset="100%" stop-color="#000" stop-opacity=".7"/></radialGradient></defs>
      <rect x="0" y="0" width="390" height="844" fill="var(--bg)"/>
      <path d="M-20 470 C 80 430, 140 520, 230 470 S 380 420, 420 450" fill="none" stroke="var(--water)" stroke-width="34"/>
      <rect x="22" y="650" width="96" height="70" rx="10" fill="var(--land)"/>
      <rect x="236" y="120" width="120" height="84" rx="12" fill="var(--land)"/>
      <rect x="228" y="520" width="140" height="60" rx="10" fill="var(--land)"/>
      <g class="rd">
        <path d="M-20 620 L410 560" stroke-width="16"/><path d="M60 -20 L140 860" stroke-width="12"/>
        <path d="M262 -20 L332 860" stroke-width="12"/><path d="M-20 300 L410 262" stroke-width="12"/>
        <path d="M-20 130 L410 168" stroke-width="10"/><path d="M195 860 L203 610 L152 425 L288 305 L300 185 L310 -20" stroke-width="14"/>
        <path d="M-20 740 L410 700" stroke-width="7"/><path d="M-20 380 L410 350" stroke-width="6"/>
        <path d="M200 -20 L170 860" stroke-width="6"/><path d="M-20 220 L410 210" stroke-width="5"/>
      </g>
      <path class="rglow" d="M195 640 L203 610 L152 425 L288 305 L300 185"/>
      <path class="route" d="M195 640 L203 610 L152 425 L288 305 L300 185"/>
      <circle cx="300" cy="185" r="8" fill="var(--acc)"/><circle cx="300" cy="185" r="15" fill="none" stroke="var(--acc)" stroke-width="2" opacity=".6"/>
      <rect x="0" y="0" width="390" height="844" fill="url(#vg)"/>
    </svg>
    <svg class="puck" viewBox="0 0 30 34" aria-hidden="true"><path d="M15 1 L28 32 L15 25 L2 32 Z" fill="#fff" stroke="var(--acc)" stroke-width="2" stroke-linejoin="round"/></svg>
    """

def column(v):
    menu = ic('menu', 'ic-menu') + ic('star', 'ic-star')
    if v == 'V3':
        return f"""
  <div class="col">
    <div class="cap">
      <button class="b c" aria-label="Меню">{menu}</button><div class="sep"></div>
      <button class="b c" aria-label="Музыка">{ic('music')}</button><div class="sep"></div>
      <button class="b c" aria-label="Антирадар">{ic('radar')}</button>
    </div>
    <button class="b m acc" aria-label="Поиск" onClick="{{{{openRoute}}}}">{ic('search')}</button>
  </div>"""
    return f"""
  <div class="col">
    <button class="b m" aria-label="Меню">{menu}</button>
    <button class="b m acc" aria-label="Поиск" onClick="{{{{openRoute}}}}">{ic('search')}</button>
    <button class="b m" aria-label="Музыка">{ic('music')}</button>
    <button class="b m" aria-label="Антирадар">{ic('radar')}</button>
  </div>"""

def body(v, dc=True):
    loc = ic('loc', 'ic-loc') + ic('heart', 'ic-heart')
    lb_cls = 'b m locb' if v != 'V3' else 'b m locb'
    sw = """<div class="tp"><div class="dots"><sc-for list="{{swatches}}" as="s" hint-placeholder-count="8"><button class="{{s.cls}}" style="background: {{s.color}}" aria-label="{{s.name}}" onClick="{{s.pick}}"></button></sc-for></div><div class="nm">{{t.name}}</div></div>"""
    opts = """<sc-for list="{{opts}}" as="o" hint-placeholder-count="3"><div class="{{o.cls}}" onClick="{{o.pick}}"><span class="tm">{{o.tm}}</span><span class="nmx">{{o.name}}<i>{{o.meta}}</i></span></div></sc-for>"""
    chips = """<sc-for list="{{chips}}" as="c" hint-placeholder-count="3"><button class="{{c.cls}}" onClick="{{c.pick}}"><span>{{c.label}}</span></button></sc-for>"""
    return f"""<div class="{{{{rootCls}}}}" style="--acc: {{{{t.acc}}}}; --acc2: {{{{t.acc2}}}}; --bg: {{{{t.bg}}}}; --panel: {{{{t.panel}}}}; --text: {{{{t.text}}}}; --sec: {{{{t.sec}}}}; --road: {{{{t.road}}}}; --land: {{{{t.land}}}}; --water: {{{{t.water}}}}">
  <div class="map">{map_svg()}</div>
  {sw}
  {column(v)}

  <button class="pill" onClick="{{{{openRoute}}}}">{ic('search','sic')}<span class="t">Куда едем?</span><span class="b l" aria-label="Где я">{loc}</span></button>
  <button class="{lb_cls}" aria-label="Где я">{loc}</button>
  <div class="sheet">
    <div class="grab"></div>
    <div class="hd"><div class="tt"><b>ул. Советская, 21</b><span>3 варианта · с учётом светофоров</span></div><button class="b x" aria-label="Закрыть" onClick="{{{{toMap}}}}">{ic('x')}</button></div>
    {opts}
    <div class="chips">{chips}</div>
    <button class="go"><span>Поехали</span></button>
  </div>
</div>"""

JS = """
class Component extends DCLogic {
  constructor(props) {
    super(props);
    this.state = { mode: 'map', theme: 0, sel: 0, chip: 0 };
  }
  renderVals() {
    const T = %s;
    const s = this.state;
    const t = T[s.theme];
    const O = [
      { tm: '18 мин', name: 'Самый быстрый', meta: '9,4 км · прибытие 18:42' },
      { tm: '21 мин', name: 'Без камер', meta: '10,1 км · 0 камер' },
      { tm: '22 мин', name: 'Экономия бензина', meta: '9,8 км · 0,7 л' }
    ];
    const C = ['Сейчас', 'Через 15 мин', 'Через 30 мин'];
    return {
      t,
      rootCls: 'ph f-' + t.f + ' th' + s.theme + ' m-' + s.mode,
      opts: O.map((o, i) => ({ ...o, cls: 'opt' + (i === s.sel ? ' sel' : ''), pick: () => this.setState({ sel: i }) })),
      chips: C.map((c, i) => ({ label: c, cls: 'chip' + (i === s.chip ? ' on' : ''), pick: () => this.setState({ chip: i }) })),
      swatches: T.map((x, i) => ({ name: x.name, color: 'linear-gradient(135deg,' + x.acc + ',' + x.acc2 + ')', cls: 'sw' + (i === s.theme ? ' on' : ''), pick: () => this.setState({ theme: i }) })),
      openRoute: () => this.setState({ mode: 'route' }),
      toMap: () => this.setState({ mode: 'map' })
    };
  }
}
""" % json.dumps(T, ensure_ascii=False)

HEAD = """<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&amp;display=swap" rel="stylesheet">"""

def dc_file(v):
    return f"""<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>{V[v]['title']}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
{HEAD}
<style>{BASE_CSS}{VCSS[v]}</style>
</helmet>
{body(v)}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":390,"height":844}}}}'>{JS}</script>
</body>
</html>
"""

def static_file(v, ti, mode):
    """Простая подстановка шаблона для локального снимка."""
    import re
    t = T[ti]
    h = body(v)
    O = [('18 мин', 'Самый быстрый', '9,4 км · прибытие 18:42'), ('21 мин', 'Без камер', '10,1 км · 0 камер'), ('22 мин', 'Экономия бензина', '9,8 км · 0,7 л')]
    opts = ''.join(f'<div class="opt{" sel" if i == 0 else ""}"><span class="tm">{a}</span><span class="nmx">{b}<i>{c}</i></span></div>' for i, (a, b, c) in enumerate(O))
    chips = ''.join(f'<button class="chip{" on" if i == 0 else ""}"><span>{c}</span></button>' for i, c in enumerate(['Сейчас', 'Через 15 мин', 'Через 30 мин']))
    sws = ''.join(f'<button class="sw{" on" if i == ti else ""}" style="background: linear-gradient(135deg,{x["acc"]},{x["acc2"]})"></button>' for i, x in enumerate(T))
    h = re.sub(r'<sc-for list="\{\{opts\}\}".*?</sc-for>', opts, h, flags=re.S)
    h = re.sub(r'<sc-for list="\{\{chips\}\}".*?</sc-for>', chips, h, flags=re.S)
    h = re.sub(r'<sc-for list="\{\{swatches\}\}".*?</sc-for>', sws, h, flags=re.S)
    h = h.replace('{{rootCls}}', f'ph f-{t["f"]} th{ti} m-{mode}')
    for k, val in t.items():
        h = h.replace('{{t.' + k + '}}', str(val))
    h = re.sub(r'onClick="\{\{[^}]*\}\}"', '', h)
    return f"<!doctype html><html><head><meta charset='utf-8'>{HEAD}<style>{BASE_CSS}{VCSS[v]}</style></head><body>{h}</body></html>"

if __name__ == '__main__':
    for v in V:
        open(os.path.join(OUT, V[v]['file']), 'w').write(dc_file(v))
        for ti in range(8):
            for mode in ('map', 'route'):
                open(os.path.join(PREV, f'{v}_{ti}_{mode}.html'), 'w').write(static_file(v, ti, mode))
    canvas = {
        "v": 3, "createdOnFiles": {"v": 1, "at": "2026-10-14T06:00:00Z"},
        "title": "Штурман — варианты кнопок", "launch": {"view": "canvas"}, "pages": [],
        "boards": {V[v]['file']: {"x": i * 470, "y": 0, "w": 390, "h": 844, "title": V[v]['title'], "is_interactive": True, "radius": 44} for i, v in enumerate(V)},
        "order": [V[v]['file'] for v in V],
        "notes": {"t1": {"x": 0, "y": -300, "text": "Штурман: 4 варианта кнопок — переключай темы цветными кружками справа вверху", "kind": "title1", "maxW": 1800}},
        "designSystems": []
    }
    json.dump(canvas, open(os.path.join(OUT, 'canvas.json'), 'w'), ensure_ascii=False)
    print('ok')
