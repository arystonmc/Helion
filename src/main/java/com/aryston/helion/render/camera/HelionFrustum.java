package com.aryston.helion.render.camera;

import java.util.List;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class HelionFrustum {
    private final List<Vector4fc> planes;
    private final Vector3dc origin;

    private HelionFrustum(List<Vector4fc> planes, Vector3dc origin) {
        this.planes = planes;
        this.origin = origin;
    }

    public static HelionFrustum of(HelionCamera camera) {
        Matrix4fc matrix = camera.viewProjection();
        Vector4f x = row(matrix, 0);
        Vector4f y = row(matrix, 1);
        Vector4f z = row(matrix, 2);
        Vector4f w = row(matrix, 3);
        Vector4f lowerDepthBound = camera.zeroToOneDepth() ? new Vector4f(z) : new Vector4f(w).add(z);
        List<Vector4fc> planes = List.of(
            new Vector4f(w).add(x),
            new Vector4f(w).sub(x),
            new Vector4f(w).add(y),
            new Vector4f(w).sub(y),
            new Vector4f(w).sub(z),
            lowerDepthBound
        );
        return new HelionFrustum(planes, camera.position());
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float relativeMinX = (float) (minX - origin.x());
        float relativeMinY = (float) (minY - origin.y());
        float relativeMinZ = (float) (minZ - origin.z());
        float relativeMaxX = (float) (maxX - origin.x());
        float relativeMaxY = (float) (maxY - origin.y());
        float relativeMaxZ = (float) (maxZ - origin.z());
        for (Vector4fc plane : planes) {
            float farthestX = plane.x() >= 0.0F ? relativeMaxX : relativeMinX;
            float farthestY = plane.y() >= 0.0F ? relativeMaxY : relativeMinY;
            float farthestZ = plane.z() >= 0.0F ? relativeMaxZ : relativeMinZ;
            if (plane.x() * farthestX + plane.y() * farthestY + plane.z() * farthestZ + plane.w() < 0.0F) {
                return false;
            }
        }
        return true;
    }

    private static Vector4f row(Matrix4fc matrix, int index) {
        return matrix.getRow(index, new Vector4f());
    }
}
