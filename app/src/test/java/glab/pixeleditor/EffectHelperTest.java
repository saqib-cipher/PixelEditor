package glab.pixeleditor;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import glab.pixeleditor.effect.EffectDefinition;
import glab.pixeleditor.effect.EffectHelper;
import glab.pixeleditor.effect.EffectParam;

public class EffectHelperTest {

    private File getEffectsDir() {
        File dir = new File("src/main/assets/effects");
        if (!dir.exists()) {
            dir = new File("../effects");
        }
        if (!dir.exists()) {
            dir = new File("effects");
        }
        return dir;
    }

    @Test
    public void testParseBrightnessContrastEffect() throws Exception {
        File file = new File(getEffectsDir(), "brightness-contrast.xml");
        assertTrue("brightness-contrast.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Effect should be parsed", effect);
            assertEquals("com.alightcreative.effects.brightcont", effect.getId());
            assertEquals("Color & Light", effect.getCategory());
            assertTrue(effect.isDeprecated());

            // Controls verification: brightness and contrast spinners
            assertEquals(2, effect.getParams().size());

            EffectParam brightness = effect.getParam("brightness");
            assertNotNull("Brightness param should exist", brightness);
            assertEquals("Brightness", brightness.getLabel());
            assertEquals(-1.0f, brightness.getMinValue(), 0.001f);
            assertEquals(1.0f, brightness.getMaxValue(), 0.001f);
            assertEquals(0.0f, brightness.getDefaultValue(), 0.001f);
            assertEquals("relative-percent", brightness.getUnitType());

            EffectParam contrast = effect.getParam("contrast");
            assertNotNull("Contrast param should exist", contrast);
            assertEquals("Contrast", contrast.getLabel());
            assertEquals(-1.0f, contrast.getMinValue(), 0.001f);
            assertEquals(3.0f, contrast.getMaxValue(), 0.001f);
            assertEquals(0.0f, contrast.getDefaultValue(), 0.001f);
            assertEquals("relative-percent", contrast.getUnitType());
        }
    }

    @Test
    public void testParseGaussianBlurEffect() throws Exception {
        File file = new File(getEffectsDir(), "gaussianblur.xml");
        assertTrue("gaussianblur.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Effect should be parsed", effect);
            assertEquals("com.alightcreative.effects.gaussianblur", effect.getId());
            assertEquals("Blur", effect.getCategory());

            EffectParam strength = effect.getParam("strength");
            assertNotNull("Strength param should exist", strength);
            assertEquals("Strength", strength.getLabel());
            assertEquals(0.15f, strength.getDefaultValue(), 0.001f);
            assertEquals(0.0f, strength.getMinValue(), 0.001f);
            assertEquals(2.0f, strength.getMaxValue(), 0.001f);
        }
    }

    @Test
    public void testParseVignetteEffect() throws Exception {
        File file = new File(getEffectsDir(), "vignette.xml");
        assertTrue("vignette.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Effect should be parsed", effect);
            assertEquals("com.alightcreative.effects.vignette", effect.getId());
            assertEquals("Matte / Mask / Key", effect.getCategory());

            assertNotNull("Scale param should exist", effect.getParam("scale"));
            assertNotNull("Roundness param should exist", effect.getParam("roundness"));
            assertNotNull("Feather param should exist", effect.getParam("feather"));
            assertNotNull("Strength param should exist", effect.getParam("strength"));

            EffectParam punchout = effect.getParam("punchout");
            assertNotNull("Punchout switch should exist", punchout);
            assertEquals(EffectParam.ParamType.SWITCH, punchout.getType());
        }
    }

    @Test
    public void testParseVortexBlurWithShaderAndParams() throws Exception {
        File file = new File(getEffectsDir(), "vortexblur.xml");
        assertTrue("vortexblur.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Vortex blur should be parsed", effect);
            assertEquals("com.alightcreative.effects.vortexblur", effect.getId());

            // Verify shader CDATA is extracted
            String shader = effect.getShaderSource();
            assertNotNull("Shader source should exist", shader);
            assertTrue("Shader must contain main function", shader.contains("void main()"));
            assertTrue("Shader must contain vortex formula", shader.contains("acScreenNorm") && shader.contains("centerPoint"));

            // Verify uniforms mapping
            assertEquals("vec2", effect.getUniformTypes().get("centerPoint"));
            assertEquals("float", effect.getUniformTypes().get("strength"));
            assertEquals("float", effect.getUniformTypes().get("radius"));

            // Verify parameters available
            assertNotNull(effect.getParam("centerPoint_x"));
            assertNotNull(effect.getParam("centerPoint_y"));
            assertNotNull(effect.getParam("strength"));
            assertNotNull(effect.getParam("radius"));
        }
    }

    @Test
    public void testParseBoxBlurMultiPass() throws Exception {
        File file = new File(getEffectsDir(), "boxblur3.xml");
        assertTrue("boxblur3.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Box blur should be parsed", effect);

            // Verify passes
            assertEquals(3, effect.getPassTargets().size());
            assertEquals("ds1", effect.getPassTargets().get(0));
            assertEquals("hblur", effect.getPassTargets().get(1));
            assertNull(effect.getPassTargets().get(2)); // Final screen pass

            // Verify downsamples
            assertEquals(Integer.valueOf(2), effect.getTextureDownsamples().get("ds1"));
            assertEquals(Integer.valueOf(2), effect.getTextureDownsamples().get("hblur"));

            // Verify CDATA contains multi-pass shader
            assertTrue(effect.getShaderSource().contains("acPass==0"));
            assertTrue(effect.getShaderSource().contains("acPass==1"));
        }
    }

    @Test
    public void testParseChromaKeyPrimaryShader() throws Exception {
        File file = new File(getEffectsDir(), "chromakey.xml");
        assertTrue("chromakey.xml should exist", file.exists());

        try (InputStream is = new FileInputStream(file)) {
            EffectDefinition effect = EffectHelper.parseEffectFromXml(is, file.getName());
            assertNotNull("Chroma Key should be parsed", effect);

            // Should select group 0 (the actual chromakey algorithm, not group 1 eyedropper passthrough)
            assertTrue("Shader must contain rgb2yuv matrix", effect.getShaderSource().contains("rgb2yuv"));
            assertTrue("Shader must compute keyYUV distance", effect.getShaderSource().contains("keyYUV"));

            // Uniform types
            assertEquals("vec4", effect.getUniformTypes().get("keyColor"));
            assertEquals("float", effect.getUniformTypes().get("threshold"));
            assertEquals("float", effect.getUniformTypes().get("feather"));
            assertEquals("bool", effect.getUniformTypes().get("defringe"));
            assertEquals("bool", effect.getUniformTypes().get("invert"));
        }
    }

    @Test
    public void testEffectDefinitionCopyPreservesGLMetadata() throws Exception {
        File file = new File(getEffectsDir(), "boxblur3.xml");
        EffectDefinition original;
        try (InputStream is = new FileInputStream(file)) {
            original = EffectHelper.parseEffectFromXml(is, file.getName());
        }

        assertNotNull(original);
        EffectDefinition copy = original.copy();

        assertNotNull(copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getPassTargets().size(), copy.getPassTargets().size());
        assertEquals(original.getPassTargets().get(0), copy.getPassTargets().get(0));
        assertEquals(original.getUniformTypes().get("strength"), copy.getUniformTypes().get("strength"));
        assertTrue(copy.getShaderSource().contains("acPass"));
    }
}
