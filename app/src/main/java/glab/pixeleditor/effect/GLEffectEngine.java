package glab.pixeleditor.effect;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance offscreen OpenGL ES 2.0 evaluation engine for Alight Motion effects.
 * Compiles and runs the effect's fragment shader CDATA directly on the GPU with its declared parameters.
 */
public class GLEffectEngine {

    private static final String TAG = "GLEffectEngine";
    private static GLEffectEngine sInstance;

    private static final String VERTEX_SHADER_CODE =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 acScreenNorm;\n" +
            "varying vec2 acLayerNorm;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    acScreenNorm = aTexCoord;\n" +
            "    acLayerNorm = aTexCoord;\n" +
            "}\n";

    private boolean isInitialized = false;
    private boolean isAvailable = true;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    // Quad geometry buffers
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;

    // Cache of compiled GL programs keyed by effectId + shaderHashCode
    private final Map<String, GLProgram> programCache = new HashMap<>();

    public static synchronized GLEffectEngine getInstance() {
        if (sInstance == null) {
            sInstance = new GLEffectEngine();
        }
        return sInstance;
    }

    private GLEffectEngine() {
        initBuffers();
    }

    private void initBuffers() {
        float[] quadVertices = {
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
        };

        float[] quadTexCoords = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
        };

        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(quadVertices).position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        texCoordBuffer.put(quadTexCoords).position(0);
    }

    /**
     * Ensures headless EGL 14 context is created and current on this thread.
     */
    public synchronized boolean ensureContext() {
        if (!isAvailable) return false;
        if (isInitialized) {
            // Verify current context
            if (!EGL14.eglGetCurrentContext().equals(eglContext)) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
            }
            return true;
        }

        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "EGL display not available");
                isAvailable = false;
                return false;
            }

            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.w(TAG, "Unable to initialize EGL");
                isAvailable = false;
                return false;
            }

            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
            };

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)
                    || numConfigs[0] <= 0) {
                Log.w(TAG, "No matching EGL config");
                isAvailable = false;
                return false;
            }

            int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "Failed to create EGL context");
                isAvailable = false;
                return false;
            }

            int[] surfaceAttribs = {
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
            };
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.w(TAG, "Failed to create EGL pbuffer surface");
                isAvailable = false;
                return false;
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.w(TAG, "Failed to make EGL context current");
                isAvailable = false;
                return false;
            }

            isInitialized = true;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "EGL initialization failed: " + t.getMessage());
            isAvailable = false;
            return false;
        }
    }

    /**
     * Applies the effect's fragment shader to the input bitmap.
     */
    public synchronized Bitmap applyEffect(Bitmap input, EffectDefinition effect, RectF layerBounds, RectF canvasBounds) {
        if (input == null || input.isRecycled() || effect == null) return input;
        String shaderSource = effect.getShaderSource();
        if (shaderSource == null || shaderSource.trim().isEmpty()) {
            return input;
        }

        if (!ensureContext()) {
            return input;
        }

        int width = input.getWidth();
        int height = input.getHeight();
        if (width <= 0 || height <= 0) return input;

        GLProgram program = getOrCreateProgram(effect);
        if (program == null || program.programId == 0) {
            return input;
        }

        int[] fbo = new int[1];
        GLES20.glGenFramebuffers(1, fbo, 0);

        int inputTex = createTexture(input);
        Map<String, Integer> bufferTextures = new HashMap<>();
        Map<String, int[]> bufferSizes = new HashMap<>();

        // Allocate intermediate buffer textures for multi-pass effects
        List<String> textureIds = effect.getTextureIds();
        for (String tid : textureIds) {
            if ("inputImg".equalsIgnoreCase(tid)) continue;
            int ds = 1;
            Integer customDs = effect.getTextureDownsamples().get(tid);
            if (customDs != null && customDs > 1) ds = customDs;
            int bw = Math.max(1, width / ds);
            int bh = Math.max(1, height / ds);
            int bTex = createEmptyTexture(bw, bh);
            bufferTextures.put(tid, bTex);
            bufferSizes.put(tid, new int[]{bw, bh});
        }

        // Final output texture
        int outputTex = createEmptyTexture(width, height);

        List<String> passTargets = effect.getPassTargets();
        List<String> passEffects = effect.getPassEffects();
        int numPasses = Math.max(1, passTargets.size());

        GLES20.glUseProgram(program.programId);

        // Bind standard environment uniforms
        float screenW = canvasBounds != null ? canvasBounds.width() : width;
        float screenH = canvasBounds != null ? canvasBounds.height() : height;
        float layerW = layerBounds != null ? layerBounds.width() : width;
        float layerH = layerBounds != null ? layerBounds.height() : height;
        float layerCenterX = layerBounds != null ? layerBounds.centerX() : width / 2.0f;
        float layerCenterY = layerBounds != null ? layerBounds.centerY() : height / 2.0f;

        program.setUniform2f("acScreenSize", screenW, screenH);
        program.setUniform2f("acCanvasSize", screenW, screenH);
        program.setUniform2f("acPreviewSize", (float) width, (float) height);
        program.setUniform2f("acResolution", (float) width, (float) height);
        program.setUniform2f("u_resolution", (float) width, (float) height);
        program.setUniform2f("acLayerSize", layerW, layerH);
        program.setUniform2f("acLayerCenter", layerCenterX, layerCenterY);
        program.setUniform2f("acLayerPivot", 0.0f, 0.0f);
        program.setUniform1f("acTime", 0.0f);
        program.setUniform1f("acAngularVelocity", 0.0f);
        program.setUniform1f("acScaleVelocity", 0.0f);
        program.setUniform2f("acVelocity", 0.0f, 0.0f);

        float[] identityMat = new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
        program.setUniformMatrix4fv("acLayerToScreen", identityMat);
        program.setUniformMatrix4fv("acScreenToLayer", identityMat);

        // Bind effect parameters
        bindEffectParams(program, effect);

        // Setup vertex attributes
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(program.aPositionLoc);
        GLES20.glVertexAttribPointer(program.aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        texCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(program.aTexCoordLoc);
        GLES20.glVertexAttribPointer(program.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // Multi-pass execution
        for (int pass = 0; pass < numPasses; pass++) {
            program.setUniform1i("acPass", pass);

            // Determine render target
            String targetName = pass < passTargets.size() ? passTargets.get(pass) : null;
            String passEffectId = pass < passEffects.size() ? passEffects.get(pass) : null;
            int targetTex = outputTex;
            int targetW = width;
            int targetH = height;

            if (targetName != null && bufferTextures.containsKey(targetName)) {
                targetTex = bufferTextures.get(targetName);
                int[] sz = bufferSizes.get(targetName);
                if (sz != null) {
                    targetW = sz[0];
                    targetH = sz[1];
                }
            }

            // If this pass specifies an external sub-effect (e.g. com.alightcreative.effects.boxblur2 for innerblur)
            if (passEffectId != null) {
                float strength = 0.15f;
                EffectParam sp = effect.getParam("strength");
                if (sp != null) strength = sp.getFloatValue();
                int iter = 2;
                EffectParam ip = effect.getParam("iter");
                if (ip != null) iter = (int) ip.getFloatValue();

                renderBlurSubPass(inputTex, targetTex, targetW, targetH, strength, iter, fbo[0]);

                // Re-bind parent effect program and attributes for subsequent passes
                GLES20.glUseProgram(program.programId);
                vertexBuffer.position(0);
                GLES20.glEnableVertexAttribArray(program.aPositionLoc);
                GLES20.glVertexAttribPointer(program.aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
                texCoordBuffer.position(0);
                GLES20.glEnableVertexAttribArray(program.aTexCoordLoc);
                GLES20.glVertexAttribPointer(program.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
                continue;
            }

            // Bind textures to texture units
            int texUnit = 0;

            // 1. inputImg
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texUnit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTex);
            program.setUniform1i("inputImg.texture", texUnit);
            program.setUniform2f("inputImg.size", (float) width, (float) height);
            texUnit++;

            // 2. Buffer textures
            for (Map.Entry<String, Integer> entry : bufferTextures.entrySet()) {
                String bName = entry.getKey();
                int bTexId = entry.getValue();
                int[] bSz = bufferSizes.get(bName);
                float bw = bSz != null ? bSz[0] : width;
                float bh = bSz != null ? bSz[1] : height;

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texUnit);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bTexId);
                program.setUniform1i(bName + ".texture", texUnit);
                program.setUniform2f(bName + ".size", bw, bh);
                texUnit++;
            }

            // Bind common fallback buffers to inputTex if requested by shader
            String[] commonBuffers = {"comp", "ds1", "hblur", "vblur", "downImg", "xDistBuf", "blurBuf", "surfBuf", "hdist"};
            for (String cb : commonBuffers) {
                if (!bufferTextures.containsKey(cb)) {
                    program.setUniform1i(cb + ".texture", 0);
                    program.setUniform2f(cb + ".size", (float) width, (float) height);
                }
            }

            // Bind FBO and set viewport
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, targetTex, 0);

            GLES20.glViewport(0, 0, targetW, targetH);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // Draw quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        // Read pixels from final output texture
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, outputTex, 0);

        IntBuffer pixelBuffer = IntBuffer.allocate(width * height);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);

        // Convert OpenGL pixel buffer to Android ARGB Bitmap with upright rows
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] rawPixels = pixelBuffer.array();
        int[] outPixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            int srcRow = y * width;
            int dstRow = y * width;
            for (int x = 0; x < width; x++) {
                int c = rawPixels[srcRow + x];
                // Swap R and B from GL_RGBA byte order to Android ARGB
                outPixels[dstRow + x] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
            }
        }
        result.setPixels(outPixels, 0, width, 0, 0, width, height);

        // Clean up GL resources
        GLES20.glDisableVertexAttribArray(program.aPositionLoc);
        GLES20.glDisableVertexAttribArray(program.aTexCoordLoc);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, fbo, 0);

        int[] texToDelete = new int[2 + bufferTextures.size()];
        texToDelete[0] = inputTex;
        texToDelete[1] = outputTex;
        int idx = 2;
        for (int bId : bufferTextures.values()) {
            texToDelete[idx++] = bId;
        }
        GLES20.glDeleteTextures(texToDelete.length, texToDelete, 0);

        return result;
    }

    private void bindEffectParams(GLProgram program, EffectDefinition effect) {
        Map<String, String> uniformTypes = effect.getUniformTypes();

        for (Map.Entry<String, String> entry : uniformTypes.entrySet()) {
            String uName = entry.getKey();
            String uType = entry.getValue();

            if ("float".equalsIgnoreCase(uType)) {
                EffectParam param = effect.getParam(uName);
                if (param != null) {
                    program.setUniform1f(uName, param.getFloatValue());
                }
            } else if ("bool".equalsIgnoreCase(uType)) {
                EffectParam param = effect.getParam(uName);
                if (param != null) {
                    program.setUniform1i(uName, param.getBooleanValue() ? 1 : 0);
                }
            } else if ("int".equalsIgnoreCase(uType)) {
                EffectParam param = effect.getParam(uName);
                if (param != null) {
                    program.setUniform1i(uName, Math.round(param.getFloatValue()));
                }
            } else if ("vec4".equalsIgnoreCase(uType)) {
                EffectParam param = effect.getParam(uName);
                if (param != null) {
                    int c = param.getColorValue();
                    program.setUniform4f(uName,
                            Color.red(c) / 255.0f,
                            Color.green(c) / 255.0f,
                            Color.blue(c) / 255.0f,
                            Color.alpha(c) / 255.0f);
                }
            } else if ("vec2".equalsIgnoreCase(uType)) {
                EffectParam px = effect.getParam(uName + "_x");
                EffectParam py = effect.getParam(uName + "_y");
                if (px != null && py != null) {
                    program.setUniform2f(uName, px.getFloatValue(), py.getFloatValue());
                } else {
                    EffectParam phue = effect.getParam(uName + "_hue");
                    EffectParam pstr = effect.getParam(uName + "_strength");
                    if (phue != null && pstr != null) {
                        program.setUniform2f(uName, phue.getFloatValue(), pstr.getFloatValue());
                    } else {
                        EffectParam single = effect.getParam(uName);
                        if (single != null) {
                            program.setUniform2f(uName, single.getFloatValue(), single.getFloatValue());
                        }
                    }
                }
            } else if ("vec3".equalsIgnoreCase(uType)) {
                EffectParam px = effect.getParam(uName + "_x");
                EffectParam py = effect.getParam(uName + "_y");
                EffectParam pz = effect.getParam(uName + "_z");
                if (px != null && py != null && pz != null) {
                    program.setUniform3f(uName, px.getFloatValue(), py.getFloatValue(), pz.getFloatValue());
                }
            }
        }

        // Catch any parameters declared on EffectParam list not explicitly in uniformTypes
        for (EffectParam p : effect.getParams()) {
            String pId = p.getId();
            if (uniformTypes.containsKey(pId)) continue;

            if (p.getType() == EffectParam.ParamType.SLIDER) {
                program.setUniform1f(pId, p.getFloatValue());
            } else if (p.getType() == EffectParam.ParamType.SWITCH) {
                program.setUniform1i(pId, p.getBooleanValue() ? 1 : 0);
            } else if (p.getType() == EffectParam.ParamType.COLOR) {
                int c = p.getColorValue();
                program.setUniform4f(pId,
                        Color.red(c) / 255.0f,
                        Color.green(c) / 255.0f,
                        Color.blue(c) / 255.0f,
                        Color.alpha(c) / 255.0f);
            }
        }
    }

    private int createTexture(Bitmap bitmap) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        return texId;
    }

    private int createEmptyTexture(int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return texId;
    }

    private GLProgram getOrCreateProgram(EffectDefinition effect) {
        String key = effect.getId() + "_" + effect.getShaderSource().hashCode();
        GLProgram cached = programCache.get(key);
        if (cached != null) return cached;

        String fragmentShaderCode = buildFragmentShaderCode(effect);

        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        if (vertexShader == 0) return null;

        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader);
            return null;
        }

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program for " + effect.getName() + ": " + GLES20.glGetProgramInfoLog(programId));
            GLES20.glDeleteProgram(programId);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return null;
        }

        GLProgram program = new GLProgram(programId);
        program.aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition");
        program.aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord");

        programCache.put(key, program);
        return program;
    }

    private String buildFragmentShaderCode(EffectDefinition effect) {
        StringBuilder sb = new StringBuilder();

        String rawShader = effect.getShaderSource();

        // Extract any #extension directives to place at top of shader
        StringBuilder extensions = new StringBuilder();
        StringBuilder cleanedShader = new StringBuilder();
        String[] lines = rawShader.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("#extension")) {
                extensions.append(line).append("\n");
            } else {
                cleanedShader.append(line).append("\n");
            }
        }

        sb.append(extensions);
        sb.append("#ifdef GL_FRAGMENT_PRECISION_HIGH\n");
        sb.append("precision highp float;\n");
        sb.append("precision highp int;\n");
        sb.append("#else\n");
        sb.append("precision mediump float;\n");
        sb.append("precision mediump int;\n");
        sb.append("#endif\n\n");

        sb.append("struct TextureInfo {\n");
        sb.append("    sampler2D texture;\n");
        sb.append("    vec2 size;\n");
        sb.append("};\n\n");

        sb.append("uniform TextureInfo inputImg;\n");

        // Declare buffer textures
        List<String> textureIds = effect.getTextureIds();
        for (String tid : textureIds) {
            if (!"inputImg".equalsIgnoreCase(tid)) {
                sb.append("uniform TextureInfo ").append(tid).append(";\n");
            }
        }

        // Declare standard buffer fallbacks in case referenced in shader
        String[] standardBuffers = {"comp", "ds1", "hblur", "vblur", "downImg", "xDistBuf", "blurBuf", "surfBuf", "hdist"};
        for (String b : standardBuffers) {
            if (!textureIds.contains(b)) {
                sb.append("uniform TextureInfo ").append(b).append(";\n");
            }
        }

        sb.append("\n");
        sb.append("uniform vec2 acScreenSize;\n");
        sb.append("uniform vec2 acCanvasSize;\n");
        sb.append("uniform vec2 acPreviewSize;\n");
        sb.append("uniform vec2 acResolution;\n");
        sb.append("uniform vec2 u_resolution;\n");
        sb.append("uniform vec2 acLayerCenter;\n");
        sb.append("uniform vec2 acLayerSize;\n");
        sb.append("uniform mat4 acLayerToScreen;\n");
        sb.append("uniform mat4 acScreenToLayer;\n");
        sb.append("uniform float acTime;\n");
        sb.append("uniform int acPass;\n");
        sb.append("uniform float acAngularVelocity;\n");
        sb.append("uniform float acScaleVelocity;\n");
        sb.append("uniform vec2 acVelocity;\n");
        sb.append("uniform vec2 acLayerPivot;\n\n");

        sb.append("varying vec2 acScreenNorm;\n");
        sb.append("varying vec2 acLayerNorm;\n\n");

        sb.append("#define texture2DCv(tex, uv) texture2D((tex), (uv))\n");
        sb.append("#define getTexSize(sz) (sz)\n");
        sb.append("#define saturate(v) clamp((v), 0.0, 1.0)\n");
        sb.append("#define lerp(a, b, t) mix((a), (b), (t))\n\n");

        // Uniforms declared from <params>
        Map<String, String> uniformTypes = effect.getUniformTypes();
        for (Map.Entry<String, String> e : uniformTypes.entrySet()) {
            sb.append("uniform ").append(e.getValue()).append(" ").append(e.getKey()).append(";\n");
        }

        // Additional fallback uniforms from effect params list
        for (EffectParam p : effect.getParams()) {
            String pId = p.getId();
            if (!uniformTypes.containsKey(pId)) {
                if (p.getType() == EffectParam.ParamType.SLIDER) {
                    sb.append("uniform float ").append(pId).append(";\n");
                } else if (p.getType() == EffectParam.ParamType.SWITCH) {
                    sb.append("uniform bool ").append(pId).append(";\n");
                } else if (p.getType() == EffectParam.ParamType.COLOR) {
                    sb.append("uniform vec4 ").append(pId).append(";\n");
                }
            }
        }

        sb.append("\n");
        String safeShaderCode = cleanedShader.toString()
                .replaceAll("/\\s*compColor\\.a", "/ max(compColor.a, 0.0001)");
        sb.append(safeShaderCode);

        return sb.toString();
    }

    private int compileShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) return 0;

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ": " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    private GLProgram blurSubProgram = null;

    private void renderBlurSubPass(int inputTex, int targetTex, int width, int height, float strength, int iterations, int fbo) {
        if (blurSubProgram == null) {
            String blurFrag =
                    "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "varying vec2 acScreenNorm;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "uniform vec2 uTexSize;\n" +
                    "uniform float uStrength;\n" +
                    "void main() {\n" +
                    "    vec2 texelSize = 1.0 / uTexSize;\n" +
                    "    float rad = max(0.5, uStrength * 16.0);\n" +
                    "    vec4 sum = vec4(0.0);\n" +
                    "    float count = 0.0;\n" +
                    "    for (float x = -3.0; x <= 3.0; x += 1.0) {\n" +
                    "        for (float y = -3.0; y <= 3.0; y += 1.0) {\n" +
                    "            vec2 off = vec2(x, y) * texelSize * rad;\n" +
                    "            sum += texture2D(uTexture, acScreenNorm + off);\n" +
                    "            count += 1.0;\n" +
                    "        }\n" +
                    "    }\n" +
                    "    gl_FragColor = sum / count;\n" +
                    "}\n";
            int vShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
            int fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, blurFrag);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vShader);
            GLES20.glAttachShader(prog, fShader);
            GLES20.glLinkProgram(prog);
            blurSubProgram = new GLProgram(prog);
            blurSubProgram.aPositionLoc = GLES20.glGetAttribLocation(prog, "aPosition");
            blurSubProgram.aTexCoordLoc = GLES20.glGetAttribLocation(prog, "aTexCoord");
        }

        GLES20.glUseProgram(blurSubProgram.programId);

        // Bind attributes
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(blurSubProgram.aPositionLoc);
        GLES20.glVertexAttribPointer(blurSubProgram.aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        texCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(blurSubProgram.aTexCoordLoc);
        GLES20.glVertexAttribPointer(blurSubProgram.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // Bind uniforms
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTex);
        blurSubProgram.setUniform1i("uTexture", 0);
        blurSubProgram.setUniform2f("uTexSize", (float) width, (float) height);
        blurSubProgram.setUniform1f("uStrength", Math.max(0.01f, strength));

        // Render to targetTex
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, targetTex, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private static class GLProgram {
        final int programId;
        int aPositionLoc = -1;
        int aTexCoordLoc = -1;
        private final Map<String, Integer> uniformLocations = new HashMap<>();

        GLProgram(int programId) {
            this.programId = programId;
        }

        int getLoc(String name) {
            Integer loc = uniformLocations.get(name);
            if (loc == null) {
                loc = GLES20.glGetUniformLocation(programId, name);
                uniformLocations.put(name, loc);
            }
            return loc;
        }

        void setUniform1f(String name, float val) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniform1f(loc, val);
        }

        void setUniform1i(String name, int val) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniform1i(loc, val);
        }

        void setUniform2f(String name, float x, float y) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniform2f(loc, x, y);
        }

        void setUniform3f(String name, float x, float y, float z) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniform3f(loc, x, y, z);
        }

        void setUniform4f(String name, float x, float y, float z, float w) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniform4f(loc, x, y, z, w);
        }

        void setUniformMatrix4fv(String name, float[] mat) {
            int loc = getLoc(name);
            if (loc >= 0) GLES20.glUniformMatrix4fv(loc, 1, false, mat, 0);
        }
    }
}
