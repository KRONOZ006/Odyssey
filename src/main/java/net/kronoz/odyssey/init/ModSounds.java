package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent DESOLATION_AMBIENT = registerSoundEvent("desolation_ambient");
    public static final SoundEvent SENTRY_STEP = registerSoundEvent("sentry_step");
    public static final SoundEvent ENERGY_SHIELD_BREAK = registerSoundEvent("energy_shield_break");
    public static final SoundEvent ENERGY_SHIELD_HIT = registerSoundEvent("energy_shield_hit");
    public static final SoundEvent DASH_1 = registerSoundEvent("dash_1");
    public static final SoundEvent DASH_2 = registerSoundEvent("dash_2");
    public static final SoundEvent DASH_3 = registerSoundEvent("dash_3");
    public static final SoundEvent WALLRUN_LOOP = registerSoundEvent("wallrun_loop");
    public static final SoundEvent ARC_SHOOT = registerSoundEvent("arc_shoot");



    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(Odyssey.MODID,name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void registerSounds() {
        Odyssey.LOGGER.info("Registering ModSounds for " + Odyssey.MODID);

    }
}
