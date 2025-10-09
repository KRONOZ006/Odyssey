package net.kronoz.odyssey;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.block.SequencerRegistry;
import net.kronoz.odyssey.init.*;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.kronoz.odyssey.systems.dialogue.Dialogue;
import net.kronoz.odyssey.systems.grapple.GrappleNetworking;
import net.kronoz.odyssey.systems.grapple.GrapplePayloads;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

public class Odyssey implements ModInitializer {
    public static final String MODID = "odyssey";
    public static Identifier id(String path){ return Identifier.of(MODID, path); }
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        SequencerRegistry.init();
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


        GrapplePayloads.registerPayloads();
        new GrappleNetworking().registerServer();
    }
}