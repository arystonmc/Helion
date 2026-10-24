package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.ClientLevelAccessor;
import com.aryston.helion.render.water.WeatherSource;
import com.aryston.helion.render.water.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

final class VanillaWeatherSource implements WeatherSource {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final boolean PAUSED_TICK = false;
    private static final float FLASH = 1.0F;
    private static final float NO_FLASH = 0.0F;

    @Override
    public WeatherState weather() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return WeatherState.DRY;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(PAUSED_TICK);
        BlockPos camera = minecraft.gameRenderer.mainCamera().blockPosition();
        boolean rainsHere = level.getBiome(camera).value().getPrecipitationAt(camera, level.getSeaLevel()) == Biome.Precipitation.RAIN;
        double seconds = (level.getGameTime() + (double) partialTick) / TICKS_PER_SECOND;
        boolean flashing = ((ClientLevelAccessor) level).helion$skyFlashTime() > 0 && !minecraft.options.hideLightningFlash().get();
        return new WeatherState(level.getRainLevel(partialTick), seconds, rainsHere, flashing ? FLASH : NO_FLASH);
    }
}
