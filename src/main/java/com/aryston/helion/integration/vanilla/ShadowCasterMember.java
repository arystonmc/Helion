package com.aryston.helion.integration.vanilla;

public interface ShadowCasterMember {
    int NOT_A_CASTER = -1;

    int helion$casterIndex();

    void helion$setCasterIndex(int index);
}
