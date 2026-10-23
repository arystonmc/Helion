package com.aryston.helion.debug;

import java.util.List;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class VisualTestWorld {
    private static final List<String> RULES = List.of(
        "gamerule advance_time false",
        "gamerule advance_weather false",
        "gamerule spawn_mobs false",
        "weather clear",
        "gamemode spectator @a"
    );
    private static final BlockPos AREA_START = new BlockPos(-6, 0, -11);
    private static final BlockPos AREA_END = new BlockPos(6, 8, 3);
    private static final int FLOOR = -1;
    private static final List<Placement> LIGHTS = List.of(
        new Placement(-6, 0, -6, "glowstone"),
        new Placement(-3, 0, -6, "sea_lantern"),
        new Placement(0, -1, -6, "lava"),
        new Placement(3, 0, -6, "stone"),
        new Placement(3, 1, -6, "torch"),
        new Placement(-6, 0, -9, "shroomlight"),
        new Placement(4, 0, -4, "torch"),
        new Placement(6, 0, -11, "soul_lantern"),
        new Placement(-6, 0, -11, "redstone_torch"),
        new Placement(-3, 0, -9, "end_rod"),
        new Placement(0, 0, -9, "jack_o_lantern[facing=south]"),
        new Placement(3, -1, -9, "redstone_block"),
        new Placement(3, 0, -9, "redstone_lamp[lit=true]")
    );
    private static final Volume CANOPY = new Volume(new BlockPos(4, 4, -5), new BlockPos(6, 4, -3));
    private static final String CANOPY_BLOCK = "oak_leaves[persistent=true]";
    private static final Volume FENCE = new Volume(new BlockPos(2, 0, -1), new BlockPos(4, 0, -1));
    private static final String FENCE_BLOCK = "oak_fence";
    private static final Volume ICE = new Volume(new BlockPos(2, -1, -3), new BlockPos(4, -1, -2));
    private static final String ICE_BLOCK = "ice";
    private static final Volume GLASS = new Volume(new BlockPos(6, 0, -2), new BlockPos(6, 1, -1));
    private static final String GLASS_BLOCK = "light_blue_stained_glass";
    private static final Volume POOL = new Volume(new BlockPos(-5, -3, -10), new BlockPos(-2, -1, -1));
    private static final int POOL_WALL = 1;
    private static final String CASTER_BLOCK = "stone";
    private static final List<Volume> SHADOW_CASTERS = List.of(
        new Volume(new BlockPos(5, 0, -4), new BlockPos(5, 3, -4)),
        new Volume(new BlockPos(-6, 2, -5), new BlockPos(-4, 2, -3))
    );

    private static final List<Summon> ENTITIES = List.of(
        new Summon(-1, 0, -3, "armor_stand"),
        new Summon(1, 0, -3, "cow")
    );
    private static final String FROZEN_ENTITY = " {NoAI:1b,NoGravity:1b,Invulnerable:1b,Rotation:[90f,0f]}";
    private static final double BLOCK_CENTER = 0.5;
    static final BlockPos CAVE_CAMERA = new BlockPos(24, -23, -2);
    private static final Volume CAVE_ROCK = new Volume(new BlockPos(12, -26, -18), new BlockPos(32, -12, 4));
    private static final List<Volume> CAVE_CORRIDORS = List.of(
        new Volume(new BlockPos(17, -23, -13), new BlockPos(25, -21, -11)),
        new Volume(new BlockPos(23, -23, -13), new BlockPos(25, -21, -1))
    );
    private static final Placement CAVE_TORCH = new Placement(21, -23, -12, "torch");
    static final BlockPos WALL_CAMERA = new BlockPos(15, -17, -6);
    private static final List<Volume> WALL_ROOMS = List.of(
        new Volume(new BlockPos(14, -17, -9), new BlockPos(20, -15, -3)),
        new Volume(new BlockPos(22, -17, -9), new BlockPos(26, -15, -3))
    );
    private static final List<Placement> WALL_LIGHTS = List.of(
        new Placement(22, -17, -8, "torch"),
        new Placement(22, -16, -6, "glowstone"),
        new Placement(22, -17, -4, "torch"),
        new Placement(23, -17, -6, "sea_lantern")
    );

    private VisualTestWorld() {
    }

    static void prepare(IntegratedServer server, BlockPos origin, boolean buildTestArea) {
        RULES.forEach(command -> run(server, command));
        if (!buildTestArea) {
            return;
        }
        run(server, fill(origin.offset(AREA_START), origin.offset(AREA_END), "air"));
        run(server, fill(
            origin.offset(AREA_START.getX(), FLOOR, AREA_START.getZ()),
            origin.offset(AREA_END.getX(), FLOOR, AREA_END.getZ()),
            "stone"
        ));
        LIGHTS.forEach(light -> run(server, light.command(origin)));
        SHADOW_CASTERS.forEach(caster -> run(server, fill(origin.offset(caster.from()), origin.offset(caster.to()), CASTER_BLOCK)));
        run(server, fill(origin.offset(POOL.from().offset(-POOL_WALL, -POOL_WALL, -POOL_WALL)), origin.offset(POOL.to().offset(POOL_WALL, 0, POOL_WALL)), CASTER_BLOCK));
        run(server, fill(origin.offset(POOL.from()), origin.offset(POOL.to()), "water"));
        run(server, fill(origin.offset(CANOPY.from()), origin.offset(CANOPY.to()), CANOPY_BLOCK));
        run(server, fill(origin.offset(FENCE.from()), origin.offset(FENCE.to()), FENCE_BLOCK));
        run(server, fill(origin.offset(ICE.from()), origin.offset(ICE.to()), ICE_BLOCK));
        run(server, fill(origin.offset(GLASS.from()), origin.offset(GLASS.to()), GLASS_BLOCK));
        ENTITIES.forEach(entity -> run(server, entity.command(origin)));
        run(server, killEntities(origin.offset(CAVE_ROCK.from()), origin.offset(CAVE_ROCK.to())));
        run(server, fill(origin.offset(CAVE_ROCK.from()), origin.offset(CAVE_ROCK.to()), CASTER_BLOCK));
        CAVE_CORRIDORS.forEach(corridor -> run(server, fill(origin.offset(corridor.from()), origin.offset(corridor.to()), "air")));
        run(server, CAVE_TORCH.command(origin));
        WALL_ROOMS.forEach(room -> run(server, fill(origin.offset(room.from()), origin.offset(room.to()), "air")));
        WALL_LIGHTS.forEach(light -> run(server, light.command(origin)));
    }

    static void placeCamera(IntegratedServer server, Vec3 start, BlockPos origin, BlockPos cameraOffset) {
        Vec3 position = cameraOffset.equals(BlockPos.ZERO) ? start : Vec3.atBottomCenterOf(origin.offset(cameraOffset));
        run(server, "tp @a " + position.x + " " + position.y + " " + position.z);
    }

    static void setTime(IntegratedServer server, int dayTime) {
        run(server, "time set " + dayTime);
    }

    static void freezeTicks(IntegratedServer server, boolean frozen) {
        run(server, frozen ? "tick freeze" : "tick unfreeze");
    }

    private static String fill(BlockPos from, BlockPos to, String block) {
        return "fill " + coordinates(from) + " " + coordinates(to) + " " + block;
    }

    private static String killEntities(BlockPos from, BlockPos to) {
        BlockPos size = to.subtract(from);
        return "kill @e[type=!player,x=" + from.getX() + ",y=" + from.getY() + ",z=" + from.getZ()
            + ",dx=" + size.getX() + ",dy=" + size.getY() + ",dz=" + size.getZ() + "]";
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void run(IntegratedServer server, String command) {
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        });
    }

    private record Volume(BlockPos from, BlockPos to) {
    }

    private record Placement(int x, int y, int z, String block) {
        String command(BlockPos origin) {
            return "setblock " + coordinates(origin.offset(x, y, z)) + " " + block;
        }
    }

    private record Summon(int x, int y, int z, String entity) {
        String command(BlockPos origin) {
            BlockPos position = origin.offset(x, y, z);
            return "summon " + entity + " " + (position.getX() + BLOCK_CENTER) + " " + position.getY() + " " + (position.getZ() + BLOCK_CENTER) + FROZEN_ENTITY;
        }
    }
}
