package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathSounds extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<DeathSound> sound = sgGeneral.add(
        new EnumSetting.Builder<DeathSound>()
            .name("sound")
            .description("Sound played when a player dies.")
            .defaultValue(DeathSound.GOAT_HORN_1)
            .build()
    );

    /*
     * One-shot test control.
     *
     * Turn this on in the module settings and the selected
     * sound will play once. It automatically turns itself
     * back off.
     */
    private final Setting<Boolean> testSound = sgGeneral.add(
        new BoolSetting.Builder()
            .name("test-sound")
            .description("Play the selected sound once for testing.")
            .defaultValue(false)
            .build()
    );

    private final Set<UUID> deadPlayers = new HashSet<>();

    public DeathSounds() {
        super(
            QuinnAddon.CATEGORY,
            "death-sounds",
            "Plays a vanilla sound when a player dies."
        );
    }

    @Override
    public void onActivate() {
        deadPlayers.clear();
    }

    @Override
    public void onDeactivate() {
        deadPlayers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        /*
         * Test the currently selected sound.
         */
        if (testSound.get()) {
            playTestSound();

            /*
             * Reset the setting so it acts like a button.
             */
            testSound.set(false);
        }

        /*
         * Check all players currently loaded in the world.
         */
        for (PlayerEntity player : mc.world.getPlayers()) {
            /*
             * Don't trigger for yourself.
             */
            if (player == mc.player) {
                continue;
            }

            UUID uuid = player.getUuid();

            /*
             * Detect the transition into the dead state.
             */
            if (player.isDead()) {
                if (deadPlayers.add(uuid)) {
                    handleDeath(player);
                }
            } else {
                /*
                 * Allow the same player to trigger again
                 * after they respawn.
                 */
                deadPlayers.remove(uuid);
            }
        }
    }

    private void handleDeath(PlayerEntity player) {
        SoundEvent soundEvent = sound.get().getSound();

        if (soundEvent == null || mc.world == null) {
            return;
        }

        /*
         * Play the selected sound at the player's
         * death location.
         */
        mc.world.playSoundClient(
            player.getX(),
            player.getY(),
            player.getZ(),
            soundEvent,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f,
            true
        );
    }

    private void playTestSound() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        SoundEvent soundEvent = sound.get().getSound();

        if (soundEvent == null) {
            return;
        }

        /*
         * Play the sound at the player's current location.
         */
        mc.world.playSoundClient(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            soundEvent,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f,
            true
        );
    }

    private enum DeathSound {
        GOAT_HORN_1,
        GOAT_HORN_2,
        GOAT_HORN_3,
        GOAT_HORN_4,
        GOAT_HORN_5,
        GOAT_HORN_6,
        GOAT_HORN_7,
        GOAT_HORN_8,

        WITHER_SPAWN,
        WITHER_DEATH,

        END_PORTAL_OPEN,
        LIGHTNING;

        public SoundEvent getSound() {
            return switch (this) {
                case GOAT_HORN_1 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(0).value();

                case GOAT_HORN_2 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(1).value();

                case GOAT_HORN_3 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(2).value();

                case GOAT_HORN_4 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(3).value();

                case GOAT_HORN_5 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(4).value();

                case GOAT_HORN_6 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(5).value();

                case GOAT_HORN_7 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(6).value();

                case GOAT_HORN_8 ->
                    SoundEvents.GOAT_HORN_SOUNDS.get(7).value();

                case WITHER_SPAWN ->
                    SoundEvents.ENTITY_WITHER_SPAWN;

                case WITHER_DEATH ->
                    SoundEvents.ENTITY_WITHER_DEATH;

                case END_PORTAL_OPEN ->
                    SoundEvents.BLOCK_END_PORTAL_SPAWN;

                case LIGHTNING ->
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            };
        }
    }
}
