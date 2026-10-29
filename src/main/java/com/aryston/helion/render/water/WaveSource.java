package com.aryston.helion.render.water;

import java.util.List;

public interface WaveSource {
    List<WaveImpulse> impulses(double cameraX, double cameraZ, double reach, boolean puddles);

    boolean holdsWaves(int blockX, int blockZ, int cameraBlockY);

    float rainFloor(int blockX, int blockZ);
}
