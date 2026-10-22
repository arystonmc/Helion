package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelDynamicScene;
import com.aryston.helion.render.voxel.VoxelOccluder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class VanillaVoxelOccluders {
    private static final double MAX_DISTANCE = 24.0;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

    private final List<Candidate> candidates = new ArrayList<>();

    List<VoxelOccluder> collect(ClientLevel level, Vec3 camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity hidden = minecraft.options.getCameraType().isFirstPerson() ? minecraft.getCameraEntity() : null;
        candidates.clear();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == hidden || entity.isInvisible() || entity.isSpectator()) {
                continue;
            }
            Vec3 position = entity.getPosition(partialTick);
            double distanceSquared = position.distanceToSqr(camera);
            if (distanceSquared <= MAX_DISTANCE_SQUARED) {
                candidates.add(new Candidate(entity.getBoundingBox().move(position.subtract(entity.position())), distanceSquared));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
        List<VoxelOccluder> occluders = new ArrayList<>(Math.min(candidates.size(), VoxelDynamicScene.MAX_OCCLUDERS));
        for (Candidate candidate : candidates) {
            if (occluders.size() == VoxelDynamicScene.MAX_OCCLUDERS) {
                break;
            }
            AABB box = candidate.box();
            occluders.add(new VoxelOccluder(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return occluders;
    }

    private record Candidate(AABB box, double distanceSquared) {
    }
}
