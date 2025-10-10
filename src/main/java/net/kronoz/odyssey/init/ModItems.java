package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.*;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item TOMAHAWK = registerItem("tomahawk", new TomahawkItem(new Item.Settings().maxCount(1).fireproof()));
    public static final Item XARIS = registerItem("xaris", new XarisArm(new Item.Settings().maxCount(1).fireproof()));
    public static final Item JETPACK = registerItem("jetpack", new JetpackTorso(new Item.Settings().maxCount(1).fireproof()));
    public static final Item WIRE_TOOL = Registry.register(Registries.ITEM,
            Identifier.of("odyssey","wire_tool"),
            new WireToolItem(new Item.Settings().maxCount(1)));
public static final Item GRAPPLE = Registry.register(Registries.ITEM,
            Identifier.of("odyssey","grapple"),
            new LoyalGrappleItem(new Item.Settings().maxCount(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Odyssey.MODID, name), item);
    }

    public static void registerModItems(){
        Odyssey.LOGGER.info("register items for" + Odyssey.MODID);
    }
}
