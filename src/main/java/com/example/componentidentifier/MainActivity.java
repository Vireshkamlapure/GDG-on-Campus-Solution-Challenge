package com.example.componentidentifier;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private Button captureButton;
    private ImageCapture imageCapture;
    private static final int REQUEST_CODE_PERMISSIONS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.captureButton);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissions();
        }

        captureButton.setOnClickListener(v -> captureImage());
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CODE_PERMISSIONS
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture == null) return;

        File outputFile = new File(getExternalFilesDir(null), "temp.jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
                options,
                Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
                        runOnUiThread(() -> processImage(bitmap));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                }
        );
    }

    private void processImage(Bitmap bitmap) {
        try {
            Classifier classifier = new Classifier(this);
            Classifier.Result result = classifier.classify(bitmap);
            Classifier.ComponentInfo info = classifier.getComponentInfo(result.getTopLabel());
            showResultDialog(result, info, bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showResultDialog(
            Classifier.Result result,
            Classifier.ComponentInfo info,
            Bitmap bitmap
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.result_dialog, null);
        builder.setView(dialogView);

        TextView tvComponent = dialogView.findViewById(R.id.tvComponentName);
        TextView tvConfidence = dialogView.findViewById(R.id.tvConfidence);
        ImageView ivResult = dialogView.findViewById(R.id.ivResult);
        TextView tvDescription = dialogView.findViewById(R.id.tvDescription);
        TextView tvSpecs = dialogView.findViewById(R.id.tvSpecs);
        TextView tvProjects = dialogView.findViewById(R.id.tvProjects);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        tvComponent.setText("Component: " + result.getTopLabel());
        tvConfidence.setText(String.format("Confidence: %.1f%%", result.getTopConfidence() * 100));
        ivResult.setImageBitmap(bitmap);
        tvDescription.setText(info.description);
        tvSpecs.setText(formatList(info.specs));
        tvProjects.setText(formatList(info.projects));

        AlertDialog dialog = builder.create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String formatList(List<String> items) {
        if (items.isEmpty()) return "No information available";
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("• ").append(item).append("\n");
        }
        return sb.toString().trim();
    }
}