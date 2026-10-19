"""Готовые фразы голоса тем же голосом Piper (irina), что и прежние записи.
Как прежние: без паузы на запятых, пик −1 дБ, тишина в начале до 0,02 с и в конце 0,08 с, ffmpeg libvorbis по умолчанию.
Имя файла: v + первые 10 знаков md5 текста. Пишет файлы в <res/raw> и строки для VoiceClips.kt в <out.txt>.
Запуск: python3 -I make_clips.py <res/raw> <out.txt>
"""
import hashlib, os, subprocess, sys, tempfile
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import say  # noqa: E402

ORD = ['первый', 'второй', 'третий', 'четвёртый', 'пятый']
HOW = ['резко налево', 'налево', 'плавно налево', 'резко направо', 'направо', 'плавно направо']


def phrases():
    out = []
    for n in range(50, 601, 50):
        for o in ORD:
            out.append(f'Через {n} метров, круговое движение, {o} съезд')
        out.append(f'Через {n} метров, круговое движение')
    for o in ORD:
        out.append(f'Через километр, круговое движение, {o} съезд')
        out.append(f'На кольце {o} съезд')
    out.append('Через километр, круговое движение')
    out.append('Круговое движение')
    for h in HOW:
        out.append(f'Через километр, {h}')
    out.append('Осторожно, возможен гололёд')
    for v in (20, 30, 40, 50, 60, 70):
        out.append(f'Впереди крутой поворот, сбавьте до {v}')
    return out


def trim(pcm, sr):
    a = np.abs(pcm.astype(np.float32))
    thr = 0.02 * a.max()
    idx = np.where(a > thr)[0]
    start = max(0, idx[0] - int(0.02 * sr))
    end = min(len(pcm), idx[-1] + int(0.08 * sr))
    return pcm[start:end]


def main():
    raw, listing = sys.argv[1], sys.argv[2]
    lines = []
    for text in phrases():
        name = 'v' + hashlib.md5(text.encode()).hexdigest()[:10]
        pcm = say.synth(text.replace(',', ''))
        pcm = trim(pcm, say.SR)
        pcm = (pcm.astype(np.float32) * 0.885).astype(np.int16)
        with tempfile.TemporaryDirectory() as td:
            wav = os.path.join(td, 'a.wav')
            say.write_wav(wav, pcm)
            subprocess.run(['ffmpeg', '-v', 'quiet', '-y', '-i', wav, '-c:a', 'libvorbis', os.path.join(raw, name + '.ogg')], check=True)
        lines.append(f'        "{text}" to R.raw.{name},')
        print(f'{name} {len(pcm) / say.SR:.2f}s {text}')
    open(listing, 'w').write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
