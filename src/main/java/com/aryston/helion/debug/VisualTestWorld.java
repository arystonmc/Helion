package com.aryston.helion.debug;

import java.util.List;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

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
        new Placement(6, 0, -6, "shroomlight"),
        new Placement(-3, 0, -9, "end_rod"),
        new Placement(0, 0, -9, "jack_o_lantern[facing=south]"),
        new Placement(3, -1, -9, "redstone_block"),
        new Placement(3, 0, -9, "redstone_lamp[lit=true]")
    );

    private VisualTestWorld() {
    }

    static void prepare(IntegratedServer server, BlockPos origin) {
        RULES.forEach(command -> run(server, command));
        run(server, fill(origin.offset(AREA_START), origin.offset(AREA_END), "air"));
        run(server, fill(
            origin.offset(AREA_START.getX(), FLOOR, AREA_START.getZ()),
            origin.offset(AREA_END.getX(), FLOOR, AREA_END.getZ()),
            "stone"
        ));
        LIGHTS.forEach(light -> run(server, light.command(origin)));
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

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void run(IntegratedServer server, String command) {
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        });
    }

    private record Placement(int x, int y, int z, String block) {
        String command(BlockPos origin) {
            return "setblock " + coordinates(origin.offset(x, y, z)) + " " + block;
        }
    }
}
