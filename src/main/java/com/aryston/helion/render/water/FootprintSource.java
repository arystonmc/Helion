package com.aryston.helion.render.water;

import java.util.List;

public interface FootprintSource {
    List<Footprint> steps(double cameraX, double cameraZ, double seconds);
}
