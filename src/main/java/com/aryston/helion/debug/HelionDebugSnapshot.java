package com.aryston.helion.debug;

import com.aryston.helion.Helion;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.backend.GpuDeviceSummary;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.camera.HelionFrustum;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageResources;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.scene.SceneSnapshot;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import org.joml.Vector3dc;
import org.joml.Vector4fc;

public final class HelionDebugSnapshot {
    private static final double ROUNDING_SCALE = 1000.0;
    private static final double BYTES_PER_MEBIBYTE = 1024.0 * 1024.0;
    private static final String NONE = "none";
    private static final String COLOR_FORMAT = "%.3f, %.3f, %.3f, %.3f";

    private HelionDebugSnapshot() {
    }

    public static JsonObject capture(Minecraft minecraft) {
        HelionRenderCore core = HelionRenderCore.get();
        RenderSettings settings = core.settings();
        float ambientLight = core.lastScene().map(SceneSnapshot::ambientLight).orElse(0.0F);
        JsonObject snapshot = new JsonObject();
        snapshot.add("core", core(core));
        core.device().ifPresent(device -> snapshot.add("device", device(device)));
        snapshot.add("frame", frame(minecraft, core));
        core.lastCamera().ifPresent(camera -> snapshot.add("camera", camera(camera)));
        snapshot.add("ambientOcclusion", ambientOcclusion(settings.ambientOcclusion()));
        snapshot.add("image", image(settings.image()));
        snapshot.add("bloom", bloom(settings.image().bloom(), ambientLight));
        snapshot.add("sharpening", sharpening(settings.image().sharpening()));
        snapshot.add("gpu", gpu(core));
        snapshot.add("gpuStages", gpuStages(core.timings().averageMillis()));
        snapshot.add("resources", resources(core.resources().live()));
        snapshot.add("resourceList", resourceList(core.resources().live()));
        core.lastCamera().ifPresent(camera -> snapshot.add("frustum", frustum(minecraft, camera.frustum())));
        return snapshot;
    }

