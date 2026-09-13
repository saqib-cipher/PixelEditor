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
}
