package com.aryston.helion;

import com.aryston.helion.config.HelionConfig;
import com.aryston.helion.integration.ClientEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(value = Helion.MOD_ID, dist = Dist.CLIENT)
public final class Helion {
    public static final String MOD_ID = "helion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Helion(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HelionConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ClientEvents.register(modBus);
        LOGGER.info("Helion initialized");
    }
}
