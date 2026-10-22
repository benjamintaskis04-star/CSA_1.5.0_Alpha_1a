package com.benjamintaskis.createsignalextensions.mixin;

import com.benjamintaskis.createsignalextensions.client.SignalPlateDirectEditor;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void csa$capturePlateKeys(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (SignalPlateDirectEditor.keyPressed(key, action, modifiers)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void csa$capturePlateCharacters(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (SignalPlateDirectEditor.charTyped(codePoint, modifiers)) ci.cancel();
    }
}
