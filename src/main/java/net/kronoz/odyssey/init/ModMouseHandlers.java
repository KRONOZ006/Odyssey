package net.kronoz.odyssey.init;



import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.kronoz.odyssey.entity.thrasher.ThrasherEntity;
import net.kronoz.odyssey.net.SliceAttackC2SPayload;
import net.minecraft.util.ActionResult;

public final class ModMouseHandlers {

    public static void init() {
        // When the player left-clicks an entity
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player.getVehicle() instanceof ThrasherEntity) {
                // Riding a Thrasher: send slice packet
                ModNetworking.send(new SliceAttackC2SPayload(1,1,1,1));
                return ActionResult.FAIL; // prevents vanilla attack
            }
            return ActionResult.PASS; // let normal attack happen if not riding
        });

        // When the player left-clicks a block
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && player.getVehicle() instanceof ThrasherEntity) {
                // Riding a Thrasher: send slice packet
                ModNetworking.send(new SliceAttackC2SPayload(1,1,1,1));
                return ActionResult.FAIL; // prevents block break/interact

            }
            return ActionResult.PASS; // normal behavior if not riding
        });
    }
}
