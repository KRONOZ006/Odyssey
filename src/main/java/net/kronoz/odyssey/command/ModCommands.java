package net.kronoz.odyssey.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.kronoz.odyssey.cca.ModComponents;
import net.kronoz.odyssey.data.BodyPartRegistry;
import net.kronoz.odyssey.presets.BodyPresets;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;

public final class ModCommands {
    public static void init() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    private static final SuggestionProvider<ServerCommandSource> PRESET_SUGGESTER = (ctx, builder) ->
            CommandSource.suggestMatching(BodyPresets.names(), builder);

    private static final SuggestionProvider<ServerCommandSource> PART_SUGGESTER = (ctx, builder) ->
            CommandSource.suggestIdentifiers(BodyPartRegistry.all().keySet(), builder);

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
                .then(CommandManager.literal("preset")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .suggests(PRESET_SUGGESTER)
                                .executes(ctx -> {
                                    var p = ctx.getSource().getPlayer();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    var map = BodyPresets.get(name);
                                    if (map == null || map.isEmpty()) return 0;
                                    var c = ModComponents.BODY.get(p);
                                    for (var e : map.entrySet()) {
                                        var def = BodyPartRegistry.get(e.getValue());
                                        if (def != null) c.setPart(e.getKey(), e.getValue());
                                    }
                                    c.sync(p);
                                    return 1;
                                })))
        );
    }
}
