package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.lighting.DynamicLight;
import com.aryston.helion.render.lighting.DynamicLightResources;
import com.aryston.helion.render.voxel.VoxelDynamicLight;
import com.aryston.helion.render.voxel.VoxelDynamicScene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DynamicLightTracker {
    private static final double MAX_DISTANCE = 64.0;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;
    private static final double HELD_LIGHT_HEIGHT = 0.6;
    private static final int MAX_LIGHT_LEVEL = 15;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final List<Source> SOURCES = new ArrayList<>();
    private static @Nullable ClientLevel collectedLevel;
    private static long collectedTime = Long.MIN_VALUE;
    private static float collectedPartialTick = Float.NaN;

    private DynamicLightTracker() {
    }

    public static int brighten(Entity entity, int lightCoords) {
        if (!active()) {
            return lightCoords;
        }
        List<Source> sources = sources();
        if (sources.isEmpty()) {
            return lightCoords;
        }
        Vec3 position = entity.getLightProbePosition(partialTick());
        int dynamic = (int) Math.ceil(levelAt(sources, position));
        int block = LightCoordsUtil.block(lightCoords);
        if (dynamic <= block) {
            return lightCoords;
        }
        return LightCoordsUtil.withBlock(lightCoords, Math.min(dynamic, MAX_LIGHT_LEVEL));
    }

    static List<DynamicLight> lights(Vec3 camera, boolean enabled) {
        if (!enabled) {
            return List.of();
        }
        List<Source> sources = sources();
        List<DynamicLight> lights = new ArrayList<>(Math.min(sources.size(), DynamicLightResources.MAX_LIGHTS));
        for (Source source : sources) {
            if (lights.size() == DynamicLightResources.MAX_LIGHTS) {
                break;
            }
            lights.add(new DynamicLight(
                (float) (source.position().x - camera.x),
                (float) (source.position().y - camera.y),
                (float) (source.position().z - camera.z),
                source.level()
            ));
        }
        return lights;
    }

    static List<VoxelDynamicLight> voxelLights(boolean enabled) {
        if (!enabled) {
            return List.of();
        }
        List<Source> sources = sources();
        List<VoxelDynamicLight> lights = new ArrayList<>(Math.min(sources.size(), VoxelDynamicScene.MAX_LIGHTS));
        for (Source source : sources) {
            if (lights.size() == VoxelDynamicScene.MAX_LIGHTS) {
                break;
            }
            float[] color = source.color();
            lights.add(new VoxelDynamicLight(
                source.position().x, source.position().y, source.position().z, source.level(), color[RED], color[GREEN], color[BLUE]
            ));
        }
        return lights;
    }

    private static boolean active() {
        HelionRenderCore core = HelionRenderCore.get();
        return core.isActive() && !core.screenEffectsOnly() && core.settings().dynamicLights().enabled();
    }

    private static double levelAt(List<Source> sources, Vec3 position) {
        double level = DynamicLightEmitters.NO_LIGHT;
        for (Source source : sources) {
            level = Math.max(level, source.level() - source.position().distanceTo(position));
        }
        return level;
    }

    private static List<Source> sources() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            SOURCES.clear();
            collectedLevel = null;
            return SOURCES;
        }
        float partialTick = partialTick();
        long time = level.getGameTime();
        if (level == collectedLevel && time == collectedTime && partialTick == collectedPartialTick) {
            return SOURCES;
        }
        collectedLevel = level;
        collectedTime = time;
        collectedPartialTick = partialTick;
        collect(level, minecraft.gameRenderer.mainCamera().position(), partialTick);
        return SOURCES;
    }

    private static void collect(ClientLevel level, Vec3 camera, float partialTick) {
        SOURCES.clear();
        for (Entity entity : level.entitiesForRendering()) {
            DynamicLightEmitters.Emission emission = DynamicLightEmitters.emission(entity);
            if (emission.level() == DynamicLightEmitters.NO_LIGHT) {
                continue;
            }
            Vec3 position = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * HELD_LIGHT_HEIGHT, 0.0);
            double distanceSquared = position.distanceToSqr(camera);
            if (distanceSquared <= MAX_DISTANCE_SQUARED) {
                SOURCES.add(new Source(position, emission.level(), VoxelLightColors.color(emission.state()), distanceSquared));
            }
        }
        SOURCES.sort(Comparator.comparingDouble(Source::distanceSquared));
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private record Source(Vec3 position, float level, float[] color, double distanceSquared) {
    }
}
