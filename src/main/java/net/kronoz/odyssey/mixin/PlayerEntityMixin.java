// src/main/java/net/kronoz/odyssey/mixin/PlayerEntityMixin.java
package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.movement.WallRun;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Unique private final WallRun.WallState odyssey$wall = new WallRun.WallState();

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void odyssey$wallRunTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient) return;
        WallRun.tick(self, odyssey$wall);
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void odyssey$wallRunJumpBoost(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient) return;
        WallRun.onJump(self, odyssey$wall);
    }
}
