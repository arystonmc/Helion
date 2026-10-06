package com.aryston.helion.integration;

import com.aryston.helion.debug.HelionDebugLog;
import com.aryston.helion.debug.ParityCheck;
import com.aryston.helion.debug.ParityResult;
import com.aryston.helion.render.HelionRenderCore;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ParityControl {
    private static final String EVENT = "parity";

    private ParityControl() {
    }

    static void start(Minecraft minecraft, HelionDebugLog log) {
        if (!HelionRenderCore.get().isActive()) {
            chat(minecraft, Component.translatable("helion.parity.inactive"));
            return;
        }
        boolean frozen = isFrozen(minecraft);
        if (!ParityCheck.get().start(result -> report(minecraft, log, result, frozen), () -> reportAborted(minecraft, log))) {
            chat(minecraft, Component.translatable("helion.parity.busy"));
            return;
        }
        chat(minecraft, Component.translatable("helion.parity.started"));
    }

    private static void report(Minecraft minecraft, HelionDebugLog log, ParityResult result, boolean frozen) {
        chat(minecraft, result.isIdentical()
            ? Component.translatable("helion.parity.identical", result.pixels())
            : Component.translatable(
                "helion.parity.different",
                result.differentColorPixels(),
                result.pixels(),
                result.maxColorDelta(),
                result.differentDepthPixels(),
                result.maxDepthDelta()
            ));
        if (!result.isIdentical()) {
            chat(minecraft, Component.translatable("helion.parity.differences_image"));
        }
        if (!result.isIdentical() && !frozen) {
            chat(minecraft, Component.translatable("helion.parity.not_frozen"));
        }
        JsonObject data = new JsonObject();
        data.addProperty("identical", result.isIdentical());
        data.addProperty("frozen", frozen);
        data.addProperty("pixels", result.pixels());
        data.addProperty("differentColorPixels", result.differentColorPixels());
        data.addProperty("maxColorDelta", result.maxColorDelta());
        data.addProperty("differentDepthPixels", result.differentDepthPixels());
        data.addProperty("maxDepthDelta", result.maxDepthDelta());
        data.addProperty("colorPixelsWithDifferentDepth", result.colorPixelsWithDifferentDepth());
        log.event(EVENT, data);
    }

    private static void reportAborted(Minecraft minecraft, HelionDebugLog log) {
        chat(minecraft, Component.translatable("helion.parity.aborted"));
        JsonObject data = new JsonObject();
        data.addProperty("aborted", true);
        log.event(EVENT, data);
    }

    private static boolean isFrozen(Minecraft minecraft) {
        return minecraft.level != null && minecraft.level.tickRateManager().isFrozen();
    }

    private static void chat(Minecraft minecraft, Component message) {
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
    }
}
