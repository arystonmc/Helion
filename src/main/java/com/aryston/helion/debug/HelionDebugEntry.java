package com.aryston.helion.debug;

import com.aryston.helion.Helion;
import com.aryston.helion.render.HelionRenderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import org.jspecify.annotations.Nullable;

public final class HelionDebugEntry implements DebugScreenEntry {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "debug_mode");

    public static void register(RegisterDebugEntriesEvent event) {
        event.register(ID, new HelionDebugEntry());
        event.includeInProfile(ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.ALWAYS_ON);
    }

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        if (!HelionRenderCore.get().isDebugMode()) {
            return;
        }
        displayer.addToGroup(ID, DebugOverlayLines.format(HelionDebugSnapshot.capture(Minecraft.getInstance())));
    }
}
