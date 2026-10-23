package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.water.WeatherSource;
import com.aryston.helion.render.water.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

final class VanillaWeatherSource implements WeatherSource {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final boolean PAUSED_TICK = false;

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
        return new WeatherState(level.getRainLevel(partialTick), seconds, rainsHere);
    }
}
