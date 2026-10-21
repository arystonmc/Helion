package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.clouds.CloudEnvironment;
import com.aryston.helion.render.clouds.CloudSource;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.dimension.DimensionType;

final class VanillaCloudSource implements CloudSource {
    private static final double TICKS_PER_SECOND = 20.0;

    private final LevelRendererAccessor level;

    VanillaCloudSource(LevelFrameRequest request) {
        this.level = request.level();
    }

    @Override
    public Optional<CloudEnvironment> environment() {
        LevelRenderState state = level.helion$levelRenderState();
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null
            || state.skyRenderState.skybox != DimensionType.Skybox.OVERWORLD
            || state.customCloudsRenderer != null
            || level.helion$optionsRenderState().cloudStatus == CloudStatus.OFF
            || ARGB.alpha(state.cloudColor) <= 0) {
            return Optional.empty();
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double seconds = (clientLevel.getGameTime() + (double) partialTick) / TICKS_PER_SECOND;
        float rain = Math.clamp(1.0F - state.skyRenderState.rainBrightness, 0.0F, 1.0F);
        return Optional.of(new CloudEnvironment(seconds, rain));
    }

    @Override
    public void replaceVanillaClouds(boolean replace) {
        HelionClouds.replaceVanilla(replace);
    }
}