    private static JsonObject core(HelionRenderCore core) {
        JsonObject section = new JsonObject();
        section.addProperty("version", ModList.get().getModContainerById(Helion.MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(NONE));
        section.addProperty("active", core.isActive());
        section.addProperty("enabled", core.isEnabled());
        section.addProperty("debugMode", core.isDebugMode());
        section.addProperty("passiveReason", core.passiveReason().map(Enum::name).orElse(NONE));
        section.addProperty("parityRunning", ParityCheck.get().isRunning());
        return section;
    }

    private static JsonObject device(GpuDeviceSummary device) {
        JsonObject section = new JsonObject();
        section.addProperty("name", device.name());
        section.addProperty("vendor", device.vendor());
        section.addProperty("backend", device.backend());
        section.addProperty("driver", device.driver());
        section.addProperty("zeroToOneDepth", device.zeroToOneDepth());
        section.addProperty("timestamps", device.supportsTimestamps());
        return section;
    }

    private static JsonObject frame(Minecraft minecraft, HelionRenderCore core) {
        JsonObject section = new JsonObject();
        Window window = minecraft.getWindow();
        section.addProperty("fps", minecraft.getFps());
        section.addProperty("windowWidth", window.getWidth());
        section.addProperty("windowHeight", window.getHeight());
        core.lastScene().ifPresent(scene -> {
            section.addProperty("sceneWidth", scene.width());
            section.addProperty("sceneHeight", scene.height());
            section.addProperty("colorFormat", String.valueOf(scene.colorFormat()));
            section.addProperty("renderSky", scene.renderSky());
            section.addProperty("improvedTransparency", scene.orderIndependentTransparency());
            section.addProperty("ambientLight", round(scene.ambientLight()));
            section.addProperty("fogColor", fogColor(scene.fogColor()));
        });
        return section;
    }

    private static JsonObject camera(HelionCamera camera) {
        Vector3dc position = camera.position();
        JsonObject section = new JsonObject();
        section.addProperty("x", round(position.x()));
        section.addProperty("y", round(position.y()));
        section.addProperty("z", round(position.z()));
        return section;
    }

    private static JsonObject ambientOcclusion(AmbientOcclusionSettings settings) {
        JsonObject section = new JsonObject();
        section.addProperty("enabled", settings.enabled());
        section.addProperty("algorithm", settings.algorithm().name());
        section.addProperty("quality", settings.quality().name());
        section.addProperty("strength", round(settings.strength()));
        section.addProperty("radius", round(settings.radius()));
        section.addProperty("debugView", settings.debugView());
        return section;
    }

    private static JsonObject image(ImageSettings settings) {
        JsonObject section = new JsonObject();
        section.addProperty("toneMapper", settings.toneMapper().name());
        section.addProperty("exposure", round(settings.exposure()));
        section.addProperty("dither", settings.dither());
        section.addProperty("composite", settings.needsComposite());
        return section;
    }

    private static JsonObject bloom(BloomSettings settings, float ambientLight) {
        JsonObject section = new JsonObject();
        section.addProperty("enabled", settings.enabled());
        section.addProperty("intensity", round(settings.intensity()));
        section.addProperty("threshold", round(settings.threshold()));
        section.addProperty("effectiveThreshold", round(ImageResources.bloomThreshold(settings, ambientLight)));
        section.addProperty("debugView", settings.debugView());
        return section;
    }

    private static JsonObject sharpening(SharpeningSettings settings) {
        JsonObject section = new JsonObject();
        section.addProperty("enabled", settings.enabled());
        section.addProperty("strength", round(settings.strength()));
        return section;
    }

    private static JsonObject gpu(HelionRenderCore core) {
        Map<String, Double> timings = core.timings().averageMillis();
        JsonObject section = new JsonObject();
        section.addProperty("measuring", !timings.isEmpty());
        section.addProperty("totalMillis", round(timings.values().stream().mapToDouble(Double::doubleValue).sum()));
        return section;
    }

    private static JsonObject gpuStages(Map<String, Double> timings) {
        JsonObject section = new JsonObject();
        timings.forEach((stage, millis) -> section.addProperty(stage, round(millis)));
        return section;
    }

    private static JsonObject resources(List<TrackedResource> live) {
        JsonObject section = new JsonObject();
        section.addProperty("count", live.size());
        section.addProperty("mebibytes", round(live.stream().mapToLong(TrackedResource::bytes).sum() / BYTES_PER_MEBIBYTE));
        return section;
    }

    private static JsonObject resourceList(List<TrackedResource> live) {
        Map<String, Long> bytesByLabel = live.stream().collect(Collectors.groupingBy(
            TrackedResource::label, TreeMap::new, Collectors.summingLong(TrackedResource::bytes)
        ));
        JsonObject section = new JsonObject();
        bytesByLabel.forEach((label, bytes) -> section.addProperty(label, round(bytes / BYTES_PER_MEBIBYTE)));
        return section;
    }

    private static JsonObject frustum(Minecraft minecraft, HelionFrustum frustum) {
        List<SectionRenderDispatcher.RenderSection> sections = minecraft.levelRenderer.visibleSections();
        long inside = sections.stream()
            .map(SectionRenderDispatcher.RenderSection::getBoundingBox)
            .filter(box -> intersects(frustum, box))
            .count();
        JsonObject section = new JsonObject();
        section.addProperty("helionSections", inside);
        section.addProperty("vanillaSections", sections.size());
        return section;
    }

    private static boolean intersects(HelionFrustum frustum, AABB box) {
        return frustum.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static String fogColor(Vector4fc color) {
        return String.format(Locale.ROOT, COLOR_FORMAT, color.x(), color.y(), color.z(), color.w());
    }

    private static double round(double value) {
        return Math.round(value * ROUNDING_SCALE) / ROUNDING_SCALE;
    }
}
