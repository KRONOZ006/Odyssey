package net.kronoz.odyssey.entity.thrasher;

import net.kronoz.odyssey.net.SliceAttackC2SPayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.*;

public final class SliceAttackHandler {

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

    public static void onSlicePacket(ServerPlayerEntity player, SliceAttackC2SPayload payload) {
        if (player.getVehicle() == null) return; // must be riding

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d direction = player.getRotationVec(1.0f);

        tryStartSlice(world, player, direction);
    }

    private static void tryStartSlice(ServerWorld world, PlayerEntity player, Vec3d direction) {
        UUID id = player.getUuid();
        long now = world.getTime();

        PlayerHeat heat = PLAYER_HEAT.computeIfAbsent(id, u -> new PlayerHeat());

        // Prevent actions if overheated
        if (heat.overheated) {
            player.sendMessage(Text.literal("§cOverheated! Wait for cooldown."), true);
            return;
        }

        // Increase heat with scaling
        float increment = BASE_INCREMENT * (1f + (heat.currentHeat / heat.overheatThreshold) * SCALING_FACTOR);
        heat.currentHeat += increment;
        heat.lastClickTick = now;

        // Check for overheat
        if (heat.currentHeat >= heat.overheatThreshold) {
            heat.overheated = true;
            heat.currentHeat = heat.overheatThreshold; // freeze at max
            player.sendMessage(Text.literal("§cOVERHEATED!"), true);
            return;
        }

        // Trigger slice normally
        SliceWave wave = SliceWave.start(world, player, direction);
        ACTIVE_WAVES.add(wave);

        Entity ridden = player.getVehicle();
        if (ridden instanceof ThrasherEntity thrasher) {
            thrasher.triggerSliceAnimation();
            thrasher.syncAnimationToClients();
        }

        int sliceNumber = Math.round(heat.currentHeat);
        player.sendMessage(Text.literal("§bSlice unleashed! Heat: " + sliceNumber + "/" + Math.round(heat.overheatThreshold)), true);
    }

    public static void registerTickHandler() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerWorld sw)) return;

            // Tick slice waves
            for (Iterator<SliceWave> it = ACTIVE_WAVES.iterator(); it.hasNext();) {
                SliceWave wave = it.next();
                if (!wave.tick()) it.remove();
            }

            // Update heat for each player
            for (PlayerHeat heat : PLAYER_HEAT.values()) {
                if (heat.overheated) {
                    // Faster decay when overheated
                    heat.currentHeat -= DECAY_RATE * 2f;
                    if (heat.currentHeat <= MIN_RESIDUAL_HEAT) {
                        heat.overheated = false;
                        heat.currentHeat = MIN_RESIDUAL_HEAT; // retain some residual heat
                        // slowly recover threshold
                        heat.overheatThreshold = Math.min(OVERHEAT_THRESHOLD_BASE, heat.overheatThreshold + THRESHOLD_RECOVERY);
                    }
                } else {
                    // Smooth decay towards residual heat
                    heat.currentHeat -= DECAY_RATE;
                    if (heat.currentHeat < MIN_RESIDUAL_HEAT) heat.currentHeat = MIN_RESIDUAL_HEAT;

                    // slowly recover threshold
                    heat.overheatThreshold = Math.min(OVERHEAT_THRESHOLD_BASE, heat.overheatThreshold + THRESHOLD_RECOVERY);
                }
            }
        });
    }
}
