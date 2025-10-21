package net.kronoz.odyssey.init;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.kronoz.odyssey.hud.death.DeathUICutscene;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ModCommands {
    public static void init() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }
    private static final List<String> EASINGS = List.of(
            "linear",
            "quad_in", "quad_out",
            "cubic_inout", "smoothstep",
            "sine_in", "sine_out",
            "expo_in", "expo_out",
            "back_inout",
            "bounce_out"
    );

    private static final SuggestionProvider<?> EASING_SUGGESTIONS =
            (context, builder) -> {
                for (String e : EASINGS) builder.suggest(e);
                return builder.buildFuture();
            };

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("csrec")
                    .then(literal("origin")
                            .then(literal("set").executes(ctx -> {
                                net.kronoz.odyssey.client.cs.CutsceneRecorder.I.setOriginFromPlayer();
                                ctx.getSource().sendFeedback(Text.literal("Origin set (player position & rotation)."));
                                return 1;
                            }))
                    )
                    .then(literal("key")
                            .then(literal("add")
                                    .then(argument("duration", DoubleArgumentType.doubleArg(0.0))
                                            .executes(ctx -> {
                                                double dur = DoubleArgumentType.getDouble(ctx, "duration");
                                                net.kronoz.odyssey.client.cs.CutsceneRecorder.I.addKey(dur, "linear", "linear");
                                                ctx.getSource().sendFeedback(Text.literal("Keyframe added (duration=" + dur + "s, easePos=linear, easeRot=linear)."));
                                                return 1;
                                            })
                                            .then(argument("easePos", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        double dur = DoubleArgumentType.getDouble(ctx, "duration");
                                                        String ep = StringArgumentType.getString(ctx, "easePos");
                                                        net.kronoz.odyssey.client.cs.CutsceneRecorder.I.addKey(dur, ep, "linear");
                                                        ctx.getSource().sendFeedback(Text.literal("Keyframe added (duration=" + dur + "s, easePos=" + ep + ", easeRot=linear)."));
                                                        return 1;
                                                    })
                                                    .then(argument("easeRot", StringArgumentType.word())
                                                            .executes(ctx -> {
                                                                double dur = DoubleArgumentType.getDouble(ctx, "duration");
                                                                String ep = StringArgumentType.getString(ctx, "easePos");
                                                                String er = StringArgumentType.getString(ctx, "easeRot");
                                                                net.kronoz.odyssey.client.cs.CutsceneRecorder.I.addKey(dur, ep, er);
                                                                ctx.getSource().sendFeedback(Text.literal("Keyframe added (duration=" + dur + "s, easePos=" + ep + ", easeRot=" + er + ")."));
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(literal("list").executes(ctx -> {
                        var list = net.kronoz.odyssey.client.cs.CutsceneRecorder.I.list();
                        if (list.isEmpty()) {
                            ctx.getSource().sendFeedback(Text.literal("No keyframes."));
                        } else {
                            StringBuilder sb = new StringBuilder("Keyframes:\n");
                            for (int i = 0; i < list.size(); i++) {
                                var k = list.get(i);
                                sb.append("#").append(i)
                                        .append(" dur=").append(k.duration)
                                        .append(" pos=(").append(String.format("%.3f,%.3f,%.3f", k.rx,k.ry,k.rz)).append(")")
                                        .append(" rot=(").append(String.format("%.1f,%.1f", k.yaw,k.pitch)).append(")")
                                        .append(" easePos=").append(k.easePos)
                                        .append(" easeRot=").append(k.easeRot)
                                        .append("\n");
                            }
                            ctx.getSource().sendFeedback(Text.literal(sb.toString()));
                        }
                        return 1;
                    }))
                    .then(literal("clear").executes(ctx -> {
                        net.kronoz.odyssey.client.cs.CutsceneRecorder.I.clear();
                        ctx.getSource().sendFeedback(Text.literal("Recorder cleared."));
                        return 1;
                    }))
                    .then(literal("export")
                            .then(argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        try {
                                            var path = net.kronoz.odyssey.client.cs.CutsceneRecorder.I.exportJson(name);
                                            ctx.getSource().sendFeedback(Text.literal("Exported to: " + path));
                                            return 1;
                                        } catch (Exception e) {
                                            ctx.getSource().sendError(Text.literal("Export failed: " + e.getMessage()));
                                            return 0;
                                        }
                                    })
                            )
                    )
                    .then(literal("play").executes(ctx -> {
                        net.kronoz.odyssey.client.cs.CutsceneRecorder.I.startPreview();
                        ctx.getSource().sendFeedback(Text.literal("Preview started."));
                        return 1;
                    }))
            );
        });
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
