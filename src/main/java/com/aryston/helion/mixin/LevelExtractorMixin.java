package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.VoxelSectionChanges;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @Inject(method = "blockChanged(Lnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"))
    private void helion$blockChanged(BlockPos pos, int updateFlags, CallbackInfo callback) {
        VoxelSectionChanges.sectionChanged(SectionPos.asLong(pos));
    }
}
