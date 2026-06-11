package com.chestoverlay.mixin;

import com.chestoverlay.render.ChestBoxRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void chestoverlay_afterRender(CallbackInfo ci) {
        ChestBoxRenderer.renderDirect();
    }
}
