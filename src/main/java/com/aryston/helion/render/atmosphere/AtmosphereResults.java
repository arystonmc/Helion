package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.clouds.SceneCloudPass;
import com.aryston.helion.render.lightshaft.SceneLightShaftPass;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AtmosphereResults {
    private @Nullable SceneFogPass fog;
    private @Nullable SceneSkyColor skyColor;
    private @Nullable SkyLight skyLight;
    private @Nullable SceneCloudPass clouds;
    private @Nullable SceneLightShaftPass lightShafts;

    public Optional<SceneFogPass> fog() {
        return Optional.ofNullable(fog);
    }

    public Optional<SceneSkyColor> skyColor() {
        return Optional.ofNullable(skyColor);
    }

    public Optional<SkyLight> skyLight() {
        return Optional.ofNullable(skyLight);
    }

    public Optional<SceneCloudPass> clouds() {
        return Optional.ofNullable(clouds);
    }

    public Optional<SceneLightShaftPass> lightShafts() {
        return Optional.ofNullable(lightShafts);
    }

    public void publishLightShafts(SceneLightShaftPass pass) {
        lightShafts = pass;
    }

    public void publishClouds(SceneCloudPass pass) {
        clouds = pass;
    }

    void publishSkyLight(SkyLight light) {
        skyLight = light;
    }

    void publishSkyColor(SceneSkyColor color) {
        skyColor = color;
    }

    void publishFog(SceneFogPass pass) {
        fog = pass;
    }
}
