package com.aryston.helion.debug;

import com.aryston.helion.Helion;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.camera.HelionFrustum;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import org.jspecify.annotations.Nullable;

public final class HelionDebugEntry implements DebugScreenEntry {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "render_core");
    private static final double BYTES_PER_MEBIBYTE = 1024.0 * 1024.0;

    public static void register(RegisterDebugEntriesEvent event) {
        event.register(ID, new HelionDebugEntry());
        event.includeInProfile(ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
    }

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        HelionRenderCore core = HelionRenderCore.get();
        List<String> lines = new ArrayList<>();
        lines.add("Helion: " + status(core));
        core.device().ifPresent(device -> lines.add("Helion GPU: " + device.backend() + ", " + device.name()));
        Map<String, Double> timings = core.timings().averageMillis();
        if (!timings.isEmpty()) {
            lines.add(String.format(Locale.ROOT, "Helion stages (%.2f ms): %s", HelionStatsLines.totalMillis(timings), HelionStatsLines.timings(timings)));
        }
        HelionStatsLines.image(core).ifPresent(lines::add);
        lines.add(String.format(Locale.ROOT, "Helion resources: %d, %.1f MiB", core.resources().count(), core.resources().totalBytes() / BYTES_PER_MEBIBYTE));
        core.lastCamera().ifPresent(camera -> lines.add(frustumLine(camera.frustum())));
        displayer.addToGroup(ID, lines);
    }

    private static String status(HelionRenderCore core) {
        return core.passiveReason()
            .map(reason -> "passive (" + reason.name().toLowerCase(Locale.ROOT) + ")")
            .orElseGet(() -> core.isEnabled() ? "active" : "disabled");
    }

    private static String frustumLine(HelionFrustum frustum) {
        List<SectionRenderDispatcher.RenderSection> sections = Minecraft.getInstance().levelRenderer.visibleSections();
        long inside = sections.stream()
            .map(SectionRenderDispatcher.RenderSection::getBoundingBox)
            .filter(box -> intersects(frustum, box))
            .count();
        return "Helion frustum: " + inside + " of " + sections.size() + " visible sections";
    }

    private static boolean intersects(HelionFrustum frustum, AABB box) {
        return frustum.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
