package net.kronoz.odyssey.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kronoz.odyssey.cca.DialogueComponent;
import net.kronoz.odyssey.cca.ModComponents;
import net.kronoz.odyssey.dialogue.server.DialogueManager;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class DialogueCommand {
    public static void init(){
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(
                    CommandManager.literal("odialogue")
                            // Shorthand: /odialogue <tree_id>  → set preset + play now
                            .then(CommandManager.argument("tree", IdentifierArgumentType.identifier())
                                    .executes(ctx -> {
                                        var p = ctx.getSource().getPlayer();
                                        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "tree");
                                        DialogueComponent comp = ModComponents.DIALOGUE.get(p);
                                        comp.setPreset(id);
                                        DialogueManager.start(p, id);
                                        ctx.getSource().sendFeedback(() -> Text.literal("Preset set to " + id + " and started."), false);
                                        return 1;
                                    })
                            )
                            // Explicit: /odialogue set <tree_id>
                            .then(CommandManager.literal("set")
                                    .then(CommandManager.argument("tree", IdentifierArgumentType.identifier())
                                            .executes(ctx -> {
                                                var p = ctx.getSource().getPlayer();
                                                Identifier id = IdentifierArgumentType.getIdentifier(ctx, "tree");
                                                ModComponents.DIALOGUE.get(p).setPreset(id);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Preset set to " + id), false);
                                                return 1;
                                            })
                                    )
                            )
                            // /odialogue play  → plays preset
                            .then(CommandManager.literal("play")
                                    .executes(DialogueCommand::playPreset)
                            )
                            // /odialogue preset  → show current preset
                            .then(CommandManager.literal("preset")
                                    .executes(ctx -> {
                                        var p = ctx.getSource().getPlayer();
                                        Identifier id = ModComponents.DIALOGUE.get(p).presetTree();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Current preset: " + (id==null?"<none>":id.toString())), false);
                                        return 1;
                                    })
                            )
                            // /odialogue  → if preset exists, play it
                            .executes(DialogueCommand::playPreset)
            );
        });
    }

    private static int playPreset(CommandContext<ServerCommandSource> ctx){
        var p = ctx.getSource().getPlayer();
        var comp = ModComponents.DIALOGUE.get(p);
        Identifier id = comp.presetTree();
        if (id == null){
            ctx.getSource().sendError(Text.literal("No preset set. Use /odialogue set <namespace:id> or /odialogue <namespace:id>."));
            return 0;
        }
        DialogueManager.start(p, id);
        ctx.getSource().sendFeedback(() -> Text.literal("Started preset " + id), false);
        return 1;
    }
}
