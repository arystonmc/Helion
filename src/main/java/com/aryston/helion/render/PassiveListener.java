package com.aryston.helion.render;

@FunctionalInterface
public interface PassiveListener {
    void onPassive(PassiveReason reason);
}
