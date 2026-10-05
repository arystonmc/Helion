package com.aryston.helion.integration;

import com.aryston.helion.debug.ParityCheck;
import com.aryston.helion.debug.ParityResult;
import com.aryston.helion.render.HelionRenderCore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class HelionCommands {
    private static final String ROOT = "helion";

    private HelionCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
            .then(Commands.literal("status").executes(HelionCommands::status))
            .then(Commands.literal("toggle").executes(HelionCommands::toggle))
            .then(Commands.literal("parity").executes(HelionCommands::parity)));
    }

    static Component enabledState(boolean enabled) {
        return Component.translatable(enabled ? "helion.state.enabled" : "helion.state.disabled");
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        HelionRenderCore core = HelionRenderCore.get();
        Component state = core.passiveReason()
            .<Component>map(reason -> Component.translatable(reason.translationKey()))
            .orElseGet(() -> enabledState(core.isEnabled()));
        context.getSource().sendSuccess(() -> Component.translatable("helion.command.status", state), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        boolean enabled = HelionRenderCore.get().toggle();
        context.getSource().sendSuccess(() -> enabledState(enabled), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int parity(CommandContext<CommandSourceStack> context) {
        if (!HelionRenderCore.get().isActive()) {
            context.getSource().sendFailure(Component.translatable("helion.command.parity.inactive"));
            return 0;
        }
        if (!ParityCheck.get().start(HelionCommands::reportParity)) {
            context.getSource().sendFailure(Component.translatable("helion.command.parity.busy"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("helion.command.parity.started"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void reportParity(ParityResult result) {
        Component message = result.isIdentical()
            ? Component.translatable("helion.command.parity.identical", result.pixels())
            : Component.translatable(
                "helion.command.parity.different",
                result.differentColorPixels(),
                result.pixels(),
                result.maxColorDelta(),
                result.differentDepthPixels()
            );
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
    }
}
