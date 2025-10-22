package net.kronoz.odyssey.entity.thrasher;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kronoz.odyssey.net.RollAttackC2SPayload;
import net.kronoz.odyssey.net.SliceAttackC2SPayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class RollAttackHandler {

    // Tracks heat per player
    private static final Map<UUID, PlayerHeat> PLAYER_HEAT = new HashMap<>();
    private static final List<SliceWave> ACTIVE_WAVES = new ArrayList<>();

    // Heat parameters
    private static final float BASE_INCREMENT = 1f;      // Heat added per slice at cold
    private static final float SCALING_FACTOR = 0.3f;    // Additional heat when already hot
    private static final float DECAY_RATE = 0.05f;       // Heat decay per tick
    private static final float OVERHEAT_THRESHOLD_BASE = 8f; // Base threshold
    private static final float THRESHOLD_RECOVERY = 0.01f;  // Threshold recovery per tick
    private static final float MIN_RESIDUAL_HEAT = 1f;      // Heat floor to prevent instant reset

    private static class PlayerHeat {
        float currentHeat = 0f;
        float overheatThreshold = OVERHEAT_THRESHOLD_BASE;
        boolean overheated = false;
        long lastClickTick = 0;
    }

    public static void onRollPacket(ServerPlayerEntity player, RollAttackC2SPayload payload) {
        if (player.getVehicle() == null) return; // must be riding

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d direction = player.getRotationVec(1.0f);

        tryStartRoll(world, player, direction);
    }

    private static void tryStartRoll(ServerWorld world, PlayerEntity player, Vec3d direction) {
        Entity ridden = player.getVehicle();
        if (ridden instanceof ThrasherEntity thrasher) {
            thrasher.triggerRollAnimation();
            thrasher.syncAnimationToClients();

            player.sendMessage(Text.literal("worked"));
            if (thrasher.isRolling()) {
                if (thrasher.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
                    thrasher.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                            .setBaseValue(6); // faster
                }
            }
            else if (thrasher.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
                thrasher.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                        .setBaseValue(0.3); // back to normal
            }

        }

    }
}
