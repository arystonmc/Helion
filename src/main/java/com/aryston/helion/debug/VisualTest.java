package com.aryston.helion.debug;

import com.aryston.helion.config.HelionConfig;
import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.voxel.VoxelLightingResources;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class VisualTest {
    private static final String PROPERTY = "helion.visualTest";
    private static final String NATURAL_SCENE_PROPERTY = "helion.visualTest.natural";
    private static final String SCENES_PROPERTY = "helion.visualTest.scenes";
    private static final String VARIANTS_PROPERTY = "helion.visualTest.variants";
    private static final String FULL_SIZE_PROPERTY = "helion.visualTest.fullSize";
    private static final String LIST_SEPARATOR = "\\+";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int START_DELAY_TICKS = 200;
    private static final int SCENE_SETTLE_TICKS = 100;
    private static final int VARIANT_SETTLE_TICKS = 20;
    private static final int RETURN_TICKS = 20;
    private static final int SPLASH_LEAD_TICKS = 18;
    private static final int SCREENSHOT_DOWNSCALE = 2;
    private static final int FULL_SIZE = 1;
    private static final String SCREENSHOT_PREFIX = "helion_";
    private static final String SCREENSHOT_EXTENSION = ".png";
    private static final boolean DEBUG_MODE_DURING_TEST = true;

    private final List<Shot> shots = VisualTestScene.ALL.stream()
        .filter(scene -> selected(SCENES_PROPERTY, scene.name()))
        .flatMap(scene -> Arrays.stream(VisualTestVariant.values())
            .filter(variant -> selected(VARIANTS_PROPERTY, variant.name().toLowerCase(Locale.ROOT)))
            .map(variant -> new Shot(scene, variant)))
        .toList();
    private int nextShot;
    private int ticksUntilAction = START_DELAY_TICKS;
    private boolean prepared;
    private boolean finished;
    private boolean hudWasHidden;
    private boolean pausedOnLostFocus;
    private VisualTestScene scene = VisualTestScene.ALL.getFirst();
    private final AtomicReference<ParityResult> parityResult = new AtomicReference<>();
    private final AtomicBoolean parityAborted = new AtomicBoolean();
    private boolean parityStarted;
    private Vec3 start = Vec3.ZERO;
    private BlockPos origin = BlockPos.ZERO;

    private VisualTest() {
    }

    private static boolean selected(String property, String name) {
        String list = System.getProperty(property);
        return list == null || Arrays.asList(list.split(LIST_SEPARATOR)).contains(name);
    }

    public static Optional<VisualTest> requested() {
        if (FMLEnvironment.isProduction() || !Boolean.getBoolean(PROPERTY)) {
            return Optional.empty();
        }
        return Optional.of(new VisualTest());
    }

    public void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (finished || player == null || server == null) {
            return;
        }
        minecraft.gui.setScreen(null);
        if (prepared) {
            holdCamera(player);
        }
        ticksUntilAction--;
        if (prepared && scene.rain() && ticksUntilAction == SPLASH_LEAD_TICKS) {
            VisualTestWorld.dropIntoRainPool(server, origin);
        }
        if (ticksUntilAction > 0) {
            return;
        }
        if (!prepared) {
            prepare(minecraft, player, server);
            beginShot(server);
            return;
        }
        if (nextShot == shots.size()) {
            finish(minecraft);
            return;
        }
        Shot shot = shots.get(nextShot);
        VoxelLightingResources voxels = HelionRenderCore.get().voxelLighting();
        if (voxels.active() && voxels.pendingSections() > 0) {
            return;
        }
        if (shot.variant().comparesWithVanilla()) {
            if (!parityFinished(shot, server)) {
                return;
            }
        } else {
            capture(minecraft, shot);
        }
        nextShot++;
        if (nextShot == shots.size()) {
            VisualTestWorld.placeCamera(server, start, origin, BlockPos.ZERO);
            ticksUntilAction = RETURN_TICKS;
            return;
        }
        beginShot(server);
    }

    private void prepare(Minecraft minecraft, LocalPlayer player, IntegratedServer server) {
        prepared = true;
        checkSkyBeforeVanillaFrames(minecraft);
        hudWasHidden = minecraft.gui.hud.isHidden();
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        pausedOnLostFocus = minecraft.options.pauseOnLostFocus;
        minecraft.options.pauseOnLostFocus = false;
        start = player.position();
        origin = player.blockPosition();
        VisualTestWorld.prepare(server, origin, !Boolean.getBoolean(NATURAL_SCENE_PROPERTY));
        LOGGER.info("Helion visual test started with {} shots", shots.size());
    }

    private static void checkSkyBeforeVanillaFrames(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || !HelionRenderCore.get().isActive()) {
            return;
        }
        DimensionType.Skybox expected = level.dimensionType().skybox();
        DimensionType.Skybox extracted = ((LevelRendererAccessor) minecraft.levelRenderer).helion$levelRenderState().skyRenderState.skybox;
        if (extracted == expected) {
            LOGGER.info("Helion visual test startup: the sky state ({}) is extracted before any vanilla frame", extracted);
        } else {
            LOGGER.warn("Helion visual test startup: the sky state is {} instead of {} before any vanilla frame, so Helion draws no sky", extracted, expected);
        }
    }

    private void beginShot(IntegratedServer server) {
        Shot shot = shots.get(nextShot);
        if (nextShot == 0 || shot.scene() != scene) {
            scene = shot.scene();
            VisualTestWorld.setTime(server, scene.dayTime());
            VisualTestWorld.setRain(server, scene.rain());
            if (scene.wet()) {
                HelionRenderCore.get().water().rainWetness().saturate();
            }
            VisualTestWorld.placeCamera(server, start, origin, scene.cameraOffset());
            ticksUntilAction = SCENE_SETTLE_TICKS;
        } else {
            ticksUntilAction = VARIANT_SETTLE_TICKS;
        }
        if (shot.variant().comparesWithVanilla()) {
            VisualTestWorld.freezeTicks(server, true);
        }
        HelionRenderCore.get().applySettings(
            shot.variant().helion(),
            DEBUG_MODE_DURING_TEST,
            shot.variant().applyTo(HelionConfig.renderSettings())
        );
    }

    private boolean parityFinished(Shot shot, IntegratedServer server) {
        if (!parityStarted) {
            parityStarted = ParityCheck.get().start(parityResult::set, () -> parityAborted.set(true));
            if (!parityStarted) {
                LOGGER.warn("Helion visual test {}: the parity check could not start", shot.name());
                VisualTestWorld.freezeTicks(server, false);
            }
            return !parityStarted;
        }
        if (parityAborted.getAndSet(false)) {
            parityStarted = false;
            VisualTestWorld.freezeTicks(server, false);
            LOGGER.warn("Helion visual test {}: parity aborted because the Helion frame could not be rendered", shot.name());
            return true;
        }
        ParityResult result = parityResult.getAndSet(null);
        if (result == null) {
            return false;
        }
        parityStarted = false;
        VisualTestWorld.freezeTicks(server, false);
        if (result.isIdentical()) {
            LOGGER.info("Helion visual test {}: parity passed, all {} pixels match vanilla", shot.name(), result.pixels());
        } else {
            LOGGER.warn(
                "Helion visual test {}: parity failed, {} of {} pixels differ in color (largest difference {}, {} of them also in depth), {} in depth (largest difference {} steps)",
                shot.name(), result.differentColorPixels(), result.pixels(), result.maxColorDelta(), result.colorPixelsWithDifferentDepth(),
                result.differentDepthPixels(), result.maxDepthDelta()
            );
        }
        return true;
    }

    private void holdCamera(LocalPlayer player) {
        player.setYRot(scene.yaw());
        player.setXRot(scene.pitch());
        player.setYHeadRot(scene.yaw());
        player.yRotO = scene.yaw();
        player.xRotO = scene.pitch();
    }

    private static void capture(Minecraft minecraft, Shot shot) {
        String fileName = SCREENSHOT_PREFIX + shot.name() + SCREENSHOT_EXTENSION;
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.gameRenderer.mainRenderTarget(), Boolean.getBoolean(FULL_SIZE_PROPERTY) ? FULL_SIZE : SCREENSHOT_DOWNSCALE, message -> { });
        LOGGER.info("Helion visual test {}: {}", shot.name(), HelionDebugSnapshot.capture(minecraft));
    }

    private void finish(Minecraft minecraft) {
        finished = true;
        HelionRenderCore.get().applySettings(
            HelionConfig.ENABLED.getAsBoolean(),
            HelionConfig.DEBUG_MODE.getAsBoolean(),
            HelionConfig.renderSettings()
        );
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        minecraft.options.pauseOnLostFocus = pausedOnLostFocus;
        LOGGER.info("Helion visual test finished, screenshots are in {}", minecraft.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR));
        minecraft.stop();
    }

    private record Shot(VisualTestScene scene, VisualTestVariant variant) {
        String name() {
            return scene.name() + "_" + variant.fileName();
        }
    }
}
