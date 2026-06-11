package com.chestoverlay.mixin;

import com.chestoverlay.render.ChestBoxRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void chestoverlay_afterRender(CallbackInfo ci) {
        ChestBoxRenderer.renderDirect();
    }
}
