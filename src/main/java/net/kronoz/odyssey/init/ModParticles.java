package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.kronoz.odyssey.Odyssey;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType SENTRY_SHIELD_FULL_PARTICLE =
            registerParticle("sentry_shield_full", FabricParticleTypes.simple(true));

    private static SimpleParticleType registerParticle(String name, SimpleParticleType particleType) {
        return Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Odyssey.MODID, name), particleType);
    }

    public static void registerParticles() {
        Odyssey.LOGGER.info("Registering Particles for " + Odyssey.MODID);
    }
}
