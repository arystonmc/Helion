package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.ShadowCasterChanges;
import com.aryston.helion.integration.vanilla.ShadowCasterMember;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionMixin implements ShadowCasterMember {
    @Unique
    private int helion$casterIndex = NOT_A_CASTER;

    @Override
    public int helion$casterIndex() {
        return helion$casterIndex;
    }

    @Override
    public void helion$setCasterIndex(int index) {
        helion$casterIndex = index;
    }

    @Inject(
        method = "setSectionMesh(Lnet/minecraft/client/renderer/chunk/SectionMesh;)Lnet/minecraft/client/renderer/chunk/SectionMesh;",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicReference;getAndSet(Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER)
    )
    private void helion$meshReplaced(CallbackInfoReturnable<?> callback) {
        ShadowCasterChanges.meshChanged((SectionRenderDispatcher.RenderSection) (Object) this);
    }

    @Inject(
        method = "reset()V",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicReference;getAndSet(Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER)
    )
    private void helion$meshReset(CallbackInfo callback) {
        ShadowCasterChanges.meshChanged((SectionRenderDispatcher.RenderSection) (Object) this);
    }
}
