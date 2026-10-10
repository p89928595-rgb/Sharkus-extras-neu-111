package sharkus.extras.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sharkus.extras.HitColor;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {

    @Shadow
    @Final
    private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sharkus$init(CallbackInfo ci) {
        HitColor.attach(this.texture);
    }
}
