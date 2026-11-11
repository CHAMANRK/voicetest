package com.chaman.voicetest;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.content.Context;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;

/** openWakeWord pipeline: raw 16k audio -> melspectrogram -> embedding -> wake word model. */
public class WakeWordDetector {
    public static final int CHUNK = 1280;          // 80 ms @ 16 kHz
    private static final int CTX = 480;            // extra samples of context for mel model
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession mel, emb, ww;
    private final String melIn, embIn, wwIn;
    private final float[] raw = new float[CHUNK + CTX];
    private final ArrayList<float[]> melBuf = new ArrayList<>();
    private final ArrayList<float[]> feat = new ArrayList<>();
    private int warm = 0;

    public WakeWordDetector(Context c, String wakeModelFile) throws Exception {
        mel = load(c, "melspectrogram.onnx");
        emb = load(c, "embedding_model.onnx");
        ww = load(c, wakeModelFile);
        melIn = mel.getInputNames().iterator().next();
        embIn = emb.getInputNames().iterator().next();
        wwIn = ww.getInputNames().iterator().next();
        for (int i = 0; i < 76; i++) { float[] f = new float[32]; java.util.Arrays.fill(f, 1f); melBuf.add(f); }
        for (int i = 0; i < 16; i++) feat.add(new float[96]);
    }

    private OrtSession load(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open("models/" + name)) {
            byte[] b = in.readAllBytes();
            return env.createSession(b, new OrtSession.SessionOptions());
        }
    }

    /** Feed exactly CHUNK int16 samples, returns wake score 0..1. */
    public float process(short[] chunk) throws Exception {
        System.arraycopy(raw, CHUNK, raw, 0, CTX);
        for (int i = 0; i < CHUNK; i++) raw[CTX + i] = chunk[i];

        // 1) mel spectrogram
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(raw), new long[]{1, raw.length});
             OrtSession.Result r = mel.run(Collections.singletonMap(melIn, t))) {
            float[][][][] out = (float[][][][]) r.get(0).getValue();
            for (float[] f : out[0][0]) {
                float[] s = new float[32];
                for (int i = 0; i < 32; i++) s[i] = f[i] / 10f + 2f;
                melBuf.add(s);
            }
        }
        while (melBuf.size() > 200) melBuf.remove(0);

        // 2) embedding from last 76 mel frames
        FloatBuffer eb = FloatBuffer.allocate(76 * 32);
        for (int i = melBuf.size() - 76; i < melBuf.size(); i++) eb.put(melBuf.get(i));
        eb.rewind();
        try (OnnxTensor t = OnnxTensor.createTensor(env, eb, new long[]{1, 76, 32, 1});
             OrtSession.Result r = emb.run(Collections.singletonMap(embIn, t))) {
            float[][][][] out = (float[][][][]) r.get(0).getValue();
            feat.add(out[0][0][0].clone());
        }
        while (feat.size() > 16) feat.remove(0);

        // 3) wake word model on last 16 embeddings
        FloatBuffer wb = FloatBuffer.allocate(16 * 96);
        for (float[] f : feat) wb.put(f);
        wb.rewind();
        float score;
        try (OnnxTensor t = OnnxTensor.createTensor(env, wb, new long[]{1, 16, 96});
             OrtSession.Result r = ww.run(Collections.singletonMap(wwIn, t))) {
            score = ((float[][]) r.get(0).getValue())[0][0];
        }
        if (warm < 25) { warm++; return 0f; }   // ignore initial buffer fill
        return score;
    }

    public void close() {
        try { mel.close(); emb.close(); ww.close(); } catch (Exception ignored) {}
    }
}
