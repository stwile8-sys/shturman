"""Piper (голос irina, medium) без пакета piper: фонемы через espeak-ng (форк rhasspy), модель через onnxruntime.
Повторяет piper: фразы по предложениям, BOS/EOS/паузы между фонемами, нормализация по пику.
Запуск: python3 say.py <out.wav> <текст> [length_scale] [silence_s]
"""
import json, subprocess, sys, unicodedata, wave
import numpy as np
import onnxruntime as ort

BASE = __file__.rsplit('/', 2)[0]
MODEL = '/tmp/voices/ru-irinia-medium.onnx'
CFG = json.load(open(MODEL + '.json'))
IDS = CFG['phoneme_id_map']
INF = CFG['inference']
SR = CFG['audio']['sample_rate']
_sess = None

def sess():
    global _sess
    if _sess is None:
        o = ort.SessionOptions(); o.intra_op_num_threads = 4
        _sess = ort.InferenceSession(MODEL, sess_options=o, providers=['CPUExecutionProvider'])
    return _sess

def phonemize(text):
    out = subprocess.run([BASE + '/tools/phon', BASE + '/espeak-ng/build/espeak-ng-data', text],
                         capture_output=True, text=True, check=True).stdout
    sentences, cur = [], None
    for line in out.split('\n'):
        if not line:
            continue
        punct, sent, ph = line.split('\t', 2)
        if cur is None:
            cur = []; sentences.append(cur)
        ph = unicodedata.normalize('NFD', ph)
        flag = False
        for ch in ph:
            if flag:
                if ch == ')': flag = False
            elif ch == '(':
                flag = True
            else:
                cur.append(ch)
        for ch in punct:
            cur.append(ch)
        if sent == '1':
            cur = None
    return sentences

def to_ids(ph):
    ids = list(IDS['^']) + list(IDS['_'])
    for p in ph:
        if p in IDS:
            ids += IDS[p] + IDS['_']
    ids += IDS['$']
    return ids

def synth(text, length_scale=None, silence=0.0, noise_scale=None, noise_w=None):
    ls = INF['length_scale'] if length_scale is None else length_scale
    ns = INF['noise_scale'] if noise_scale is None else noise_scale
    nw = INF['noise_w'] if noise_w is None else noise_w
    chunks = []
    for ph in phonemize(text):
        ids = np.array([to_ids(ph)], dtype=np.int64)
        audio = sess().run(None, {
            'input': ids,
            'input_lengths': np.array([ids.shape[1]], dtype=np.int64),
            'scales': np.array([ns, ls, nw], dtype=np.float32),
        })[0].squeeze()
        audio = audio * (32767.0 / max(0.01, float(np.max(np.abs(audio)))))
        chunks.append(np.clip(audio, -32767, 32767).astype(np.int16))
        if silence > 0:
            chunks.append(np.zeros(int(SR * silence), dtype=np.int16))
    return np.concatenate(chunks)

def write_wav(path, pcm):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

if __name__ == '__main__':
    out, text = sys.argv[1], sys.argv[2]
    ls = float(sys.argv[3]) if len(sys.argv) > 3 else None
    sil = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    pcm = synth(text, ls, sil)
    write_wav(out, pcm)
    print(out, len(pcm) / SR)
