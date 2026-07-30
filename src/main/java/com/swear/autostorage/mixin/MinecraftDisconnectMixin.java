package com.swear.autostorage.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftDisconnectMixin {
    @Shadow
    @Final
    private Window window;

    @Inject(
            method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At("HEAD"))
    private void autoStorage$leaveFullscreenBeforeDisconnect(
            Screen nextScreen,
            boolean keepResourcePacks,
            CallbackInfo callback
    ) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (Minecraft.ON_OSX && minecraft.level != null && window.isFullscreen()) {
            window.toggleFullScreen();
        }
    }
}
