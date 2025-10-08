package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroup {

    public static final ItemGroup ODYSSEY = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(Odyssey.MODID, "odyssey"),
            ItemGroup.create(null, -1)
                    .displayName(Text.translatable("itemgroup.odyssey.odyssey"))
                    .icon(() -> new ItemStack(ModItems.XARIS))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.TOMAHAWK);
                        entries.add(ModBlocks.FACILITY_PILLAR_BLOCK);
                        entries.add(ModBlocks.ELEVATOR);
                        entries.add(ModBlocks.SLIDING_DOOR);
                        entries.add(ModItems.XARIS);
                        entries.add(ModItems.WIRE_TOOL);
                        entries.add(ModItems.JETPACK);
                        entries.add(ModBlocks.LIGHT1);
                        entries.add(ModBlocks.LARGE_FACILITY_PILLAR_BLOCK);
                        entries.add(ModBlocks.PYROXENE);
                        entries.add(ModBlocks.FACILITY_REBAR_BLOCK);
                        entries.add(ModBlocks.VERDIGRIS_BLOCK);
                        entries.add(ModBlocks.VERDIGRIS_PANNEL);
                        entries.add(ModBlocks.VERDIGRIS_PLATES);
                        entries.add(ModBlocks.VERDIGRIS_SHEATHING);
                        entries.add(ModBlocks.SCARRED_VERDIGRIS_SHEATHING);
                        entries.add(ModBlocks.ALARM);


                    })
                    .build());


    public static void registerItemGroups() {
        Odyssey.LOGGER.info("Registering Item Groups for " + Odyssey.MODID);
    }
}