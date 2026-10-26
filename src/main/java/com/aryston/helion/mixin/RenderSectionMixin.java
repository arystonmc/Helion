package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.RtxSectionCapture;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionMixin {
    @Inject(
        method = "reset()V",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicReference;getAndSet(Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER)
    )
    private void helion$meshReset(CallbackInfo callback) {
        SectionRenderDispatcher.RenderSection section = (SectionRenderDispatcher.RenderSection) (Object) this;
        RtxSectionCapture.removed(section.getSectionNode());
    }
}
