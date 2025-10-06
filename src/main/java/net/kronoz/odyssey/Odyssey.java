package net.kronoz.odyssey;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
import net.kronoz.odyssey.init.*;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.kronoz.odyssey.systems.dialogue.Dialogue;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Odyssey implements ModInitializer {
    public static final String MODID = "odyssey";
    public static Identifier id(String path){ return Identifier.of(MODID, path); }
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {

        ModEntities.init();
        ModBlockEntities.register();
        ModEntities.register();
        ModInteractions.init();
        Dialogue.init();
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
        ModItemGroup.registerItemGroups();
        ModComponents.init();
        BodyPartRegistry.init();
        ModNetworking.init();
        ModCommands.init();
        ModSounds.registerSounds();
    }
}
