package com.aryston.helion.render.clouds;

public enum CloudQuality {
    LOW(4, 24, 3, false),
    MEDIUM(3, 40, 4, false),
    HIGH(2, 56, 6, true);

    private final int resolutionDivisor;
    private final int primarySteps;
    private final int lightSteps;
    private final boolean cirrus;

    CloudQuality(int resolutionDivisor, int primarySteps, int lightSteps, boolean cirrus) {
        this.resolutionDivisor = resolutionDivisor;
        this.primarySteps = primarySteps;
        this.lightSteps = lightSteps;
        this.cirrus = cirrus;
    }

    public int resolutionDivisor() {
        return resolutionDivisor;
    }

    public int primarySteps() {
        return primarySteps;
    }

    public int lightSteps() {
        return lightSteps;
    }

    public boolean cirrus() {
        return cirrus;
    }
}
