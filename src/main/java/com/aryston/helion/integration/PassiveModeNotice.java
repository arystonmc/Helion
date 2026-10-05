package com.aryston.helion.integration;

import com.aryston.helion.render.PassiveReason;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

final class PassiveModeNotice {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();
    private static final Queue<PassiveReason> PENDING = new ArrayDeque<>();

    private PassiveModeNotice() {
    }

    static void queue(PassiveReason reason) {
        PENDING.add(reason);
    }

    static void showPending(Minecraft minecraft) {
        if (minecraft.gui == null) {
            return;
        }
        for (PassiveReason reason = PENDING.poll(); reason != null; reason = PENDING.poll()) {
            show(minecraft, Component.translatable("helion.passive.title"), Component.translatable(reason.translationKey()));
        }
    }

    static void show(Minecraft minecraft, Component title, Component message) {
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST_ID, title, message);
    }
}
