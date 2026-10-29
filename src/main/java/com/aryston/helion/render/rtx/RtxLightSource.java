package com.aryston.helion.render.rtx;

import java.util.List;

public interface RtxLightSource {
    int MAX_LIGHTS = 16;

    List<RtxDynamicLight> lights(double cameraX, double cameraY, double cameraZ);

    float ambientShare();
}
