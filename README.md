# Chaman Voice Test (v0.1)
Wake word ("hey jarvis") offline via openWakeWord + ONNX Runtime, mic foreground service, sensitivity slider.
Next: v0.2 STT (SpeechRecognizer hi-IN/en-IN), v0.3 TTS engines (Android / Piper / cloud neural).

Build: push to GitHub -> Actions -> artifact "ChamanVoiceTest-debug" (models are downloaded in the workflow).
Screen-off test: Start listening -> screen lock -> phrase bolo -> unlock karke log me detections dekho.

## Signing
`app/chaman-test.jks` repo me committed hai (personal test key). Har build same key se sign hota hai,
isliye naya APK purane ke upar update install ho jata hai, uninstall nahi karna padta.
Is key ko Play Store ke liye use mat karna. Isko repo se delete/change mat karna, warna phir uninstall karna padega.
