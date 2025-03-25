package com.example.componentidentifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Classifier {
    private static final String TAG = "ComponentClassifier";
    private static final String MODEL_FILE = "component_classifier.tflite";
    private static final String LABEL_FILE = "labels.txt";
    private static final String INFO_FILE = "component_info.json";

    private final Interpreter interpreter;
    private final List<String> labels;
    private final ImageProcessor imageProcessor;
    private final Context context;
    private final int inputSize = 224;

    public Classifier(Context context) throws Exception {
        this.context = context;
        try {
            interpreter = new Interpreter(loadModelFile(), null);
            labels = loadLabels();
            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0f, 255f))
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error: " + e.getMessage());
            throw e;
        }
    }

    public Result classify(Bitmap bitmap) {
        try {
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            float[][] output = new float[1][labels.size()];
            interpreter.run(tensorImage.getBuffer(), output);
            return new Result(output[0], labels);
        } catch (Exception e) {
            Log.e(TAG, "Classification error: " + e.getMessage());
            return new Result(new float[0], new ArrayList<>());
        }
    }

    public ComponentInfo getComponentInfo(String componentName) {
        try {
            String jsonString = loadJsonFromAssets(INFO_FILE);
            JSONObject json = new JSONObject(jsonString);
            JSONArray components = json.getJSONArray("components");

            for(int i = 0; i < components.length(); i++) {
                JSONObject comp = components.getJSONObject(i);
                if(comp.getString("name").equals(componentName)) {
                    return new ComponentInfo(
                            comp.getString("description"),
                            jsonArrayToList(comp.getJSONArray("specs")),
                            jsonArrayToList(comp.getJSONArray("common_projects"))
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Info load error: " + e.getMessage());
        }
        return new ComponentInfo("Information unavailable", new ArrayList<>(), new ArrayList<>());
    }

    private MappedByteBuffer loadModelFile() throws Exception {
        return FileUtil.loadMappedFile(context, MODEL_FILE);
    }

    private List<String> loadLabels() throws Exception {
        List<String> labels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABEL_FILE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
        }
        return labels;
    }

    private String loadJsonFromAssets(String fileName) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName)));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private List<String> jsonArrayToList(JSONArray array) throws Exception {
        List<String> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }

    public static class Result {
        private final float[] confidences;
        private final List<String> labels;

        public Result(float[] confidences, List<String> labels) {
            this.confidences = confidences;
            this.labels = labels;
        }

        public String getTopLabel() {
            if (confidences.length == 0 || labels.isEmpty()) return "Unknown";
            int maxPos = 0;
            float max = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > max) {
                    max = confidences[i];
                    maxPos = i;
                }
            }
            return labels.get(maxPos);
        }

        public float getTopConfidence() {
            if (confidences.length == 0) return 0;
            float max = 0;
            for (float val : confidences) {
                if (val > max) max = val;
            }
            return max;
        }
    }

    public static class ComponentInfo {
        public final String description;
        public final List<String> specs;
        public final List<String> projects;

        public ComponentInfo(String description, List<String> specs, List<String> projects) {
            this.description = description;
            this.specs = specs;
            this.projects = projects;
        }
    }
}