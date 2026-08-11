package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoTotemPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================

    private final Setting<Keybind> xpKeybind = sgGeneral.add(
        new KeybindSetting.Builder()
            .name("xp-keybind")
            .description("Hold this key to throw XP bottles. Does nothing while a GUI is open.")
            .defaultValue(Keybind.none())
            .build()
    );

    private final Setting<Integer> xpDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("xp-delay")
            .description("Ticks between XP bottle throws.")
            .defaultValue(2)
            .min(0)
            .max(20)
            .sliderMin(0)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> totemDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("totem-delay")
            .description("Ticks to wait before putting a Totem in the offhand.")
            .defaultValue(0)
            .min(0)
            .max(5)
            .sliderMin(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Integer> minHealth = sgGeneral.add(
        new IntSetting.Builder()
            .name("minimum-health")
            .description("Do not swap the Totem out for XP when health is at or below this value. 0 disables the health check.")
            .defaultValue(10)
            .min(0)
            .max(36)
            .sliderMin(0)
            .sliderMax(36)
            .build()
    );

    // ============================================================
    // STATE
    // ============================================================

    private int xpTimer;
    private int totemTimer;

    private boolean wasThrowingXp;
    private boolean waitingForTotem;

    /*
     * Exact main-inventory slot where the XP came from.
     *
     * 9-35 = main inventory.
     */
    private int xpInventorySlot = -1;

    /*
     * Prevents repeated XP swap packets before the previous
     * inventory update has been processed.
     */
    private boolean xpSwapPending;

    /*
     * Totem swap state.
     */
    private boolean totemSwapPending;
    private int totemInventorySlot = -1;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public AutoTotemPlus() {
        super(
            QuinnAddon.CATEGORY,
            "autototem+",
            "Automatically keeps a Totem in the offhand and throws XP while holding the XP keybind. Credits to H_ux, Discord h.u.x."
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        xpTimer = 0;
        totemTimer = 0;

        wasThrowingXp = false;
        waitingForTotem = false;

        xpInventorySlot = -1;
        xpSwapPending = false;

        totemSwapPending = false;
        totemInventorySlot = -1;
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {
        xpTimer = 0;
        totemTimer = 0;

        wasThrowingXp = false;
        waitingForTotem = false;

        xpSwapPending = false;

        totemSwapPending = false;
        totemInventorySlot = -1;
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (
            mc.player == null
                || mc.world == null
                || mc.interactionManager == null
        ) {
            return;
        }

        /*
         * ========================================================
         * XP SYSTEM
         * ========================================================
         *
         * IMPORTANT:
         *
         * The XP keybind is completely ignored while a GUI is
         * open.
         *
         * This means:
         *
         * - No XP swap
         * - No XP throwing
         * - No XP return
         * - No XP inventory changes
         *
         * while a GUI is open.
         *
         * The Totem system below can still operate normally.
         */
        if (mc.currentScreen == null) {
            boolean throwingXp = xpKeybind.get().isPressed();

            // ====================================================
            // XP KEY HELD
            // ====================================================

            if (throwingXp) {
                handleXpMode();

                wasThrowingXp = true;
            }

            // ====================================================
            // XP KEY RELEASED
            // ====================================================

            else {
                /*
                 * If XP is still in the offhand, return it to the
                 * exact inventory slot it originally came from.
                 */
                if (
                    xpInventorySlot != -1
                        && isXp(mc.player.getOffHandStack())
                ) {
                    returnXpToInventory();
                }

                wasThrowingXp = false;
            }
        }

        /*
         * ========================================================
         * TOTEM SYSTEM
         * ========================================================
         *
         * Totem mode is intentionally outside the GUI check.
         * This allows AutoTotemPlus to continue protecting the
         * player even while a GUI is open.
         */
        if (mc.currentScreen == null || !wasThrowingXp) {
            handleTotemMode();
        }
    }

    // ============================================================
    // XP MODE
    // ============================================================

    private void handleXpMode() {
        /*
         * ========================================================
         * HEALTH CHECK
         * ========================================================
         *
         * If the player's health is at or below the configured
         * threshold, DO NOT move the Totem out of the offhand.
         */
        if (
            !isXp(mc.player.getOffHandStack())
                && shouldProtectTotem()
        ) {
            return;
        }

        /*
         * XP is already in the offhand.
         */
        if (isXp(mc.player.getOffHandStack())) {
            xpSwapPending = false;

            throwOffhand();

            return;
        }

        /*
         * If a swap was just sent, check whether the XP has
         * reached the offhand.
         */
        if (xpSwapPending) {
            if (isXp(mc.player.getOffHandStack())) {
                xpSwapPending = false;
                return;
            }

            xpSwapPending = false;
        }

        /*
         * Find XP if we don't already have a remembered slot.
         */
        if (xpInventorySlot == -1) {
            xpInventorySlot = findXpInMainInventory();
        }

        if (xpInventorySlot == -1) {
            return;
        }

        /*
         * Make sure the remembered slot still contains XP.
         */
        if (!isXp(
            mc.player.getInventory()
                .getStack(xpInventorySlot)
        )) {
            xpInventorySlot = findXpInMainInventory();

            if (xpInventorySlot == -1) {
                return;
            }
        }

        /*
         * Direct inventory slot <-> offhand swap.
         *
         * SWAP button 40 = offhand.
         */
        swapSlotWithOffhand(xpInventorySlot);

        xpSwapPending = true;
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    private boolean shouldProtectTotem() {
        if (mc.player == null) {
            return true;
        }

        int threshold = minHealth.get();

        /*
         * 0 means health protection is disabled.
         */
        if (threshold <= 0) {
            return false;
        }

        /*
         * Minecraft health:
         *
         * 20.0 = 10 hearts
         * 10.0 = 5 hearts
         * 4.0  = 2 hearts
         */
        return mc.player.getHealth() <= threshold;
    }

    // ============================================================
    // RETURN XP
    // ============================================================

    private void returnXpToInventory() {
        if (
            mc.player == null
                || mc.interactionManager == null
        ) {
            return;
        }

        /*
         * If XP isn't in the offhand, there is nothing to return.
         */
        if (!isXp(mc.player.getOffHandStack())) {
            xpInventorySlot = -1;
            xpSwapPending = false;
            return;
        }

        /*
         * We must know the original slot.
         */
        if (
            xpInventorySlot < 9
                || xpInventorySlot > 35
        ) {
            return;
        }

        /*
         * Directly swap:
         *
         * OFFHAND XP
         *      ↕
         * ORIGINAL XP SLOT
         */
        swapSlotWithOffhand(xpInventorySlot);

        xpSwapPending = false;
    }

    // ============================================================
    // TOTEM MODE
    // ============================================================

    private void handleTotemMode() {
        if (
            mc.player == null
                || mc.interactionManager == null
        ) {
            return;
        }

        /*
         * Never replace XP with a Totem.
         */
        if (isXp(mc.player.getOffHandStack())) {
            return;
        }

        /*
         * Totem already equipped.
         */
        if (isTotem(mc.player.getOffHandStack())) {
            waitingForTotem = false;
            totemTimer = 0;

            xpInventorySlot = -1;

            return;
        }

        /*
         * Check whether a previous Totem swap succeeded.
         */
        if (totemSwapPending) {
            if (isTotem(mc.player.getOffHandStack())) {
                totemSwapPending = false;
                totemInventorySlot = -1;

                waitingForTotem = false;
                totemTimer = 0;

                xpInventorySlot = -1;

                return;
            }

            /*
             * Previous transaction didn't complete.
             */
            totemSwapPending = false;
        }

        /*
         * Start the configurable Totem delay.
         */
        if (!waitingForTotem) {
            waitingForTotem = true;
            totemTimer = totemDelay.get();
        }

        if (totemTimer > 0) {
            totemTimer--;
            return;
        }

        /*
         * Find Totem ONLY in main inventory.
         */
        totemInventorySlot = findTotemInMainInventory();

        if (totemInventorySlot == -1) {
            /*
             * No Totem available.
             *
             * Keep checking.
             */
            waitingForTotem = true;
            totemTimer = Math.max(1, totemDelay.get());

            return;
        }

        /*
         * Direct main-inventory <-> offhand swap.
         */
        swapSlotWithOffhand(totemInventorySlot);

        totemSwapPending = true;
    }

    // ============================================================
    // DIRECT OFFHAND SWAP
    // ============================================================

    private void swapSlotWithOffhand(int inventorySlot) {
        if (
            mc.player == null
                || mc.interactionManager == null
        ) {
            return;
        }

        /*
         * Only main inventory slots are used.
         *
         * 9-35 = main inventory.
         */
        if (
            inventorySlot < 9
                || inventorySlot > 35
        ) {
            return;
        }

        int syncId =
            mc.player.currentScreenHandler.syncId;

        /*
         * SlotActionType.SWAP
         *
         * Button 40 = offhand.
         *
         * This directly exchanges the selected inventory
         * slot with the offhand without touching the cursor.
         */
        mc.interactionManager.clickSlot(
            syncId,
            inventorySlot,
            40,
            SlotActionType.SWAP,
            mc.player
        );
    }

    // ============================================================
    // THROW XP
    // ============================================================

    private void throwOffhand() {
        if (
            mc.player == null
                || mc.interactionManager == null
        ) {
            return;
        }

        /*
         * Extra safety check:
         *
         * Never throw XP while a GUI is open.
         */
        if (mc.currentScreen != null) {
            return;
        }

        if (!isXp(mc.player.getOffHandStack())) {
            return;
        }

        if (xpTimer > 0) {
            xpTimer--;
            return;
        }

        mc.interactionManager.interactItem(
            mc.player,
            Hand.OFF_HAND
        );

        mc.player.swingHand(Hand.OFF_HAND);

        xpTimer = xpDelay.get();
    }

    // ============================================================
    // FIND XP
    // ============================================================

    private int findXpInMainInventory() {
        if (mc.player == null) {
            return -1;
        }

        /*
         * Main inventory only.
         *
         * 9-35.
         */
        for (int i = 9; i <= 35; i++) {
            if (
                isXp(
                    mc.player.getInventory()
                        .getStack(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // FIND TOTEM
    // ============================================================

    private int findTotemInMainInventory() {
        if (mc.player == null) {
            return -1;
        }

        /*
         * Main inventory only.
         *
         * 9-35.
         */
        for (int i = 9; i <= 35; i++) {
            if (
                isTotem(
                    mc.player.getInventory()
                        .getStack(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // ITEM CHECKS
    // ============================================================

    private boolean isXp(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.isOf(Items.EXPERIENCE_BOTTLE);
    }

    private boolean isTotem(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.isOf(Items.TOTEM_OF_UNDYING);
    }
}
