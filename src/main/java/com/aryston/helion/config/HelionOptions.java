package com.aryston.helion.config;

import com.aryston.arkea.api.config.Binding;
import com.aryston.arkea.api.config.ChoiceStyle;
import com.aryston.arkea.api.config.ConfigOption;
import com.aryston.arkea.ui.render.Icon;
import com.aryston.arkea.ui.render.Icons;
import com.aryston.arkea.ui.widget.Tag;
import com.aryston.helion.Helion;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.lighting.AmbientOcclusionAlgorithm;
import com.aryston.helion.render.lighting.AmbientOcclusionQuality;
import com.aryston.helion.render.post.ToneMapper;
import com.aryston.helion.render.shadow.ShadowQuality;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

final class HelionOptions {
    private static final String KEY = "helion.config.";
    private static final String TOOLTIP_KEY = "helion.configuration.";
    private static final float PREVIEW_ASPECT = 16.0F / 9.0F;
    private static final double STRENGTH_STEP = 0.05;
    private static final double RADIUS_STEP = 0.25;
    private static final double EXPOSURE_STEP = 0.1;
    private static final double LIGHT_STEP = 0.1;
    private static final int LOW_COST = 1;
    private static final int MEDIUM_COST = 2;
    private static final int HIGH_COST = 3;
    private static final Identifier VANILLA_PREVIEW = preview("vanilla");

    final ConfigOption<Boolean> enabled;
    final ConfigOption<Boolean> geometryBuffer;
    final ConfigOption<Boolean> temporal;
    final ConfigOption<Boolean> physicalSky;
    final ConfigOption<Boolean> ambientOcclusion;
    final ConfigOption<AmbientOcclusionAlgorithm> ambientOcclusionMethod;
    final ConfigOption<AmbientOcclusionQuality> ambientOcclusionQuality;
    final ConfigOption<Double> ambientOcclusionStrength;
    final ConfigOption<Double> ambientOcclusionRadius;
    final ConfigOption<Boolean> lighting;
    final ConfigOption<Double> blockLight;
    final ConfigOption<Double> skyLight;
    final ConfigOption<Boolean> shadows;
    final ConfigOption<ShadowQuality> shadowQuality;
    final ConfigOption<Boolean> bloom;
    final ConfigOption<Double> bloomIntensity;
    final ConfigOption<Double> bloomThreshold;
    final ConfigOption<ToneMapper> toneMapper;
    final ConfigOption<Double> exposure;
    final ConfigOption<Boolean> dither;
    final ConfigOption<Boolean> sharpening;
    final ConfigOption<Double> sharpeningStrength;
    final ConfigOption<Boolean> debugMode;
    final ConfigOption<Boolean> ambientOcclusionView;
    final ConfigOption<Boolean> bloomView;
    final ConfigOption<GeometryBufferView> geometryBufferView;
    final ConfigOption<Boolean> lightView;

