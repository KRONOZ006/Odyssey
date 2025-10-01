package net.kronoz.odyssey.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kronoz.odyssey.dialogue.BaseDialogue;
import net.kronoz.odyssey.dialogue.Registry;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class PlayDialogueCommand {
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(CommandManager.literal("dialogue_play")
                .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                    .executes(ctx -> {
                        var p = ctx.getSource().getPlayer();
                        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");
                        BaseDialogue d = Registry.get(id);
                        if (d == null) {
                            ctx.getSource().sendError(Text.literal("Dialogue not found: " + id));
                            return 0;
                        }
                        d.play(p);
                        ctx.getSource().sendFeedback(() -> Text.literal("Playing: " + id), false);
                        return 1;
                    })
                )
            );
        });
    }
}
