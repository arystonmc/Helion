package com.aryston.helion.debug;

import com.aryston.helion.config.HelionConfig;
import com.aryston.helion.render.HelionRenderCore;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class GalleryCapture {
    private static final String PROPERTY = "helion.gallery";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MENU_DELAY_TICKS = 100;
    private static final int WORLD_DELAY_TICKS = 100;
    private static final int POLL_TICKS = 1;
    private static final int SPOT_SETTLE_TICKS = 300;
    private static final int SECTION_WAIT_LIMIT_TICKS = 900;
    private static final int AIM_SETTLE_TICKS = 40;
    private static final int PATH_WARM_UP_TICKS = 20;
    private static final int FULL_RESOLUTION = 1;
    private static final String SCREENSHOT_PREFIX = "gallery_";
    private static final String SCREENSHOT_EXTENSION = ".png";
    private static final String NAME_SEPARATOR = "_";
    private static final String VANILLA_PATH = "vanilla";
    private static final String HELION_PATH = "helion";

    private Phase phase = Phase.CREATE_WORLD;
    private int ticksUntilAction = MENU_DELAY_TICKS;
    private int sectionWaitTicks;
    private int spotIndex;
    private int yawIndex;
    private int savedFrames;
    private BlockPos origin = BlockPos.ZERO;
    private CompletableFuture<Optional<BlockPos>> located = CompletableFuture.completedFuture(Optional.empty());
    private boolean hudWasHidden;
    private boolean pausedOnLostFocus;

    private GalleryCapture() {
    }

    public static Optional<GalleryCapture> requested() {
        if (FMLEnvironment.isProduction() || !Boolean.getBoolean(PROPERTY)) {
            return Optional.empty();
        }
        return Optional.of(new GalleryCapture());
    }

    public void tick(Minecraft minecraft) {
        if (phase == Phase.FINISHED || minecraft.gui.overlay() != null) {
            return;
        }
        if (phase.beforeWorld()) {
            tickBeforeWorld(minecraft);
            return;
        }
        LocalPlayer player = minecraft.player;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (player == null || server == null) {
            return;
        }
        minecraft.gui.setScreen(null);
        if (phase.holdsCamera()) {
            holdCamera(player);
        }
        if (--ticksUntilAction > 0) {
            return;
        }
        switch (phase) {
            case PREPARE -> prepare(minecraft, player, server);
            case LOCATE -> arrive(minecraft, server);
            case SETTLE -> settle(minecraft);
            case AIM -> showVanilla(server);
            case VANILLA_FRAME -> captureVanilla(minecraft);
            case HELION_FRAME -> captureHelion(minecraft, server);
            default -> {
            }
        }
    }

    private void tickBeforeWorld(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.player != null) {
            phase = Phase.PREPARE;
            ticksUntilAction = WORLD_DELAY_TICKS;
            return;
        }
        if (phase == Phase.AWAIT_WORLD || minecraft.gui.screen() == null || --ticksUntilAction > 0) {
            return;
        }
        phase = Phase.AWAIT_WORLD;
        try {
            GalleryWorld.create(minecraft);
            LOGGER.info("Helion gallery is creating its world");
        } catch (IOException failure) {
            LOGGER.error("Helion gallery could not create its world", failure);
            phase = Phase.FINISHED;
            minecraft.stop();
        }
    }

    private void prepare(Minecraft minecraft, LocalPlayer player, IntegratedServer server) {
        hudWasHidden = minecraft.gui.hud.isHidden();
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        pausedOnLostFocus = minecraft.options.pauseOnLostFocus;
        minecraft.options.pauseOnLostFocus = false;
        origin = player.blockPosition();
        VisualTestWorld.prepare(server, origin, false);
        showHelion(true);
        LOGGER.info("Helion gallery started with {} spots and settings {}", GallerySpot.ALL.size(), HelionConfig.renderSettings());
        beginSpot(server);
    }

    private void beginSpot(IntegratedServer server) {
        yawIndex = 0;
        located = GalleryWorld.locateSurface(server, spot().locator(), origin);
        phase = Phase.LOCATE;
        ticksUntilAction = POLL_TICKS;
    }

    private void arrive(Minecraft minecraft, IntegratedServer server) {
        if (!located.isDone()) {
            ticksUntilAction = POLL_TICKS;
            return;
        }
        GallerySpot spot = spot();
        Optional<BlockPos> surface = located.exceptionally(failure -> {
            LOGGER.warn("Helion gallery {}: the search failed", spot.name(), failure);
            return Optional.empty();
        }).join();
        if (surface.isEmpty()) {
            LOGGER.warn("Helion gallery {}: no place found, the spot is skipped", spot.name());
            nextSpot(minecraft, server);
            return;
        }
        LOGGER.info("Helion gallery {}: surface at {}", spot.name(), surface.get());
        GalleryWorld.teleport(server, surface.get(), spot.heightAboveSurface(), yaw(), spot.pitch());
        VisualTestWorld.setTime(server, spot.dayTime());
        phase = Phase.SETTLE;
        ticksUntilAction = SPOT_SETTLE_TICKS;
        sectionWaitTicks = 0;
    }

    private void settle(Minecraft minecraft) {
        if (!minecraft.levelRenderer.hasRenderedAllSections() && sectionWaitTicks < SECTION_WAIT_LIMIT_TICKS) {
            sectionWaitTicks++;
            ticksUntilAction = POLL_TICKS;
            return;
        }
        aim();
    }

    private void aim() {
        phase = Phase.AIM;
        ticksUntilAction = AIM_SETTLE_TICKS;
    }

    private void showVanilla(IntegratedServer server) {
        VisualTestWorld.freezeTicks(server, true);
        showHelion(false);
        phase = Phase.VANILLA_FRAME;
        ticksUntilAction = PATH_WARM_UP_TICKS;
    }

    private void captureVanilla(Minecraft minecraft) {
        save(minecraft, VANILLA_PATH);
        showHelion(true);
        phase = Phase.HELION_FRAME;
        ticksUntilAction = PATH_WARM_UP_TICKS;
    }

    private void captureHelion(Minecraft minecraft, IntegratedServer server) {
        HelionRenderCore core = HelionRenderCore.get();
        if (core.isActive()) {
            save(minecraft, HELION_PATH);
        } else {
            LOGGER.warn("Helion gallery {} {}: Helion did not render ({}), the frame is skipped", spot().name(), yaw(), core.passiveReason());
        }
        VisualTestWorld.freezeTicks(server, false);
        yawIndex++;
        if (yawIndex < spot().yaws().size()) {
            aim();
            return;
        }
        nextSpot(minecraft, server);
    }

    private void nextSpot(Minecraft minecraft, IntegratedServer server) {
        spotIndex++;
        if (spotIndex < GallerySpot.ALL.size()) {
            beginSpot(server);
            return;
        }
        finish(minecraft);
    }

    private void save(Minecraft minecraft, String path) {
        String fileName = SCREENSHOT_PREFIX + spot().name() + NAME_SEPARATOR + yaw() + NAME_SEPARATOR + path + SCREENSHOT_EXTENSION;
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.gameRenderer.mainRenderTarget(), FULL_RESOLUTION, message -> { });
        savedFrames++;
        LOGGER.info("Helion gallery saved {}", fileName);
    }

    private void holdCamera(LocalPlayer player) {
        float yaw = yaw();
        float pitch = spot().pitch();
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    private static void showHelion(boolean enabled) {
        HelionRenderCore.get().applySettings(enabled, HelionConfig.DEBUG_MODE.getAsBoolean(), HelionConfig.renderSettings());
    }

    private void finish(Minecraft minecraft) {
        phase = Phase.FINISHED;
        HelionRenderCore.get().applySettings(
            HelionConfig.ENABLED.getAsBoolean(),
            HelionConfig.DEBUG_MODE.getAsBoolean(),
            HelionConfig.renderSettings()
        );
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        minecraft.options.pauseOnLostFocus = pausedOnLostFocus;
        LOGGER.info("Helion gallery finished, {} screenshots are in {}", savedFrames, minecraft.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR));
        minecraft.stop();
    }

    private GallerySpot spot() {
        return GallerySpot.ALL.get(spotIndex);
    }

    private int yaw() {
        return spot().yaws().get(yawIndex);
    }

    private enum Phase {
        CREATE_WORLD(true, false),
        AWAIT_WORLD(true, false),
        PREPARE(false, false),
        LOCATE(false, false),
        SETTLE(false, true),
        AIM(false, true),
        VANILLA_FRAME(false, true),
        HELION_FRAME(false, true),
        FINISHED(false, false);

        private final boolean beforeWorld;
        private final boolean holdsCamera;

        Phase(boolean beforeWorld, boolean holdsCamera) {
            this.beforeWorld = beforeWorld;
            this.holdsCamera = holdsCamera;
        }

        boolean beforeWorld() {
            return beforeWorld;
        }

        boolean holdsCamera() {
            return holdsCamera;
        }
    }
}
