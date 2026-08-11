package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LogStripper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<LogType> logType = sgGeneral.add(
        new EnumSetting.Builder<LogType>()
            .name("log")
            .description("The type of log to place, strip and break.")
            .defaultValue(LogType.Oak)
            .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("action-delay")
            .description("Ticks to wait between actions.")
            .defaultValue(4)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private enum State {
        PLACE,
        STRIP,
        BREAK
    }

    private State state = State.PLACE;

    private BlockPos currentLog;

    private int timer;
    private int actionAttempts;

    private static final int MAX_ACTION_ATTEMPTS = 10;

    private static final int OFFHAND_SLOT = 40;

    private int previousSlot = -1;

    public LogStripper() {
        super(
            QuinnAddon.CATEGORY,
            "log-stripper",
            "Places a log above your head, strips it with an axe, breaks it and repeats."
        );
    }

    @Override
    public void onActivate() {
        timer = 0;
        currentLog = null;
        state = State.PLACE;
        actionAttempts = 0;

        if (mc.player != null) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            previousSlot = -1;
        }

        if (!findAxe().found()) {
            error("No axe found in hotbar.");
            toggle();
            return;
        }

        if (!findLog().found() && !hasSelectedLogInOffhand()) {
            error("No selected log found.");
            toggle();
            return;
        }

        if (!setupHands()) {
            error("Could not move the log to the offhand.");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        cancelBreaking();

        currentLog = null;
        state = State.PLACE;
        timer = 0;
        actionAttempts = 0;

        if (mc.player != null && previousSlot >= 0) {
            InvUtils.swap(previousSlot, false);
        }

        previousSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!findAxe().found()) {
            error("No axe found in hotbar.");
            toggle();
            return;
        }

        if (!hasSelectedLogInOffhand()) {
            if (!findLog().found()) {
                info("Out of selected logs.");
                toggle();
                return;
            }

            if (!moveLogToOffhand()) {
                return;
            }

            selectAxe();
        }

        if (!isAxeInMainHand()) {
            selectAxe();
            timer = 1;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        switch (state) {
            case PLACE -> handlePlace();
            case STRIP -> handleStrip();
            case BREAK -> handleBreak();
        }
    }

    private boolean setupHands() {
        if (mc.player == null) {
            return false;
        }

        if (!hasSelectedLogInOffhand()) {
            if (!moveLogToOffhand()) {
                return false;
            }
        }

        return selectAxe();
    }

    private boolean moveLogToOffhand() {
        FindItemResult log = findLog();

        if (!log.found()) {
            return hasSelectedLogInOffhand();
        }

        if (hasSelectedLogInOffhand()) {
            return true;
        }

        /*
         * IMPORTANT:
         * In this Meteor version, .to() returns void.
         * Therefore there is NO .run() here.
         */
        InvUtils.move()
            .from(log.slot())
            .to(OFFHAND_SLOT);

        return hasSelectedLogInOffhand();
    }

    private boolean selectAxe() {
        FindItemResult axe = findAxe();

        if (!axe.found()) {
            return false;
        }

        return InvUtils.swap(axe.slot(), false);
    }

    private boolean isAxeInMainHand() {
        return mc.player != null
            && mc.player.getMainHandStack().getItem() instanceof AxeItem;
    }

    private boolean hasSelectedLogInOffhand() {
        if (mc.player == null) {
            return false;
        }

        Item selectedLog = logType.get().getItem();

        return mc.player.getOffHandStack().isOf(selectedLog);
    }

    private void handlePlace() {
        if (currentLog != null) {
            resetCurrentLog();
            return;
        }

        if (!hasSelectedLogInOffhand()) {
            if (!moveLogToOffhand()) {
                return;
            }

            selectAxe();
            return;
        }

        if (!isAxeInMainHand()) {
            selectAxe();
            return;
        }

        if (placeLog()) {
            state = State.STRIP;
            actionAttempts = 0;
            timer = actionDelay.get();
        } else {
            timer = actionDelay.get();
        }
    }

    private boolean placeLog() {
        if (
            mc.player == null
                || mc.world == null
                || mc.interactionManager == null
        ) {
            return false;
        }

        /*
         * Place directly above the player's head.
         */
        BlockPos pos = mc.player.getBlockPos().up(2);

        if (!mc.world.getBlockState(pos).isReplaceable()) {
            return false;
        }

        Vec3d hitPos = new Vec3d(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );

        BlockHitResult hit = new BlockHitResult(
            hitPos,
            Direction.DOWN,
            pos,
            false
        );

        /*
         * ONLY use the OFFHAND for placing.
         */
        ActionResult result =
            mc.interactionManager.interactBlock(
                mc.player,
                Hand.OFF_HAND,
                hit
            );

        mc.player.swingHand(Hand.OFF_HAND);

        if (!result.isAccepted()) {
            return false;
        }

        BlockState after = mc.world.getBlockState(pos);

        if (!isSelectedLog(after)) {
            return false;
        }

        currentLog = pos.toImmutable();

        /*
         * Keep the axe in the main hand.
         */
        selectAxe();

        return true;
    }

    private void handleStrip() {
        if (currentLog == null) {
            state = State.PLACE;
            actionAttempts = 0;
            timer = actionDelay.get();
            return;
        }

        BlockState stateAtTarget =
            mc.world.getBlockState(currentLog);

        if (isStrippedLog(stateAtTarget)) {
            state = State.BREAK;
            actionAttempts = 0;
            timer = actionDelay.get();
            return;
        }

        if (
            stateAtTarget.isAir()
                || stateAtTarget.isReplaceable()
        ) {
            resetCurrentLog();
            return;
        }

        if (!isSelectedLog(stateAtTarget)) {
            resetCurrentLog();
            return;
        }

        if (!isAxeInMainHand()) {
            if (!selectAxe()) {
                return;
            }

            timer = 1;
            return;
        }

        actionAttempts++;

        if (actionAttempts > MAX_ACTION_ATTEMPTS) {
            resetCurrentLog();
            return;
        }

        stripLog();

        timer = actionDelay.get();
    }

    private boolean stripLog() {
        if (
            mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || currentLog == null
        ) {
            return false;
        }

        BlockState before =
            mc.world.getBlockState(currentLog);

        if (isStrippedLog(before)) {
            return true;
        }

        if (!isSelectedLog(before)) {
            return false;
        }

        if (!isAxeInMainHand()) {
            if (!selectAxe()) {
                return false;
            }
        }

        Vec3d hitPos = new Vec3d(
            currentLog.getX() + 0.5,
            currentLog.getY() + 0.5,
            currentLog.getZ() + 0.5
        );

        BlockHitResult hit = new BlockHitResult(
            hitPos,
            Direction.DOWN,
            currentLog,
            false
        );

        /*
         * ONLY the main-hand axe strips the log.
         */
        ActionResult result =
            mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                hit
            );

        mc.player.swingHand(Hand.MAIN_HAND);

        if (!result.isAccepted()) {
            return false;
        }

        BlockState after =
            mc.world.getBlockState(currentLog);

        return isStrippedLog(after)
            || isSelectedLog(after);
    }

    private void handleBreak() {
        if (currentLog == null) {
            state = State.PLACE;
            actionAttempts = 0;
            timer = actionDelay.get();
            return;
        }

        BlockState stateAtTarget =
            mc.world.getBlockState(currentLog);

        if (
            stateAtTarget.isAir()
                || stateAtTarget.isReplaceable()
        ) {
            cancelBreaking();

            currentLog = null;
            state = State.PLACE;
            actionAttempts = 0;
            timer = actionDelay.get();

            if (hasSelectedLogInOffhand()) {
                selectAxe();
            } else if (findLog().found()) {
                moveLogToOffhand();
                selectAxe();
            }

            return;
        }

        if (!isStrippedLog(stateAtTarget)) {
            resetCurrentLog();
            return;
        }

        if (!isAxeInMainHand()) {
            if (!selectAxe()) {
                return;
            }

            timer = 1;
            return;
        }

        actionAttempts++;

        if (actionAttempts > MAX_ACTION_ATTEMPTS) {
            resetCurrentLog();
            return;
        }

        breakLog();

        timer = actionDelay.get();
    }

    private boolean breakLog() {
        if (
            mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || currentLog == null
        ) {
            return false;
        }

        BlockState stateAtTarget =
            mc.world.getBlockState(currentLog);

        if (
            stateAtTarget.isAir()
                || stateAtTarget.isReplaceable()
        ) {
            return true;
        }

        if (!isStrippedLog(stateAtTarget)) {
            return false;
        }

        if (!isAxeInMainHand()) {
            if (!selectAxe()) {
                return false;
            }
        }

        Direction direction = Direction.DOWN;

        if (!mc.interactionManager.isBreakingBlock()) {
            boolean started =
                mc.interactionManager.attackBlock(
                    currentLog,
                    direction
                );

            if (!started) {
                return false;
            }
        }

        mc.interactionManager.updateBlockBreakingProgress(
            currentLog,
            direction
        );

        mc.player.swingHand(Hand.MAIN_HAND);

        BlockState after =
            mc.world.getBlockState(currentLog);

        return after.isAir()
            || after.isReplaceable();
    }

    private void resetCurrentLog() {
        cancelBreaking();

        currentLog = null;
        state = State.PLACE;
        timer = actionDelay.get();
        actionAttempts = 0;
    }

    private void cancelBreaking() {
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    private FindItemResult findLog() {
        Item target = logType.get().getItem();

        return InvUtils.findInHotbar(
            stack ->
                !stack.isEmpty()
                    && stack.isOf(target)
        );
    }

    private FindItemResult findAxe() {
        return InvUtils.findInHotbar(
            stack ->
                !stack.isEmpty()
                    && stack.getItem() instanceof AxeItem
        );
    }

    private boolean isSelectedLog(BlockState state) {
        if (state == null) {
            return false;
        }

        return switch (logType.get()) {
            case Oak ->
                state.isOf(Blocks.OAK_LOG);

            case Spruce ->
                state.isOf(Blocks.SPRUCE_LOG);

            case Birch ->
                state.isOf(Blocks.BIRCH_LOG);

            case Jungle ->
                state.isOf(Blocks.JUNGLE_LOG);

            case Acacia ->
                state.isOf(Blocks.ACACIA_LOG);

            case DarkOak ->
                state.isOf(Blocks.DARK_OAK_LOG);

            case Mangrove ->
                state.isOf(Blocks.MANGROVE_LOG);

            case Cherry ->
                state.isOf(Blocks.CHERRY_LOG);

            case PaleOak ->
                state.isOf(Blocks.PALE_OAK_LOG);

            case Crimson ->
                state.isOf(Blocks.CRIMSON_STEM);

            case Warped ->
                state.isOf(Blocks.WARPED_STEM);
        };
    }

    private boolean isStrippedLog(BlockState state) {
        if (state == null) {
            return false;
        }

        return switch (logType.get()) {
            case Oak ->
                state.isOf(Blocks.STRIPPED_OAK_LOG);

            case Spruce ->
                state.isOf(Blocks.STRIPPED_SPRUCE_LOG);

            case Birch ->
                state.isOf(Blocks.STRIPPED_BIRCH_LOG);

            case Jungle ->
                state.isOf(Blocks.STRIPPED_JUNGLE_LOG);

            case Acacia ->
                state.isOf(Blocks.STRIPPED_ACACIA_LOG);

            case DarkOak ->
                state.isOf(Blocks.STRIPPED_DARK_OAK_LOG);

            case Mangrove ->
                state.isOf(Blocks.STRIPPED_MANGROVE_LOG);

            case Cherry ->
                state.isOf(Blocks.STRIPPED_CHERRY_LOG);

            case PaleOak ->
                state.isOf(Blocks.STRIPPED_PALE_OAK_LOG);

            case Crimson ->
                state.isOf(Blocks.STRIPPED_CRIMSON_STEM);

            case Warped ->
                state.isOf(Blocks.STRIPPED_WARPED_STEM);
        };
    }

    public enum LogType {
        Oak(Items.OAK_LOG),
        Spruce(Items.SPRUCE_LOG),
        Birch(Items.BIRCH_LOG),
        Jungle(Items.JUNGLE_LOG),
        Acacia(Items.ACACIA_LOG),
        DarkOak(Items.DARK_OAK_LOG),
        Mangrove(Items.MANGROVE_LOG),
        Cherry(Items.CHERRY_LOG),
        PaleOak(Items.PALE_OAK_LOG),
        Crimson(Items.CRIMSON_STEM),
        Warped(Items.WARPED_STEM);

        private final Item item;

        LogType(Item item) {
            this.item = item;
        }

        public Item getItem() {
            return item;
        }
    }
}
