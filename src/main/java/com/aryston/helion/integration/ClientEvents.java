package com.aryston.helion.integration;

import com.aryston.helion.config.HelionConfig;
import com.aryston.helion.debug.HelionDebugEntry;
import com.aryston.helion.debug.HelionDebugLog;
import com.aryston.helion.debug.VisualTest;
import com.aryston.helion.integration.vanilla.LevelRenderHook;
import com.aryston.helion.integration.vanilla.VanillaGeometryPipelines;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.PassiveReason;
import com.aryston.helion.render.shader.HelionPipelines;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.slf4j.Logger;

public final class ClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HelionDebugLog DEBUG_LOG = new HelionDebugLog();
    private static final Optional<VisualTest> VISUAL_TEST = VisualTest.requested();

    private ClientEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientEvents::onClientSetup);
        modBus.addListener(ClientEvents::onConfigLoading);
        modBus.addListener(ClientEvents::onConfigReloading);
        modBus.addListener(HelionKeys::register);
        modBus.addListener(HelionDebugEntry::register);
        modBus.addListener(ClientEvents::onRegisterPipelines);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onShutdown);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        HelionRenderCore core = HelionRenderCore.get();
        core.setPassiveListener(ClientEvents::onPassive);
        List<String> rendererMods = CompatibilityGuard.loadedRendererMods();
        if (!rendererMods.isEmpty()) {
            LOGGER.warn("Helion stays passive because these renderer mods are installed: {}", rendererMods);
            core.passivate(PassiveReason.INCOMPATIBLE_MOD);
        }
    }

    private static void onPassive(PassiveReason reason) {
        PassiveModeNotice.queue(reason);
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason.name());
        DEBUG_LOG.event("passive", data);
    }

    private static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        HelionPipelines.all().forEach(event::registerOptionalPipeline);
        VanillaGeometryPipelines.all().forEach(event::registerOptionalPipeline);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        applyConfig();
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        applyConfig();
    }

    private static void applyConfig() {
        HelionRenderCore.get().applySettings(
            HelionConfig.ENABLED.getAsBoolean(),
            HelionConfig.DEBUG_MODE.getAsBoolean(),
            HelionConfig.renderSettings()
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        PassiveModeNotice.showPending(minecraft);
        DEBUG_LOG.tick(minecraft);
        VISUAL_TEST.ifPresent(test -> test.tick(minecraft));
        while (HelionKeys.TOGGLE.consumeClick()) {
            toggle(minecraft);
        }
        while (HelionKeys.PARITY.consumeClick()) {
            if (HelionRenderCore.get().isDebugMode()) {
                ParityControl.start(minecraft, DEBUG_LOG);
            }
        }
    }

    private static void toggle(Minecraft minecraft) {
        boolean enabled = HelionRenderCore.get().toggle();
        PassiveModeNotice.show(minecraft, Component.translatable("helion.toggle.title"), PassiveModeNotice.enabledState(enabled));
        JsonObject data = new JsonObject();
        data.addProperty("enabled", enabled);
        DEBUG_LOG.event("toggle", data);
    }

    private static void onShutdown(GameShuttingDownEvent event) {
        LevelRenderHook.shutdown();
        HelionRenderCore.get().shutdown();
        DEBUG_LOG.close();
    }
}
