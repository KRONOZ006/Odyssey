package net.kronoz.odyssey.cca;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.entity.player.PlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

public final class ModComponents implements EntityComponentInitializer {
    public static final ComponentKey<BodyModComponent> BODY = ComponentRegistry.getOrCreate(
            Odyssey.id("body"), BodyModComponent.class);

    public static final ComponentKey<DialogueComponent> DIALOGUE = ComponentRegistry.getOrCreate(
            Odyssey.id("dialogue"), DialogueComponent.class);

    public static void init() {}

    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(PlayerEntity.class, BODY, BodyModComponentImpl::new);
        registry.registerFor(PlayerEntity.class, DIALOGUE, DialogueComponentImpl::new);
    }
}
