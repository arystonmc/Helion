package com.aryston.helion.render.geometry;

public record GeometryBufferSettings(boolean enabled, GeometryBufferView view) {
    public static final GeometryBufferSettings DISABLED = new GeometryBufferSettings(false, GeometryBufferView.NONE);

    public GeometryBufferSettings withoutView() {
        return new GeometryBufferSettings(enabled, GeometryBufferView.NONE);
    }

    public boolean showsView() {
        return enabled && view != GeometryBufferView.NONE;
    }
}
