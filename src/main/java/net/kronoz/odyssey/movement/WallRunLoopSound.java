// src/main/java/net/kronoz/odyssey/client/movement/WallRunLoopSound.java
package net.kronoz.odyssey.movement;

import net.kronoz.odyssey.init.ModSounds;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.player.PlayerEntity;

public final class WallRunLoopSound extends MovingSoundInstance {
    private final PlayerEntity player;

    public WallRunLoopSound(PlayerEntity player) {
        super(ModSounds.WALLRUN_LOOP, SoundCategory.PLAYERS, player.getRandom());
        this.player = player;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.6f;
        this.pitch = 1.0f;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
    }

    @Override
    public void tick() {
        if (player.isRemoved() || player.isDead()) {
            this.setDone();
            return;
        }
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
