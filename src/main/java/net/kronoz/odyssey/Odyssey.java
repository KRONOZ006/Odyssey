package net.kronoz.odyssey;

import net.fabricmc.api.ModInitializer;
import net.kronoz.odyssey.cca.ModComponents;
import net.kronoz.odyssey.command.ModCommands;
import net.kronoz.odyssey.data.BodyPartRegistry;
import net.kronoz.odyssey.item.ModItemGroup;
import net.kronoz.odyssey.item.ModItems;
import net.kronoz.odyssey.net.ModNetworking;
import net.kronoz.odyssey.presets.BodyPresets;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Odyssey implements ModInitializer {
    public static final String MODID = "odyssey";
    public static Identifier id(String path){ return Identifier.of(MODID, path); }
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModItemGroup.registerItemGroups();
        BodyPresets.init();
        ModComponents.init();
        BodyPartRegistry.init();
        ModNetworking.init();
        ModCommands.init();
    }
}
