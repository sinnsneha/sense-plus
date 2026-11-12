package com.senseplus.sense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisualAssistActivity extends AppCompatActivity {

    private enum Mode { TEXT, LABELS }

    private PreviewView previewView;
    private TextView tvRecognized;
    private TextToSpeech tts;
    private TextRecognizer recognizer;
    private ImageLabeler labeler;
    private ExecutorService cameraExecutor;
    private static final int CAM_REQ = 300;

    private volatile Mode mode = Mode.TEXT;
    private final LabelAnnouncer labelAnnouncer = new LabelAnnouncer();
    private static final long LABEL_INTERVAL_MS = 500;
    private long lastLabelAnalysisMs = 0;

    // Auto-read state
    private String latestClean = "";
    private String previousText = "";
    private String lastSpokenText = "";
    private long lastChangeTime = 0;
    private static final long STABLE_MS = 1200;   // text must hold steady this long before auto-reading

    // Pip (visual look): text found → alert for this long, then idle
    private static final long FOUND_ALERT_MS = 1500;
    private DuckView pip;
    private boolean cameraDenied;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pipBackToIdle = () -> {
        if (!cameraDenied) pip.setExpression(DuckLooks.Expression.IDLE, true);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visual_assist);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.visualContent));
        Ui.bindBack(this);

        previewView = findViewById(R.id.previewView);
        tvRecognized = findViewById(R.id.tvRecognized);
        Button btnSpeak = findViewById(R.id.btnSpeak);
        MaterialButtonToggleGroup modeToggle = findViewById(R.id.modeToggle);
        pip = findViewById(R.id.pipVisual);
        pip.setExpression(DuckLooks.Expression.IDLE, false); // scanning
        roundPreview();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    handler.removeCallbacks(pipBackToIdle);
                    pip.setTalking(true);
                });
            }

            @Override public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    pip.setTalking(false);
                    pipBackToIdle.run();
                });
            }

            @Override public void onError(String utteranceId) {
                runOnUiThread(() -> pip.setTalking(false));
            }
        });
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();

        modeToggle.check(R.id.btnReadText);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnReadText) {
                mode = Mode.TEXT;
                showPlaceholder(R.string.visual_placeholder);
                speak("Read text");
            } else {
                mode = Mode.LABELS;
                showPlaceholder(R.string.visual_placeholder_objects);
                speak("What's around me");
            }
        });

        // Manual re-read (for a sighted helper, or to hear it again)
        btnSpeak.setOnClickListener(v -> {
            if (!latestClean.isEmpty()) speak(latestClean);
            else Toast.makeText(this, "Point the camera at some text first", Toast.LENGTH_SHORT).show();
        });
        Ui.pressFeedback(btnSpeak);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAM_REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAM_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission is needed for Visual Assist", Toast.LENGTH_SHORT).show();
            cameraDenied = true;
            pip.setExpression(DuckLooks.Expression.CONCERNED, true);
            showPlaceholder(R.string.visual_no_camera);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyze);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void analyze(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        if (mode == Mode.LABELS) {
            long now = System.currentTimeMillis();
            if (now - lastLabelAnalysisMs < LABEL_INTERVAL_MS) {
                imageProxy.close();
                return;
            }
            lastLabelAnalysisMs = now;
        }

        InputImage image;
        try {
            image = InputImage.fromMediaImage(
                    mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        } catch (Exception e) {
            imageProxy.close();
            return;
        }

        if (mode == Mode.TEXT) {
            processText(image, imageProxy);
        } else {
            processLabels(image, imageProxy);
        }
    }

    private void processText(InputImage image, ImageProxy imageProxy) {
        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String clean = result.getText().replaceAll("\\s+", " ").trim();
                    latestClean = clean;

                    runOnUiThread(() -> {
                        if (clean.isEmpty()) showPlaceholder(R.string.visual_placeholder);
                        else showRecognized(clean);
                    });

                    long now = System.currentTimeMillis();
                    if (!clean.equals(previousText)) {
                        // text is still changing — reset the steadiness timer
                        previousText = clean;
                        lastChangeTime = now;
                        if (!clean.isEmpty()) runOnUiThread(this::pipFoundSomething);
                    } else if (!clean.isEmpty()
                            && !clean.equals(lastSpokenText)
                            && now - lastChangeTime > STABLE_MS) {
                        // text has held steady and we haven't read it yet -> read it aloud
                        lastSpokenText = clean;
                        runOnUiThread(() -> speak(clean));
                    }
                })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void processLabels(InputImage image, ImageProxy imageProxy) {
        labeler.process(image)
                .addOnSuccessListener(labels -> {
                    List<LabelAnnouncer.Label> detected = new ArrayList<>();
                    for (ImageLabel label : labels) {
                        detected.add(new LabelAnnouncer.Label(
                                label.getText(), label.getConfidence()));
                    }

                    List<LabelAnnouncer.Label> top = LabelAnnouncer.topLabels(detected);
                    String screenText = formatLabels(top);
                    runOnUiThread(() -> {
                        if (mode == Mode.LABELS) {
                            if (screenText.isEmpty()) showPlaceholder(R.string.visual_placeholder_objects);
                            else showRecognized(screenText);
                        }
                    });

                    String sentence = labelAnnouncer.announce(
                            detected, System.currentTimeMillis());
                    if (sentence != null) {
                        runOnUiThread(() -> {
                            if (mode == Mode.LABELS) speak(sentence);
                        });
                    }
                })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private String formatLabels(List<LabelAnnouncer.Label> labels) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) out.append('\n');
            LabelAnnouncer.Label label = labels.get(i);
            out.append(label.text).append(' ')
                    .append(Math.round(label.confidence * 100)).append('%');
        }
        return out.toString();
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "visual");
    }

    // ---------- presentation ----------

    /** Rounded camera preview (PreviewView runs in "compatible" mode so the outline clips it). */
    private void roundPreview() {
        final float radius = getResources().getDimension(R.dimen.radius_3xl) - Ui.dpF(this, 6);
        previewView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        previewView.setClipToOutline(true);
    }

    private void showRecognized(String text) {
        tvRecognized.setText(text);
        tvRecognized.setTextColor(Ui.color(this, R.color.ink_900));
    }

    private void showPlaceholder(int textRes) {
        tvRecognized.setText(textRes);
        tvRecognized.setTextColor(Ui.color(this, R.color.ink_500));
    }

    /** New text in view: Pip looks up (alert) for 1.5 s, then back to idle. */
    private void pipFoundSomething() {
        if (cameraDenied) return;
        pip.setExpression(DuckLooks.Expression.ALERT, true);
        handler.removeCallbacks(pipBackToIdle);
        handler.postDelayed(pipBackToIdle, FOUND_ALERT_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (recognizer != null) recognizer.close();
        if (labeler != null) labeler.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
