package sharkus.extras.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sharkus.extras.Names;

@Mixin(ClientLanguage.class)
public abstract class LanguageMixin {

    @Inject(method = "getVisualOrder", at = @At("HEAD"), cancellable = true)
    private void sharkus$names(FormattedText text, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (Names.hookCalls < Integer.MAX_VALUE) Names.hookCalls++;
        if (Names.busy()) return;
        FormattedText replaced = Names.apply(text);
        if (replaced != text) {
            Names.setBusy(true);
            try {
                cir.setReturnValue(((ClientLanguage) (Object) this).getVisualOrder(replaced));
            } finally {
                Names.setBusy(false);
            }
        }
    }
}
