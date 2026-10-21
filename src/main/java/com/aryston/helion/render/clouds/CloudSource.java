package com.aryston.helion.render.clouds;

import java.util.Optional;

public interface CloudSource {
    Optional<CloudEnvironment> environment();

    void replaceVanillaClouds(boolean replace);
}
