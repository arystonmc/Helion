package com.aryston.helion;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = Helion.MOD_ID, dist = Dist.CLIENT)
public final class Helion {
    public static final String MOD_ID = "helion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Helion() {
        LOGGER.info("Helion initialized");
    }
}
