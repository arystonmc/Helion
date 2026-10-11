package com.aryston.helion.debug;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class GalleryWorld {
    private static final String WORLD_NAME = "Helion Gallery";
    private static final String NO_SUFFIX = "";
    private static final long SEED = 20261011L;
    private static final boolean GENERATE_STRUCTURES = true;
    private static final boolean BONUS_CHEST = false;
    private static final boolean ALLOW_COMMANDS = true;
    private static final double BLOCK_CENTER = 0.5;

    private GalleryWorld() {
    }

    static void create(Minecraft minecraft) throws IOException {
        String folder = FileUtil.findAvailableName(minecraft.getLevelSource().getBaseDir(), WORLD_NAME, NO_SUFFIX);
        LevelSettings settings = new LevelSettings(
            WORLD_NAME, GameType.SPECTATOR, LevelSettings.DifficultySettings.DEFAULT, ALLOW_COMMANDS, WorldDataConfiguration.DEFAULT
        );
        minecraft.createWorldOpenFlows().createFreshLevel(
            folder, settings, new WorldOptions(SEED, GENERATE_STRUCTURES, BONUS_CHEST), WorldPresets::createNormalWorldDimensions, minecraft.gui.screen()
        );
    }

    static CompletableFuture<Optional<BlockPos>> locateSurface(IntegratedServer server, GalleryLocator locator, BlockPos origin) {
        Supplier<Optional<BlockPos>> search = () -> {
            ServerLevel level = server.overworld();
            return locator.locate(level, origin).map(found -> surface(level, found));
        };
        return server.submit(search);
    }

    static void teleport(IntegratedServer server, BlockPos surface, int heightAboveSurface, float yaw, float pitch) {
        VisualTestWorld.run(server, "tp @a " + (surface.getX() + BLOCK_CENTER) + " " + (surface.getY() + heightAboveSurface) + " "
            + (surface.getZ() + BLOCK_CENTER) + " " + yaw + " " + pitch);
    }

    private static BlockPos surface(ServerLevel level, BlockPos position) {
        level.getChunkAt(position);
        return position.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, position.getX(), position.getZ()));
    }
}
