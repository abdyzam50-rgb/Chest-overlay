package com.chestoverlay.mixin;

import com.chestoverlay.render.ChestBoxRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Target WorldRenderer by intermediary class name with remap=false so Loom
// does NOT add a version-specific method descriptor to the refmap.
// Mixin then matches "method_22710" by name only at runtime, which works in
// both 1.21.1 (7-param render) and 1.21.11 (10-param render) because the
// intermediary method name is the same across both versions.
@Mixin(targets = "net.minecraft.class_761", remap = false)
public class WorldRendererMixin {

    @Inject(method = "method_22710", at = @At("TAIL"), require = 0)
    private void chestoverlay_afterRender(CallbackInfo ci) {
        ChestBoxRenderer.renderDirect();
    }
}
