package glab.pixeleditor.segmentation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BackgroundRemover {

    private static final String TAG = "BackgroundRemover";
    public static final String PREFS_NAME = "PixelEditor_BgRemover";
    public static final String PREF_PROVIDER = "bg_provider";
    public static final String PREF_API_KEY = "bg_api_key";
    public static final String PREF_CUSTOM_URL = "bg_custom_url";

    public static final String PROVIDER_FREE_AI = "Free Cloud AI";
    public static final String PROVIDER_CLIPDROP = "Clipdrop API";
    public static final String PROVIDER_REMOVE_BG = "Remove.bg API";
    public static final String PROVIDER_CUSTOM = "Custom Server (Rembg)";

    public interface OnSegmentationListener {
        void onSuccess(Bitmap resultBitmap, Bitmap maskBitmap);
        void onFailure(Exception e);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    // =========================================================================
    // 0. LOCAL ON-DEVICE NEURAL AI BACKGROUND REMOVAL (U²-Net ONNX / Any Object)
    // =========================================================================

    private static OrtEnvironment ortEnvironment = null;
    private static OrtSession ortSession = null;

    private static synchronized OrtSession getOrtSession(Context context) throws Exception {
        if (ortEnvironment == null) {
            ortEnvironment = OrtEnvironment.getEnvironment();
        }
        if (ortSession == null) {
            try (InputStream is = context.getAssets().open("models/u2netp.onnx")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                }
                byte[] modelBytes = baos.toByteArray();
                OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                ortSession = ortEnvironment.createSession(modelBytes, options);
            }
        }
        return ortSession;
    }

    public static void removeBackgroundU2Net(
            Context context,
            Bitmap input,
            boolean invert,
            float threshold,
            float feather,
            OnSegmentationListener listener) {

        if (input == null || input.isRecycled()) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Input bitmap is null or recycled"));
            return;
        }

        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            try {
                int origW = input.getWidth();
                int origH = input.getHeight();

                // U2-Net expects 320x320 RGB input
                int targetSize = 320;
                Bitmap resized = Bitmap.createScaledBitmap(input, targetSize, targetSize, true);

                int[] pixels = new int[targetSize * targetSize];
                resized.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize);
                resized.recycle();

                // ImageNet mean and standard deviation normalization
                float meanR = 0.485f, meanG = 0.456f, meanB = 0.406f;
                float stdR = 0.229f, stdG = 0.224f, stdB = 0.225f;

                FloatBuffer floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize);

                // NCHW tensor layout: [Channel 0 (R), Channel 1 (G), Channel 2 (B)]
                int channelSize = targetSize * targetSize;
                for (int c = 0; c < 3; c++) {
                    for (int i = 0; i < channelSize; i++) {
                        int pixel = pixels[i];
                        float val;
                        if (c == 0) {
                            val = (((pixel >> 16) & 0xFF) / 255.0f - meanR) / stdR;
                        } else if (c == 1) {
                            val = (((pixel >> 8) & 0xFF) / 255.0f - meanG) / stdG;
                        } else {
                            val = ((pixel & 0xFF) / 255.0f - meanB) / stdB;
                        }
                        floatBuffer.put(val);
                    }
                }
                floatBuffer.rewind();

                OrtSession session = getOrtSession(appContext);
                long[] shape = new long[]{1, 3, targetSize, targetSize};
                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape);

                String inputName = session.getInputNames().iterator().next();
                try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
                    inputTensor.close();

                    if (result.size() == 0) {
                        throw new IOException("U2-Net inference produced no outputs");
                    }

                    OnnxValue outVal = result.get(0);
                    float[] rawMask = new float[targetSize * targetSize];

                    if (outVal instanceof OnnxTensor) {
                        OnnxTensor tensor = (OnnxTensor) outVal;
                        Object value = tensor.getValue();
                        if (value instanceof float[][][][]) {
                            float[][][][] v4 = (float[][][][]) value;
                            int idx = 0;
                            for (int y = 0; y < targetSize; y++) {
                                for (int x = 0; x < targetSize; x++) {
                                    rawMask[idx++] = v4[0][0][y][x];
                                }
                            }
                        } else if (value instanceof float[][][]) {
                            float[][][] v3 = (float[][][]) value;
                            int idx = 0;
                            for (int y = 0; y < targetSize; y++) {
                                for (int x = 0; x < targetSize; x++) {
                                    rawMask[idx++] = v3[0][y][x];
                                }
                            }
                        } else if (value instanceof float[][]) {
                            float[][] v2 = (float[][]) value;
                            int idx = 0;
                            for (int y = 0; y < targetSize; y++) {
                                for (int x = 0; x < targetSize; x++) {
                                    rawMask[idx++] = v2[y][x];
                                }
                            }
                        } else {
                            tensor.getFloatBuffer().get(rawMask, 0, targetSize * targetSize);
                        }
                    }

                    // Saliency map min-max normalization
                    float minVal = Float.MAX_VALUE;
                    float maxVal = -Float.MAX_VALUE;
                    for (float v : rawMask) {
                        if (v < minVal) minVal = v;
                        if (v > maxVal) maxVal = v;
                    }

                    float range = (maxVal - minVal > 1e-6f) ? (maxVal - minVal) : 1f;
                    float[] confidences = new float[targetSize * targetSize];
                    for (int i = 0; i < confidences.length; i++) {
                        confidences[i] = (rawMask[i] - minVal) / range;
                    }

                    Bitmap maskSmall = createMaskFromConfidences(confidences, targetSize, targetSize, invert, threshold, feather);
                    Bitmap maskFull = Bitmap.createScaledBitmap(maskSmall, origW, origH, true);
                    maskSmall.recycle();

                    Bitmap output = applyMaskToBitmap(input, maskFull);

                    mainHandler.post(() -> {
                        if (listener != null) listener.onSuccess(output, maskFull);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "U2-Net local background removal failed", e);
                mainHandler.post(() -> {
                    if (listener != null) listener.onFailure(e);
                });
            }
        });
    }

    // =========================================================================
    // 1. CLOUD AI BACKGROUND REMOVAL (Internet-Powered / Any Subject)
    // =========================================================================

    public static void removeBackgroundCloud(
            Context context,
            Bitmap input,
            String provider,
            String apiKey,
            String customUrl,
            OnSegmentationListener listener) {

        if (input == null || input.isRecycled()) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Input bitmap is null or recycled"));
            return;
        }

        executor.execute(() -> {
            try {
                // Downsample image if exceedingly large to optimize upload speed (< 1600px)
                int maxDim = 1600;
                int origW = input.getWidth();
                int origH = input.getHeight();
                Bitmap uploadBmp = input;

                if (origW > maxDim || origH > maxDim) {
                    float ratio = Math.min((float) maxDim / origW, (float) maxDim / origH);
                    int nw = Math.max(1, Math.round(origW * ratio));
                    int nh = Math.max(1, Math.round(origH * ratio));
                    uploadBmp = Bitmap.createScaledBitmap(input, nw, nh, true);
                }

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                uploadBmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byte[] pngBytes = stream.toByteArray();

                Bitmap cutoutBitmap = null;

                if (PROVIDER_CLIPDROP.equalsIgnoreCase(provider)) {
                    cutoutBitmap = executeClipdropApi(pngBytes, apiKey);
                } else if (PROVIDER_REMOVE_BG.equalsIgnoreCase(provider)) {
                    cutoutBitmap = executeRemoveBgApi(pngBytes, apiKey);
                } else if (PROVIDER_CUSTOM.equalsIgnoreCase(provider)) {
                    cutoutBitmap = executeCustomRembgApi(pngBytes, customUrl);
                } else {
                    // Default Free Cloud AI
                    cutoutBitmap = executeFreeCloudAi(pngBytes, apiKey);
                }

                if (cutoutBitmap == null) {
                    throw new IOException("Failed to obtain cutout image from " + provider);
                }

                // If input was downsampled, scale cutout bitmap back to original dimensions
                Bitmap finalResult;
                if (cutoutBitmap.getWidth() != origW || cutoutBitmap.getHeight() != origH) {
                    finalResult = Bitmap.createScaledBitmap(cutoutBitmap, origW, origH, true);
                    cutoutBitmap.recycle();
                } else {
                    finalResult = cutoutBitmap;
                }

                // Extract mask from alpha channel of result bitmap
                Bitmap maskBitmap = extractMaskFromAlpha(finalResult);

                mainHandler.post(() -> {
                    if (listener != null) listener.onSuccess(finalResult, maskBitmap);
                });

            } catch (Exception e) {
                Log.e(TAG, "Cloud background removal failed", e);
                mainHandler.post(() -> {
                    if (listener != null) listener.onFailure(e);
                });
            }
        });
    }

    private static Bitmap executeClipdropApi(byte[] pngBytes, String apiKey) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Clipdrop API Key is required. Please set it in Settings.");
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image_file", "image.png",
                        RequestBody.create(pngBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url("https://clipdrop-api.co/remove-background/v1")
                .addHeader("x-api-key", apiKey.trim())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "HTTP " + response.code();
                throw new IOException("Clipdrop error: " + errorBody);
            }
            byte[] respBytes = response.body().bytes();
            return BitmapFactory.decodeByteArray(respBytes, 0, respBytes.length);
        }
    }

    private static Bitmap executeRemoveBgApi(byte[] pngBytes, String apiKey) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Remove.bg API Key is required. Please set it in Settings.");
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image_file", "image.png",
                        RequestBody.create(pngBytes, MediaType.parse("image/png")))
                .addFormDataPart("size", "auto")
                .build();

        Request request = new Request.Builder()
                .url("https://api.remove.bg/v1.0/removebg")
                .addHeader("X-Api-Key", apiKey.trim())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "HTTP " + response.code();
                throw new IOException("Remove.bg error: " + errorBody);
            }
            byte[] respBytes = response.body().bytes();
            return BitmapFactory.decodeByteArray(respBytes, 0, respBytes.length);
        }
    }

    private static Bitmap executeCustomRembgApi(byte[] pngBytes, String customUrl) throws IOException {
        if (customUrl == null || customUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Custom Server URL is required.");
        }

        String targetUrl = customUrl.trim();
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "http://" + targetUrl;
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.png",
                        RequestBody.create(pngBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Custom server error HTTP " + response.code());
            }
            byte[] respBytes = response.body().bytes();
            return BitmapFactory.decodeByteArray(respBytes, 0, respBytes.length);
        }
    }

    private static Bitmap executeFreeCloudAi(byte[] pngBytes, String apiKey) throws IOException {
        String base64Img = Base64.encodeToString(pngBytes, Base64.NO_WRAP);
        String dataUrl = "data:image/png;base64," + base64Img;

        try {
            JSONObject payload = new JSONObject();
            JSONArray dataArr = new JSONArray();
            dataArr.put(dataUrl);
            payload.put("data", dataArr);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
            Request req = new Request.Builder()
                    .url("https://briaai-bria-rmbg-1-4.hf.space/api/predict")
                    .post(body)
                    .build();

            try (Response res = httpClient.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    String jsonStr = res.body().string();
                    JSONObject json = new JSONObject(jsonStr);
                    JSONArray resultData = json.optJSONArray("data");
                    if (resultData != null && resultData.length() > 0) {
                        String resultUrl = resultData.getString(0);
                        if (resultUrl.startsWith("data:image")) {
                            String b64 = resultUrl.substring(resultUrl.indexOf(",") + 1);
                            byte[] decoded = Base64.decode(b64, Base64.DEFAULT);
                            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Try secondary free online cutout endpoint
        try {
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "image.png",
                            RequestBody.create(pngBytes, MediaType.parse("image/png")))
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.poof.bg/v1/remove")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] respBytes = response.body().bytes();
                    Bitmap bmp = BitmapFactory.decodeByteArray(respBytes, 0, respBytes.length);
                    if (bmp != null) return bmp;
                }
            }
        } catch (Exception ignored) {
        }

        throw new IOException("Free Cloud AI service is currently busy. Please select Clipdrop / Remove.bg in Settings or use Tap-to-Cutout.");
    }

    // =========================================================================
    // 2. INTERACTIVE TAP-TO-CUTOUT / SALIENT OBJECT SEGMENTATION (On-Device)
    // =========================================================================

    public static void removeBackgroundTapPoint(
            Bitmap input,
            float normX,
            float normY,
            float tolerance,
            float feather,
            boolean invert,
            OnSegmentationListener listener) {

        if (input == null || input.isRecycled()) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Input bitmap is null or recycled"));
            return;
        }

        executor.execute(() -> {
            try {
                int origW = input.getWidth();
                int origH = input.getHeight();

                int maxDim = 480;
                float scale = Math.min(1.0f, (float) maxDim / Math.max(origW, origH));
                int w = Math.max(1, Math.round(origW * scale));
                int h = Math.max(1, Math.round(origH * scale));

                Bitmap scaledInput = Bitmap.createScaledBitmap(input, w, h, true);
                int[] pixels = new int[w * h];
                scaledInput.getPixels(pixels, 0, w, 0, 0, w, h);
                scaledInput.recycle();

                int seedX = Math.max(0, Math.min(w - 1, Math.round(normX * w)));
                int seedY = Math.max(0, Math.min(h - 1, Math.round(normY * h)));

                int sampleRadius = 3;
                double sumL = 0, sumA = 0, sumB = 0;
                int sampleCount = 0;

                for (int dy = -sampleRadius; dy <= sampleRadius; dy++) {
                    for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
                        int sx = Math.max(0, Math.min(w - 1, seedX + dx));
                        int sy = Math.max(0, Math.min(h - 1, seedY + dy));
                        int c = pixels[sy * w + sx];
                        double[] lab = rgbToLab(Color.red(c), Color.green(c), Color.blue(c));
                        sumL += lab[0];
                        sumA += lab[1];
                        sumB += lab[2];
                        sampleCount++;
                    }
                }

                double targetL = sumL / sampleCount;
                double targetA = sumA / sampleCount;
                double targetB = sumB / sampleCount;

                double maxLabDist = 15.0 + (tolerance * 65.0);

                boolean[] visited = new boolean[w * h];
                float[] confidences = new float[w * h];
                Queue<Integer> queue = new ArrayDeque<>(w * h / 4);

                int seedIdx = seedY * w + seedX;
                visited[seedIdx] = true;
                queue.add(seedIdx);

                int[] dX = {-1, 1, 0, 0, -1, 1, -1, 1};
                int[] dY = {0, 0, -1, 1, -1, -1, 1, 1};

                while (!queue.isEmpty()) {
                    int currIdx = queue.poll();
                    int cx = currIdx % w;
                    int cy = currIdx / w;

                    int c = pixels[currIdx];
                    double[] lab = rgbToLab(Color.red(c), Color.green(c), Color.blue(c));
                    double dL = lab[0] - targetL;
                    double da = lab[1] - targetA;
                    double db = lab[2] - targetB;
                    double dist = Math.sqrt(dL * dL + da * da + db * db);

                    if (dist <= maxLabDist) {
                        float confidence = (float) Math.max(0.0, 1.0 - (dist / maxLabDist));
                        confidences[currIdx] = confidence;

                        for (int k = 0; k < 8; k++) {
                            int nx = cx + dX[k];
                            int ny = cy + dY[k];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int nextIdx = ny * w + nx;
                                if (!visited[nextIdx]) {
                                    visited[nextIdx] = true;
                                    queue.add(nextIdx);
                                }
                            }
                        }
                    }
                }

                int foregroundCount = 0;
                for (float conf : confidences) {
                    if (conf > 0.3f) foregroundCount++;
                }

                if (foregroundCount < (w * h * 0.05f)) {
                    for (int i = 0; i < pixels.length; i++) {
                        int c = pixels[i];
                        double[] lab = rgbToLab(Color.red(c), Color.green(c), Color.blue(c));
                        double dL = lab[0] - targetL;
                        double da = lab[1] - targetA;
                        double db = lab[2] - targetB;
                        double dist = Math.sqrt(dL * dL + da * da + db * db);
                        if (dist <= maxLabDist) {
                            confidences[i] = (float) Math.max(0.0, 1.0 - (dist / maxLabDist));
                        } else {
                            confidences[i] = 0f;
                        }
                    }
                }

                Bitmap maskSmall = createMaskFromConfidences(confidences, w, h, invert, 0.5f, feather);
                Bitmap maskFull = Bitmap.createScaledBitmap(maskSmall, origW, origH, true);
                maskSmall.recycle();

                Bitmap output = applyMaskToBitmap(input, maskFull);

                mainHandler.post(() -> {
                    if (listener != null) listener.onSuccess(output, maskFull);
                });

            } catch (Exception e) {
                Log.e(TAG, "Tap background removal failed", e);
                mainHandler.post(() -> {
                    if (listener != null) listener.onFailure(e);
                });
            }
        });
    }

    // =========================================================================
    // 3. ON-DEVICE ML KIT SELFIE / PORTRAIT SEGMENTATION
    // =========================================================================

    public static void removeBackgroundMLKit(
            Bitmap input,
            boolean invert,
            float threshold,
            float feather,
            OnSegmentationListener listener) {

        if (input == null || input.isRecycled()) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Input bitmap is null or recycled"));
            return;
        }

        executor.execute(() -> {
            try {
                int maxDim = 1200;
                int origW = input.getWidth();
                int origH = input.getHeight();
                Bitmap procBmp = input;
                boolean scaled = false;

                if (origW > maxDim || origH > maxDim) {
                    float ratio = Math.min((float) maxDim / origW, (float) maxDim / origH);
                    int nw = Math.max(1, Math.round(origW * ratio));
                    int nh = Math.max(1, Math.round(origH * ratio));
                    procBmp = Bitmap.createScaledBitmap(input, nw, nh, true);
                    scaled = true;
                }

                SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .enableRawSizeMask()
                        .build();

                Segmenter segmenter = Segmentation.getClient(options);
                InputImage image = InputImage.fromBitmap(procBmp, 0);

                boolean isScaled = scaled;

                segmenter.process(image)
                        .addOnSuccessListener(mask -> {
                            executor.execute(() -> {
                                try {
                                    ByteBuffer buffer = mask.getBuffer();
                                    int maskW = mask.getWidth();
                                    int maskH = mask.getHeight();

                                    float[] confidences = new float[maskW * maskH];
                                    buffer.rewind();
                                    for (int i = 0; i < confidences.length; i++) {
                                        confidences[i] = buffer.getFloat();
                                    }

                                    Bitmap maskBitmap = createMaskFromConfidences(confidences, maskW, maskH, invert, threshold, feather);

                                    if (isScaled) {
                                        Bitmap fullMask = Bitmap.createScaledBitmap(maskBitmap, origW, origH, true);
                                        maskBitmap.recycle();
                                        maskBitmap = fullMask;
                                    }

                                    Bitmap output = applyMaskToBitmap(input, maskBitmap);

                                    Bitmap finalMask = maskBitmap;
                                    mainHandler.post(() -> {
                                        if (listener != null) listener.onSuccess(output, finalMask);
                                    });

                                } catch (Exception e) {
                                    mainHandler.post(() -> {
                                        if (listener != null) listener.onFailure(e);
                                    });
                                }
                            });
                        })
                        .addOnFailureListener(e -> {
                            mainHandler.post(() -> {
                                if (listener != null) listener.onFailure(e);
                            });
                        });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onFailure(e);
                });
            }
        });
    }

    // =========================================================================
    // 4. MANUAL TOUCH BRUSH (ERASE / RESTORE)
    // =========================================================================

    public static void applyBrushToMask(
            Bitmap mask,
            float normX,
            float normY,
            float normRadius,
            boolean isErase) {

        if (mask == null || mask.isRecycled()) return;

        int w = mask.getWidth();
        int h = mask.getHeight();
        float cx = normX * w;
        float cy = normY * h;
        float radius = Math.max(2f, normRadius * Math.max(w, h));

        Canvas canvas = new Canvas(mask);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        if (isErase) {
            paint.setColor(Color.BLACK);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        } else {
            paint.setColor(Color.WHITE);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }

        canvas.drawCircle(cx, cy, radius, paint);
    }

    // =========================================================================
    // 5. HELPER UTILITIES
    // =========================================================================

    public static Bitmap createMaskFromConfidences(
            float[] confs, int w, int h, boolean invert, float threshold, float feather) {

        Bitmap maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];

        float effFeather = Math.max(0.01f, feather);
        float lower = Math.max(0f, threshold - effFeather);
        float upper = Math.min(1f, threshold + effFeather);

        for (int i = 0; i < confs.length; i++) {
            float c = confs[i];
            if (invert) c = 1.0f - c;

            float alphaFraction;
            if (c <= lower) {
                alphaFraction = 0f;
            } else if (c >= upper) {
                alphaFraction = 1f;
            } else {
                alphaFraction = (c - lower) / (upper - lower);
            }

            int a = Math.round(alphaFraction * 255f);
            pixels[i] = Color.argb(a, 255, 255, 255);
        }

        maskBmp.setPixels(pixels, 0, w, 0, 0, w, h);
        return maskBmp;
    }

    public static Bitmap extractMaskFromAlpha(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] srcPixels = new int[w * h];
        src.getPixels(srcPixels, 0, w, 0, 0, w, h);

        int[] maskPixels = new int[w * h];
        for (int i = 0; i < srcPixels.length; i++) {
            int a = Color.alpha(srcPixels[i]);
            maskPixels[i] = Color.argb(a, 255, 255, 255);
        }

        Bitmap maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        maskBmp.setPixels(maskPixels, 0, w, 0, 0, w, h);
        return maskBmp;
    }

    public static Bitmap applyMaskToBitmap(Bitmap src, Bitmap mask) {
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, 0, 0, paint);

        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(mask, 0, 0, maskPaint);

        return result;
    }

    private static double[] rgbToLab(int r, int g, int b) {
        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;

        rNorm = (rNorm > 0.04045) ? Math.pow((rNorm + 0.055) / 1.055, 2.4) : (rNorm / 12.92);
        gNorm = (gNorm > 0.04045) ? Math.pow((gNorm + 0.055) / 1.055, 2.4) : (gNorm / 12.92);
        bNorm = (bNorm > 0.04045) ? Math.pow((bNorm + 0.055) / 1.055, 2.4) : (bNorm / 12.92);

        double x = (rNorm * 0.4124564 + gNorm * 0.3575761 + bNorm * 0.1804375) / 0.95047;
        double y = (rNorm * 0.2126729 + gNorm * 0.7151522 + bNorm * 0.0721750) / 1.00000;
        double z = (rNorm * 0.0193339 + gNorm * 0.1191920 + bNorm * 0.9503041) / 1.08883;

        x = (x > 0.008856) ? Math.cbrt(x) : (7.787 * x + 16.0 / 116.0);
        y = (y > 0.008856) ? Math.cbrt(y) : (7.787 * y + 16.0 / 116.0);
        z = (z > 0.008856) ? Math.cbrt(z) : (7.787 * z + 16.0 / 116.0);

        double L = (116.0 * y) - 16.0;
        double a = 500.0 * (x - y);
        double bVal = 200.0 * (y - z);

        return new double[]{L, a, bVal};
    }
}
