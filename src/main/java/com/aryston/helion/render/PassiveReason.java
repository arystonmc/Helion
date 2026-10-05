package com.aryston.helion.render;

public enum PassiveReason {
    NOT_VULKAN("helion.passive.not_vulkan"),
    INCOMPATIBLE_MOD("helion.passive.incompatible_mod"),
    RENDER_FAILURE("helion.passive.render_failure");

    private final String translationKey;

    PassiveReason(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
