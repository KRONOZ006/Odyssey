package net.kronoz.odyssey.cca;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

public final class DialogueComponentImpl implements DialogueComponent, AutoSyncedComponent {
    private final PlayerEntity player;
    private Identifier tree;
    private String node;
    private boolean active;
    private Identifier preset;
    public DialogueComponentImpl(PlayerEntity player){ this.player = player; }

    @Override public Identifier currentTree(){ return tree; }
    @Override public String currentNode(){ return node; }
    @Override public boolean inConversation(){ return active; }
    @Override public Identifier presetTree(){ return preset; }
    @Override public void setPreset(Identifier id){ this.preset = id; }

    @Override public void start(Identifier treeId, String nodeKey){ this.tree=treeId; this.node=nodeKey; this.active=true; }
    @Override public void setNode(String nodeKey){ this.node=nodeKey; }
    @Override public void end(){ this.active=false; this.tree=null; this.node=null; }

    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup wrapperLookup){
        active = tag.getBoolean("a");
        tree = tag.contains("t") ? Identifier.of(tag.getString("t")) : null;
        node = tag.contains("n") ? tag.getString("n") : null;
        preset = tag.contains("p") ? Identifier.of(tag.getString("p")) : null;
    }
    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup wrapperLookup){

        tag.putBoolean("a", active);
        if (tree!=null) tag.putString("t", tree.toString());
        if (node!=null) tag.putString("n", node);
        if (preset!=null) tag.putString("p", preset.toString()); // add
    }
}
