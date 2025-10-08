package net.kronoz.odyssey.init;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kronoz.odyssey.hud.death.DeathUICutscene;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;

public final class ModCommands {
    public static void init() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("bodymod")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("slot", StringArgumentType.string())
                                .then(CommandManager.argument("id", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                        .suggests((c,b)-> CommandSource.suggestIdentifiers(BodyPartRegistry.all().keySet(), b))
                                        .executes(ctx -> {
                                            var p = ctx.getSource().getPlayer();
                                            String slot = StringArgumentType.getString(ctx, "slot");
                                            Identifier id = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "id");
                                            var def = BodyPartRegistry.get(id);
                                            if (def == null) return 0;
                                            ModComponents.BODY.get(p).setPart(slot, id);
                                            ModComponents.BODY.get(p).sync(p);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("slot", StringArgumentType.string())
                                .executes(ctx -> {
                                    var p = ctx.getSource().getPlayer();
                                    String slot = StringArgumentType.getString(ctx, "slot");
                                    ModComponents.BODY.get(p).clearSlot(slot);
                                    ModComponents.BODY.get(p).sync(p);
                                    return 1;
                                })))
        );
        d.register(CommandManager.literal("deathui")
              .executes(ctx -> {
                      DeathUICutscene.start();
                      return 1;
             })
        );
    }
}
