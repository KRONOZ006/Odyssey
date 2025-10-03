package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent DESOLATION_AMBIENT = registerSoundEvent("desolation_ambient");


    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(Odyssey.MODID,name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void registerSounds() {
        Odyssey.LOGGER.info("Registering ModSounds for " + Odyssey.MODID);

    }
}
