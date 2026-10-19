package com.aryston.helion.render.lighting;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class DynamicLightResources {
    public static final int MAX_LIGHTS = 16;
    public static final String UNIFORM = "HelionDynamicLights";
    public static final String DEFINE = "HELION_DYNAMIC_LIGHTS";
    public static final String MAX_LIGHTS_DEFINE = "HELION_MAX_DYNAMIC_LIGHTS";
    private static final String LABEL = "Helion Dynamic Lights";
    private static final float UNUSED = 0.0F;
    private static final int UNIFORM_SIZE = uniformSize();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean written;

    public DynamicLightResources(GpuResources resources) {
        this.resources = resources;
    }

    public Optional<GpuBuffer> upload(List<DynamicLight> lights) {
        if (lights.isEmpty()) {
            return Optional.empty();
        }
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        int count = Math.min(lights.size(), MAX_LIGHTS);
        GpuBuffer buffer = uniforms.write(builder -> write(builder, lights, count));
        written = true;
        return Optional.of(buffer);
    }

    public void finishFrame() {
        if (written && uniforms != null) {
            uniforms.rotate();
        }
        written = false;
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        written = false;
    }

    private static void write(Std140Builder builder, List<DynamicLight> lights, int count) {
        for (int index = 0; index < MAX_LIGHTS; index++) {
            if (index < count) {
                DynamicLight light = lights.get(index);
                builder.putVec4(light.x(), light.y(), light.z(), light.level());
            } else {
                builder.putVec4(UNUSED, UNUSED, UNUSED, UNUSED);
            }
        }
        builder.putInt(count);
    }

    private static int uniformSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator();
        for (int index = 0; index < MAX_LIGHTS; index++) {
            calculator.putVec4();
        }
        return calculator.putInt().get();
    }
}
