package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.ClientLevelAccessor;
import com.aryston.helion.render.water.WeatherSource;
import com.aryston.helion.render.water.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FogType;

final class VanillaWeatherSource implements WeatherSource {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final boolean PAUSED_TICK = false;
    private static final float FLASH = 1.0F;
    private static final float NO_FLASH = 0.0F;
    private static final float MAX_LIGHT = 15.0F;
    private static final int LAVA_CHECK_FRAMES = 10;
    private static final int LAVA_REACH_SECTIONS = 2;
    private static final Predicate<BlockState> IS_LAVA = state -> state.getFluidState().is(FluidTags.LAVA);

    private int lavaCheckCountdown;
    private boolean lavaNearby;

    private boolean lavaNearby(ClientLevel level, BlockPos camera) {
        if (lavaCheckCountdown-- > 0) {
            return lavaNearby;
        }
        lavaCheckCountdown = LAVA_CHECK_FRAMES;
        int chunkX = SectionPos.blockToSectionCoord(camera.getX());
        int chunkZ = SectionPos.blockToSectionCoord(camera.getZ());
        int sectionY = SectionPos.blockToSectionCoord(camera.getY());
        for (int x = -LAVA_REACH_SECTIONS; x <= LAVA_REACH_SECTIONS; x++) {
            for (int z = -LAVA_REACH_SECTIONS; z <= LAVA_REACH_SECTIONS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX + x, chunkZ + z, false);
                if (chunk != null && hasLava(chunk, sectionY)) {
                    lavaNearby = true;
                    return true;
                }
            }
        }
        lavaNearby = false;
        return false;
    }

    private static boolean hasLava(LevelChunk chunk, int sectionY) {
        for (int y = sectionY - LAVA_REACH_SECTIONS; y <= sectionY + LAVA_REACH_SECTIONS; y++) {
            int index = chunk.getSectionIndexFromSectionY(y);
            if (index >= 0 && index < chunk.getSectionsCount() && !chunk.getSection(index).hasOnlyAir() && chunk.getSection(index).maybeHas(IS_LAVA)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void extendUnderwaterView(boolean extend) {
        HelionUnderwaterFog.extend(extend);
    }

    @Override
    public WeatherState weather() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return WeatherState.DRY;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(PAUSED_TICK);
        BlockPos camera = minecraft.gameRenderer.mainCamera().blockPosition();
        Biome biome = level.getBiome(camera).value();
        boolean rainsHere = biome.getPrecipitationAt(camera, level.getSeaLevel()) == Biome.Precipitation.RAIN;
        double seconds = (level.getGameTime() + (double) partialTick) / TICKS_PER_SECOND;
        boolean flashing = ((ClientLevelAccessor) level).helion$skyFlashTime() > 0 && !minecraft.options.hideLightningFlash().get();
        return new WeatherState(
            level.getRainLevel(partialTick), seconds, rainsHere, flashing ? FLASH : NO_FLASH, biome.getModifiedClimateSettings().downfall(),
            minecraft.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER,
            Math.max(level.getBrightness(LightLayer.SKY, camera) - level.getSkyDarken(), 0) / MAX_LIGHT,
            lavaNearby(level, camera)
        );
    }
}
