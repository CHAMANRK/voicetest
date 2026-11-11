# Chaman Voice Test (v0.1)
Wake word ("hey jarvis") offline via openWakeWord + ONNX Runtime, mic foreground service, sensitivity slider.
Next: v0.2 STT (SpeechRecognizer hi-IN/en-IN), v0.3 TTS engines (Android / Piper / cloud neural).

Build: push to GitHub -> Actions -> artifact "ChamanVoiceTest-debug" (models are downloaded in the workflow).
Screen-off test: Start listening -> screen lock -> phrase bolo -> unlock karke log me detections dekho.
