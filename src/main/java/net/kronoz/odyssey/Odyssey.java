package net.kronoz.odyssey;

import net.fabricmc.api.ModInitializer;
import net.kronoz.odyssey.init.ModBlocks;
import net.kronoz.odyssey.init.ModComponents;
import net.kronoz.odyssey.init.ModCommands;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.kronoz.odyssey.systems.dialogue.Dialogue;
import net.kronoz.odyssey.init.ModBlockEntities;
import net.kronoz.odyssey.init.ModEntities;
import net.kronoz.odyssey.init.ModInteractions;
import net.kronoz.odyssey.init.ModItemGroup;
import net.kronoz.odyssey.init.ModItems;
import net.kronoz.odyssey.init.ModNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Odyssey implements ModInitializer {
    public static final String MODID = "odyssey";
    public static Identifier id(String path){ return Identifier.of(MODID, path); }
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
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
    }
}
