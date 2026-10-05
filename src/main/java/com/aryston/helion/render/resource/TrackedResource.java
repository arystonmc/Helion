package com.aryston.helion.render.resource;

public record TrackedResource(String label, long bytes, Runnable releaseAction) {
}
