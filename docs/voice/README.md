# Записи голоса навигатора

Все подсказки — готовые записи в `app/src/main/res/raw/v*.ogg` (на Huawei нет синтезатора речи).
Голос — Piper, русская женская модель **irina (medium)**, `ru-irinia-medium.onnx` + `.onnx.json`
(архив `voice-ru-irinia-medium.tar.gz` из релиза Piper v0.0.2, репозиторий rhasspy/piper). Другой моделью не заменять.
Имя файла — `v` + первые 10 знаков md5 текста фразы; фраза и файл связаны в `routing/VoiceClips.kt`.

Как сделаны (чтобы новые звучали так же, как прежние):

- фонемы — espeak-ng из форка `rhasspy/espeak-ng` (тот же, что в piper-phonemize), функция
  `espeak_TextToPhonemesWithTerminator`; `phon.c` — маленькая программа над собранной библиотекой;
- текст без запятых (в прежних записях пауз на запятых нет), параметры модели по умолчанию
  (noise 0.667, length 1.0, noise_w 0.8);
- громкость: нормализация по пику, затем ×0.885 (около −1 дБ); тишина в начале до 0,02 с, в конце 0,08 с;
- кодирование: `ffmpeg -i a.wav -c:a libvorbis a.ogg` (настройки по умолчанию, 22 050 Гц, моно).

Сборка: `cmake -B build -DUSE_ASYNC=OFF -DUSE_MBROLA=OFF -DUSE_LIBSONIC=OFF -DUSE_LIBPCAUDIO=OFF -DUSE_SPEECHPLAYER=OFF`
в папке espeak-ng, затем
`gcc -O2 -o phon phon.c -I<espeak>/src/include -I<espeak>/src/libespeak-ng <espeak>/build/src/libespeak-ng/libespeak-ng.a <espeak>/build/src/ucd-tools/libucd.a -lm -lpthread`.
Пути к модели и данным espeak — в начале `say.py`. Новые фразы — в `phrases()` в `make_clips.py`;
запуск `python3 -I make_clips.py app/src/main/res/raw out.txt`, строки из `out.txt` — в `VoiceClips.kt`.
Тест `TurnPhraseTest` проверяет, что на каждую фразу о повороте есть запись.