    HelionOptions() {
        this.enabled = toggle("enabled", HelionConfig.ENABLED, Icons.POWER, "enabled");
        this.geometryBuffer = experimental(toggle("geometryBuffer", HelionConfig.GEOMETRY_BUFFER_ENABLED, Icons.LAYERS, "geometryBuffer"))
            .cost(LOW_COST).requires(this.enabled);
        this.temporal = experimental(toggle("temporal", HelionConfig.TEMPORAL_ENABLED, Icons.BLUR, "temporalAntiAliasing"))
            .cost(LOW_COST).requires(this.enabled);
        this.physicalSky = experimental(toggle("physicalSky", HelionConfig.PHYSICAL_SKY_ENABLED, Icons.CLOUD, "physicalSky"))
            .cost(LOW_COST).requires(this.enabled).preview(VANILLA_PREVIEW, preview("physical_sky"), PREVIEW_ASPECT);
        this.ambientOcclusion = toggle("ambientOcclusion", HelionConfig.AMBIENT_OCCLUSION_ENABLED, Icons.SHADOW, "ambientOcclusion")
            .cost(MEDIUM_COST).requires(this.enabled).preview(VANILLA_PREVIEW, preview("ambient_occlusion"), PREVIEW_ASPECT);
        this.ambientOcclusionMethod = choice("ambientOcclusionMethod", HelionConfig.AMBIENT_OCCLUSION_ALGORITHM, AmbientOcclusionAlgorithm.class,
            Icons.BLEND, "ambientOcclusion.algorithm").requires(this.ambientOcclusion);
        this.ambientOcclusionQuality = choice("ambientOcclusionQuality", HelionConfig.AMBIENT_OCCLUSION_QUALITY, AmbientOcclusionQuality.class,
            Icons.GAUGE, "ambientOcclusion.quality").style(ChoiceStyle.SEGMENTED).cost(MEDIUM_COST).requires(this.ambientOcclusion);
        this.ambientOcclusionStrength = slider("ambientOcclusionStrength", HelionConfig.AMBIENT_OCCLUSION_STRENGTH, HelionConfig.MIN_STRENGTH, HelionConfig.MAX_STRENGTH, STRENGTH_STEP,
            Icons.CONTRAST, "ambientOcclusion.strength").requires(this.ambientOcclusion);
        this.ambientOcclusionRadius = slider("ambientOcclusionRadius", HelionConfig.AMBIENT_OCCLUSION_RADIUS, HelionConfig.MIN_RADIUS, HelionConfig.MAX_RADIUS, RADIUS_STEP,
            Icons.EXPAND, "ambientOcclusion.radius").requires(this.ambientOcclusion);
        this.lighting = experimental(toggle("lighting", HelionConfig.LIGHTING_ENABLED, Icons.BULB, "lighting"))
            .cost(MEDIUM_COST).requires(this.enabled, this.geometryBuffer);
        this.blockLight = slider("blockLight", HelionConfig.BLOCK_LIGHT_INTENSITY, HelionConfig.MIN_BLOCK_LIGHT_INTENSITY,
            HelionConfig.MAX_BLOCK_LIGHT_INTENSITY, LIGHT_STEP, Icons.BULB, "lighting.blockLightIntensity")
            .requires(this.lighting);
        this.skyLight = slider("skyLight", HelionConfig.SKY_LIGHT_INTENSITY, HelionConfig.MIN_SKY_LIGHT_INTENSITY,
            HelionConfig.MAX_SKY_LIGHT_INTENSITY, LIGHT_STEP, Icons.SUN, "lighting.skyLightIntensity")
            .requires(this.lighting);
        this.shadows = experimental(toggle("shadows", HelionConfig.SHADOWS_ENABLED, Icons.SUN, "shadows"))
            .cost(HIGH_COST).requires(this.lighting, this.physicalSky).preview(VANILLA_PREVIEW, preview("shadows"), PREVIEW_ASPECT);
        this.shadowQuality = choice("shadowQuality", HelionConfig.SHADOW_QUALITY, ShadowQuality.class, Icons.GAUGE, "shadows.quality")
            .style(ChoiceStyle.SEGMENTED).cost(HIGH_COST).requires(this.shadows);
        this.bloom = toggle("bloom", HelionConfig.BLOOM_ENABLED, Icons.SPARKLE, "image.bloom").cost(LOW_COST).requires(this.enabled)
            .preview(VANILLA_PREVIEW, preview("bloom"), PREVIEW_ASPECT);
        this.bloomIntensity = slider("bloomIntensity", HelionConfig.BLOOM_INTENSITY, HelionConfig.MIN_BLOOM_INTENSITY, HelionConfig.MAX_BLOOM_INTENSITY, STRENGTH_STEP, Icons.SPARKLE, "image.bloom.intensity")
            .percent().requires(this.bloom);
        this.bloomThreshold = slider("bloomThreshold", HelionConfig.BLOOM_THRESHOLD, HelionConfig.MIN_BLOOM_THRESHOLD, HelionConfig.MAX_BLOOM_THRESHOLD, EXPOSURE_STEP, Icons.FILTER, "image.bloom.threshold")
            .requires(this.bloom);
        this.toneMapper = choice("toneMapper", HelionConfig.TONE_MAPPER, ToneMapper.class, Icons.CONTRAST, "image.toneMapper").requires(this.enabled);
        this.exposure = slider("exposure", HelionConfig.EXPOSURE, HelionConfig.MIN_EXPOSURE, HelionConfig.MAX_EXPOSURE, EXPOSURE_STEP, Icons.SUN, "image.exposure").requires(this.enabled);
        this.dither = toggle("dither", HelionConfig.DITHER, Icons.PARTICLES, "image.dither").requires(this.enabled);
        this.sharpening = toggle("sharpening", HelionConfig.SHARPENING_ENABLED, Icons.EYE, "image.sharpening").cost(LOW_COST).requires(this.enabled);
        this.sharpeningStrength = slider("sharpeningStrength", HelionConfig.SHARPENING_STRENGTH, HelionConfig.MIN_SHARPENING_STRENGTH,
            HelionConfig.MAX_SHARPENING_STRENGTH, STRENGTH_STEP, Icons.EYE,
            "image.sharpening.strength").percent().requires(this.sharpening);
        this.debugMode = toggle("debugMode", HelionConfig.DEBUG_MODE, Icons.CMD, "debugMode");
        this.ambientOcclusionView = toggle("ambientOcclusionView", HelionConfig.AMBIENT_OCCLUSION_DEBUG_VIEW, Icons.SHADOW, "ambientOcclusion.debugView")
            .requires(this.ambientOcclusion);
        this.bloomView = toggle("bloomView", HelionConfig.BLOOM_DEBUG_VIEW, Icons.SPARKLE, "image.bloom.debugView").requires(this.bloom);
        this.geometryBufferView = choice("geometryBufferView", HelionConfig.GEOMETRY_BUFFER_VIEW, GeometryBufferView.class, Icons.LAYERS,
            "geometryBuffer.view").style(ChoiceStyle.DROPDOWN).requires(this.geometryBuffer);
        this.lightView = toggle("lightView", HelionConfig.LIGHT_ONLY_VIEW, Icons.BULB, "lighting.lightOnlyView").requires(this.lighting);
    }

