package net.kronoz.odyssey.cca;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.net.BodyModPackets;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BodyModComponentImpl implements BodyModComponent, AutoSyncedComponent {
    private final PlayerEntity player;
    private final Map<String, Identifier> equipped = new HashMap<>();

    public BodyModComponentImpl(PlayerEntity player){ this.player = player; }

    @Override public Map<String, Identifier> getEquipped(){ return Collections.unmodifiableMap(equipped); }

    @Override public void setPart(String slot, Identifier partId) {
        clearSlot(slot);
        equipped.put(slot, partId);
        applyAttr(slot, partId);
    }

    @Override public void clearSlot(String slot) {
        removeAttr(slot);
        equipped.remove(slot);
    }

    private Identifier key(String slot, String kind){
        return Odyssey.id("bodymod_" + slot + "_" + kind);
    }

    private void applyAttr(String slot, Identifier partId) {
        BodyPartRegistry.Part p = BodyPartRegistry.get(partId);
        if (p == null) return;

        if (p.speed != 0.0) {
            var inst = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(key(slot,"speed"));
                inst.addPersistentModifier(new EntityAttributeModifier(
                        key(slot,"speed"),
                        (double)p.speed,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        if (p.attack != 0.0) {
            var inst = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (inst != null) {
                inst.removeModifier(key(slot,"attack"));
                inst.addPersistentModifier(new EntityAttributeModifier(
                        key(slot,"attack"),
                        (double)p.attack,
                        EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private void removeAttr(String slot) {
        var sp = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        var ad = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (sp != null) sp.removeModifier(key(slot,"speed"));
        if (ad != null) ad.removeModifier(key(slot,"attack"));
    }

    @Override public void sync(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                    new BodyModPackets.SyncBodyS2C(
                            this.player.getUuid(),
                            new HashMap<>(equipped)));
        }
    }

    @Override public void clientApply(Map<String, Identifier> map) {
        equipped.clear();
        equipped.putAll(map);
    }

    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup wrapperLookup) {
        equipped.clear();
        for (String k : tag.getKeys()) {
            if (tag.contains(k)) equipped.put(k, Identifier.of(tag.getString(k)));
        }
    }

    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup wrapperLookup) {
        for (var e : equipped.entrySet()) {
            tag.putString(e.getKey(), e.getValue().toString());
        }
    }

}
