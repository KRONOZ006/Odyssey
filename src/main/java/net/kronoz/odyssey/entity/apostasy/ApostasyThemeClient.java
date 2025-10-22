package net.kronoz.odyssey.entity.apostasy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ApostasyThemeClient {

    private static final Map<Integer, Loop> ACTIVE = new HashMap<>();
    private static SoundEvent THEME;

    public static void init(SoundEvent themeEvent) {
        THEME = themeEvent;

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null || mc.player == null || THEME == null) {
                stopAll();
                return;
            }

            for (Entity e : mc.world.getEntities()) {
                if (!isApostasy(e) || !e.isAlive()) continue;

                Loop loop = ACTIVE.get(e.getId());
                if (loop == null || loop.isDone()) {
                    loop = new Loop(THEME, e.getId());
                    ACTIVE.put(e.getId(), loop);
                    mc.getSoundManager().play(loop);
                } else {
                    loop.ensureFollowingEntity(e.getId());
                }
            }

            for (Iterator<Map.Entry<Integer, Loop>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Loop> entry = it.next();
                int id = entry.getKey();
                Loop loop = entry.getValue();
                Entity e = mc.world.getEntityById(id);
                if (e == null || !e.isAlive()) {
                    loop.requestStop();
                    it.remove();
                }
            }
        });
    }

    private static boolean isApostasy(Entity e) {
        return e.getClass().getSimpleName().equals("ApostasyEntity")
                || e.getType().toString().toLowerCase().contains("apostasy");
    }

    private static void stopAll() {
        for (Loop s : ACTIVE.values()) s.requestStop();
        ACTIVE.clear();
    }

    // inside ApostasyThemeClient.java

    private static final class Loop implements TickableSoundInstance {
        private final SoundEvent event;
        private final SoundCategory category = SoundCategory.MUSIC; // change if you want
        private int entityId;

        private boolean done = false;
        private boolean repeat = true;
        private int repeatDelay = 0;
        private boolean relative = false;

        private float volume = 1.0f;
        private float pitch = 1.0f;
        private double x = 0, y = 0, z = 0;
        private AttenuationType attenuation = AttenuationType.LINEAR;

        Loop(SoundEvent event, int entityId) {
            this.event = event;
            this.entityId = entityId;
        }

        void requestStop() { this.done = true; }
        void ensureFollowingEntity(int id) { this.entityId = id; }

        // ---------- TickableSoundInstance ----------
        @Override
        public void tick() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) { this.done = true; return; }

            Entity e = mc.world.getEntityById(this.entityId);
            if (e == null || !e.isAlive()) { this.done = true; return; }

            // follow
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();

            // distance-based loudness (audible up to ~64 blocks)
            double maxDist = 64.0;
            double t = 1.0 - Math.min(1.0, e.distanceTo(mc.player) / maxDist);
            float vol = (float)(0.25 + 0.75 * t);
            this.volume = MathHelper.clamp(vol, 0f, 1f);
        }

        @Override public boolean isDone() { return this.done; }

        // ---------- SoundInstance ----------
        @Override public Identifier getId() { return event.getId(); }

        @Override
        public @Nullable net.minecraft.client.sound.WeightedSoundSet getSoundSet(net.minecraft.client.sound.SoundManager sm) {
            // Resolve the sound set from the registry; if missing, stop the loop to avoid NPEs
            var set = sm.get(this.getId());
            if (set == null) this.done = true;
            return set;
        }

        @Override
        public @Nullable net.minecraft.client.sound.Sound getSound() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return null;

            SoundManager sm = mc.getSoundManager();
            net.minecraft.client.sound.WeightedSoundSet set = sm.get(this.getId());
            if (set == null) { this.done = true; return null; }

            return set.getSound(net.minecraft.util.math.random.Random.create());
        }


        @Override public SoundCategory getCategory() { return category; }
        @Override public boolean isRepeatable() { return repeat; }
        @Override public boolean isRelative() { return relative; }
        @Override public int getRepeatDelay() { return repeatDelay; }
        @Override public float getVolume() { return volume; }
        @Override public float getPitch() { return pitch; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }
        @Override public AttenuationType getAttenuationType() { return attenuation; }
    }


    private ApostasyThemeClient() {}
}
