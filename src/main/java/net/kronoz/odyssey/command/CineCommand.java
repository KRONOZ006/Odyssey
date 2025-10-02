package net.kronoz.odyssey.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

/**
 * Enregistre les commandes via le callback Fabric (pas de dispatcher global au boot).
 * - /cine demo
 * - /cine play <id>
 * - /cine speed <mult>
 * - /cine stop
 */
public final class CineCommand {

    private CineCommand(){}

    public static void register(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(
                    literal("cine")
                            .then(literal("play").then(argument("id", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        boolean ok = CutsceneManager.I.play(id);
                                        ctx.getSource().sendFeedback(Text.of(ok ? "Playing " + id : "Unknown " + id));
                                        return 1;
                                    })))
                            .then(literal("stop").executes(ctx -> {
                                CutsceneManager.I.stop();
                                ctx.getSource().sendFeedback(Text.of("Stopped"));
                                return 1;
                            }))
                            .then(literal("demo").executes(ctx -> {
                                boolean ok = CutsceneManager.I.play("intro");
                                ctx.getSource().sendFeedback(Text.of(ok ? "Playing intro" : "Intro not found"));
                                return 1;
                            }))
                            .then(literal("speed").then(argument("mult", DoubleArgumentType.doubleArg(0.001, 100.0))
                                    .executes(ctx -> {
                                        double m = DoubleArgumentType.getDouble(ctx, "mult");
                                        boolean ok = CutsceneManager.I.setSpeed(m);
                                        ctx.getSource().sendFeedback(Text.of(ok ? ("Speed x" + m) : "No active cutscene"));
                                        return 1;
                                    })))
            );
            dispatcher.register(literal("cs").redirect(root)); // alias
        });
    }
}