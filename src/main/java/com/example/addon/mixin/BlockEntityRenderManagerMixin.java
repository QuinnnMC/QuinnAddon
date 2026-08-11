package com.example.addon.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {

    @Inject(method = "getRenderState", at = @At("RETURN"))
    private void quinnAddon$storageRenderState(
        BlockEntity blockEntity,
        float tickProgress,
        ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
        CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        BlockEntityRenderState renderState = cir.getReturnValue();

        if (renderState == null) {
            return;
        }

        // Simple implementation for now.
        // We can add the actual hiding/render manipulation here.
    }
}