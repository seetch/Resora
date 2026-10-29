package dev.resora.mixin;

import dev.resora.screen.ResoraScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private boolean resora$keyWasDown;

    @Inject(method = "tick", at = @At("TAIL"))
    private void resora$openMenu(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        boolean keyDown = InputConstants.isKeyDown(InputConstants.KEY_I);
        if (keyDown && !resora$keyWasDown && client.gui.screen() == null) {
            client.setScreenAndShow(new ResoraScreen(null));
        }
        resora$keyWasDown = keyDown;
    }
}