    private static ConfigOption<Boolean> toggle(String id, ModConfigSpec.BooleanValue value, Icon icon, String tooltipKey) {
        return describe(ConfigOption.toggle(KEY + id, Binding.of(value)), id, icon, tooltipKey);
    }

    private static <E extends Enum<E>> ConfigOption<E> choice(String id, ModConfigSpec.EnumValue<E> value, Class<E> type, Icon icon, String tooltipKey) {
        return describe(ConfigOption.enumChoice(KEY + id, Binding.of(value), type), id, icon, tooltipKey);
    }

    private static ConfigOption<Double> slider(String id, ModConfigSpec.DoubleValue value, double min, double max, double step, Icon icon,
        String tooltipKey) {
        return describe(ConfigOption.slider(KEY + id, Binding.of(value), min, max, step), id, icon, tooltipKey);
    }

    private static <T> ConfigOption<T> describe(ConfigOption<T> option, String id, Icon icon, String tooltipKey) {
        return option.icon(icon)
            .description(Component.translatable(KEY + id + ".description"))
            .tooltip(Component.translatable(TOOLTIP_KEY + tooltipKey + ".tooltip"));
    }

    private static ConfigOption<Boolean> experimental(ConfigOption<Boolean> option) {
        return option.tag(Tag.beta(Component.translatable(KEY + "tag.experimental")));
    }

    private static Identifier preview(String name) {
        return Identifier.fromNamespaceAndPath(Helion.MOD_ID, "textures/gui/preview/" + name.toLowerCase(Locale.ROOT) + ".png");
    }
}
