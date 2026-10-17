"""Убирает надпись «ШТУРМАН» из видео-заставки APEX: на кадрах с надписью её место закрывается
фоном из тех же строк левее (сканлайны горизонтальные, поэтому фон совпадает), с поправкой яркости
на виньетку и мягкими краями."""
import sys, glob
import numpy as np
from PIL import Image

def feather(h, w, r):
    yy = np.minimum(np.arange(h), np.arange(h)[::-1])[:, None]
    xx = np.minimum(np.arange(w), np.arange(w)[::-1])[None, :]
    return np.clip(np.minimum(yy, xx) / r, 0, 1)

def run(folder, f0, f1, box, src_x, ref_rows):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    wt = feather(h, w, 6.0)[..., None]
    fs = sorted(glob.glob(folder + '/*.png'))
    for i in range(f0 - 1, f1):
        a = np.asarray(Image.open(fs[i]).convert('RGB'), dtype=np.float32)
        src = a[y0:y1, src_x:src_x + w].copy()
        # поправка по столбцам: виньетка даёт плавный перепад яркости слева направо
        refs = [a[r0:r1] for (r0, r1) in ref_rows]
        tref = np.concatenate([r[:, x0:x1] for r in refs]).mean(axis=0)
        sref = np.concatenate([r[:, src_x:src_x + w] for r in refs]).mean(axis=0)
        off = tref - sref
        k = 31
        pad = np.pad(off, ((k // 2, k // 2), (0, 0)), mode='edge')
        off = np.stack([np.convolve(pad[:, c], np.ones(k) / k, mode='valid') for c in range(3)], axis=1)
        src += off[None, :, :]
        tgt = a[y0:y1, x0:x1]
        a[y0:y1, x0:x1] = tgt * (1 - wt) + src * wt
        Image.fromarray(np.clip(a + 0.5, 0, 255).astype(np.uint8)).save(fs[i])
    print(folder, 'patched', f1 - f0 + 1)

if __name__ == '__main__':
    run('v', 340, 450, (378, 1092, 728, 1150), 22, [(1055, 1088), (1155, 1190)])
    run('h', 340, 450, (498, 446, 798, 494), 190, [(438, 445), (496, 503)])
