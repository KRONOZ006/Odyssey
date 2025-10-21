package net.kronoz.odyssey.dialogue;

import net.minecraft.util.Identifier;

import java.util.Optional;

public record Step(int atTicks, int durationTicks, String caption, Identifier soundId) {
    public Optional<Identifier> optSound() { return Optional.ofNullable(soundId); }
}
