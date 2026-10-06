package com.aryston.helion.render.atmosphere;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AtmosphereResults {
    private @Nullable SceneFogPass fog;
    private @Nullable SkyLight skyLight;

    public Optional<SceneFogPass> fog() {
        return Optional.ofNullable(fog);
    }

    public Optional<SkyLight> skyLight() {
        return Optional.ofNullable(skyLight);
    }

    void publishSkyLight(SkyLight light) {
        skyLight = light;
    }

    void publishFog(SceneFogPass pass) {
        fog = pass;
    }
}
