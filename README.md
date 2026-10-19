# Модель голоса навигатора APEX

`ru-irinia-medium.tar.gz` — модель Piper «irina» (русский, женский, medium) из релиза Piper v0.0.2:
`ru-irinia-medium.onnx`, `ru-irinia-medium.onnx.json`, `MODEL_CARD` (данные RHVoice, лицензия GPLv2).
Ею записаны все голосовые подсказки приложения. Нужна только для новых фраз тем же голосом —
как это делать, написано в `docs/voice/README.md` основной ветки.

Достать: `git fetch origin voice-model && git show origin/voice-model:ru-irinia-medium.tar.gz > ru-irinia-medium.tar.gz`
