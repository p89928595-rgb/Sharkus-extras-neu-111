package sharkus.extras.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sharkus.extras.ItemFx;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void sharkus$overlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ItemFx.screenHook = true;
        try {
            AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
            for (Slot slot : self.getMenu().slots) {
                int sx = this.leftPos + slot.x;
                int sy = this.topPos + slot.y;
                ItemStack st = slot.getItem();
                ItemFx.highlight(g, st, sx, sy);
                if (slot.container instanceof Inventory) {
                    ItemFx.ghost(g, slot.getContainerSlot(), st, sx, sy);
                }
            }
        } catch (Throwable ignored) { }
    }
}
