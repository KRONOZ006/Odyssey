package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item TOMAHAWK = registerItem("tomahawk", new TomahawkItem(new Item.Settings().maxCount(1).fireproof()));
    public static final Item XARIS = registerItem("xaris", new XarisArm(new Item.Settings().maxCount(1).fireproof()));
    public static final Item JETPACK = registerItem("jetpack", new JetpackTorso(new Item.Settings().maxCount(1).fireproof()));
    public static final Item WIRE_TOOL = Registry.register(Registries.ITEM, Identifier.of("odyssey","wire_tool"), new WireToolItem(new Item.Settings().maxCount(1)));
    public static final Item SOUP1 = Registry.register(Registries.ITEM, Identifier.of("odyssey","soup1"), new CannedFoodItem(new Item.Settings().maxCount(8)));
    public static final Item WIRE_CUTTER_TOOL = Registry.register(Registries.ITEM, Identifier.of("odyssey","wire_cutter_tool"), new WireCutterItem(new Item.Settings().maxCount(1)));
    public static final Item SPEAR_DASH = Registry.register(Registries.ITEM, Identifier.of("odyssey", "spear"), new SpearDashItem
            ( ToolMaterials.NETHERITE, 9, 1f, new Item.Settings().maxCount(1).maxDamage(1).fireproof().attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -3.5f))));
    public static final Item STASISPOD_ITEM = Registry.register(Registries.ITEM, Identifier.of("odyssey","stasispod"), new BlockItem(ModBlocks.STASISPOD, new Item.Settings()));
    public static final Item RAILING_ITEM = Registry.register(Registries.ITEM, Identifier.of("odyssey","railing"), new BlockItem(ModBlocks.RAILING, new Item.Settings()));
    public static final Item TERMINAL_ITEM = Registry.register(Registries.ITEM, Identifier.of("odyssey","terminal"), new BlockItem(ModBlocks.TERMINAL, new Item.Settings()));
    public static final Item DVM_ITEM = Registry.register(Registries.ITEM, Identifier.of("odyssey","dvm"), new BlockItem(ModBlocks.DVM, new Item.Settings()));


    public static final Item APOSTASY_SPAWN_EGG = new SpawnEggItem(
            ModEntities.APOSTASY,          // Your entity type
            0xFFD700,                      // Primary color (gold)
            0xFF4500,                      // Secondary color (orange-red)
            new Item.Settings()
    );

    public static final Item SENTRY_SPAWN_EGG = new SpawnEggItem(
            ModEntities.SENTRY,        // Your custom entity type
            0xFFD700,                    // Primary color (gold)
            0xFF4500,                    // Secondary color (orange-red)
            new Item.Settings() // Creative tab
    );
    public static final Item SENTINEL_SPAWN_EGG = new SpawnEggItem(
            ModEntities.SENTINEL,        // Your custom entity type
            0xFFD700,                    // Primary color (gold)
            0xFF4500,                    // Secondary color (orange-red)
            new Item.Settings() // Creative tab
    );


    public static void registerSpawnEggs() {
        Registry.register(Registries.ITEM, Identifier.of("odyssey", "apostasy_spawn_egg"), APOSTASY_SPAWN_EGG);
        Registry.register(Registries.ITEM, Identifier.of("odyssey", "sentry_spawn_egg"), SENTRY_SPAWN_EGG);
        Registry.register(Registries.ITEM, Identifier.of("odyssey", "sentinel_spawn_egg"), SENTINEL_SPAWN_EGG);
    }

    private static Item registerItem(String name, Item item) {return Registry.register(Registries.ITEM, Identifier.of(Odyssey.MODID, name), item);}
    public static void registerModItems(){
        Odyssey.LOGGER.info("register items for" + Odyssey.MODID);
    }
}
