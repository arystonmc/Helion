package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.clouds.SceneCloudPass;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AtmosphereResults {
    private @Nullable SceneFogPass fog;
    private @Nullable SkyLight skyLight;
    private @Nullable SceneCloudPass clouds;

    public Optional<SceneFogPass> fog() {
        return Optional.ofNullable(fog);
    }

    public Optional<SkyLight> skyLight() {
        return Optional.ofNullable(skyLight);
    }

    public Optional<SceneCloudPass> clouds() {
        return Optional.ofNullable(clouds);
    }

    public void publishClouds(SceneCloudPass pass) {
        clouds = pass;
    }

    void publishSkyLight(SkyLight light) {
        skyLight = light;
    }

    void publishFog(SceneFogPass pass) {
        fog = pass;
    }
}
