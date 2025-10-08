package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class WireRecord {
    public final UUID id;
    public final Identifier defId;
    public final WireAnchor a, b;
    public final boolean aPinned, bPinned;

    public WireRecord(UUID id, Identifier defId, WireAnchor a, boolean aPinned, WireAnchor b, boolean bPinned){
        this.id = id; this.defId = defId;
        this.a = a; this.b = b;
        this.aPinned = aPinned; this.bPinned = bPinned;
    }

    public NbtCompound toNbt(){
        NbtCompound n = new NbtCompound();
        n.putUuid("id", id);
        n.putString("def", defId.toString());
        n.put("a", a.toNbt());
        n.putBoolean("ap", aPinned);
        n.put("b", b.toNbt());
        n.putBoolean("bp", bPinned);
        return n;
    }

    public static WireRecord fromNbt(NbtCompound n){
        UUID id = n.getUuid("id");
        Identifier defId = Identifier.of(n.getString("def"));
        WireAnchor a = WireAnchor.fromNbt(n.getCompound("a"));
        boolean ap = n.getBoolean("ap");
        WireAnchor b = WireAnchor.fromNbt(n.getCompound("b"));
        boolean bp = n.getBoolean("bp");
        return new WireRecord(id, defId, a, ap, b, bp);
    }
}
