package com.aryston.helion.config;

import com.aryston.arkea.api.config.ArkeaConfigScreen;
import com.aryston.arkea.api.config.ConfigOption;
import com.aryston.arkea.api.config.ConfigPreset;
import com.aryston.arkea.ui.render.Icon;
import com.aryston.arkea.ui.render.Icons;
import com.aryston.helion.Helion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class HelionConfigScreen {
    private static final String KEY = "helion.config.";
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "textures/gui/logo.png");

    private HelionConfigScreen() {
    }

    public static Screen create(Screen parent) {
        HelionOptions options = new HelionOptions();
        return ArkeaConfigScreen.builder(Component.translatable(KEY + "title"))
            .brand(LOGO, Component.translatable(KEY + "brand"), Component.translatable(KEY + "brand.subtitle"))
            .meter(HelionImpact.meter(options))
            .group(text("group.configuration"))
            .page("general", text("page.general"), Icons.SLIDERS, page -> {
                for (ConfigPreset preset : HelionPresets.create(options)) {
                    page.preset(preset);
                }
                page.fullSection(text("section.core"), section -> section.add(options.enabled));
                page.fullSection(text("section.share"), section -> section
                    .add(shareAction("copyCode", Icons.COPY, () -> HelionSharing.copy(options)))
                    .add(shareAction("pasteCode", Icons.UPLOAD, () -> HelionSharing.paste(options, parent))));
            })
            .page("rendering", text("page.rendering"), Icons.MONITOR, page -> page
                .section(text("section.geometry"), section -> section.add(options.geometryBuffer))
                .section(text("section.antialiasing"), section -> section.add(options.temporal))
                .fullSection(text("section.sky"), section -> section.add(options.physicalSky)))
            .page("lighting", text("page.lighting"), Icons.SUN, page -> page
                .fullSection(text("section.ambientOcclusion"), section -> section.add(options.ambientOcclusion).add(options.ambientOcclusionMethod)
                    .add(options.ambientOcclusionStrength).add(options.ambientOcclusionRadius))
                .section(text("section.hdr"), section -> section.add(options.lighting).add(options.blockLight).add(options.skyLight))
                .section(text("section.shadows"), section -> section.add(options.shadows)))
            .page("effects", text("page.effects"), Icons.SPARKLE, page -> page
                .section(text("section.bloom"), section -> section.add(options.bloom).add(options.bloomIntensity).add(options.bloomThreshold))
                .section(text("section.image"), section -> section.add(options.toneMapper).add(options.exposure).add(options.dither))
                .fullSection(text("section.sharpening"), section -> section.add(options.sharpening).add(options.sharpeningStrength)))
            .group(text("group.system"))
            .page("performance", text("page.performance"), Icons.BOLT, page -> page
                .notice(text("performance.notice"))
                .fullSection(text("section.quality"), section -> section.add(options.ambientOcclusionQuality).add(options.shadowQuality)))
            .page("advanced", text("page.advanced"), Icons.CMD, page -> page
                .fullSection(text("section.debug"), section -> section.add(options.debugMode))
                .fullSection(text("section.views"), section -> section.collapsible(false).add(options.ambientOcclusionView).add(options.bloomView)
                    .add(options.geometryBufferView).add(options.lightView)))
            .build(parent);
    }

    private static ConfigOption<Boolean> shareAction(String id, Icon icon, Runnable action) {
        return ConfigOption.action(KEY + "share." + id, text("share." + id + ".button"), action)
            .icon(icon)
            .description(text("share." + id + ".description"));
    }

    private static Component text(String key) {
        return Component.translatable(KEY + key);
    }
}
