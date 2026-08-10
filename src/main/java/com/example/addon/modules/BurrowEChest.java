package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BurrowEChest extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ============================================================
    // GENERAL SETTINGS
    // ============================================================

    private final Setting<BurrowBlock> burrowBlock = sgGeneral.add(
        new EnumSetting.Builder<BurrowBlock>()
            .name("burrow-block")
            .description("Block used for the final burrow.")
            .defaultValue(BurrowBlock.EnderChest)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the target when placing blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> packetOffset = sgGeneral.add(
        new IntSetting.Builder()
            .name("packet-offset")
            .description("Vertical offset used during the burrow.")
            .defaultValue(10)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-disable")
            .description("Disable after successfully completing the burrow.")
            .defaultValue(true)
            .build()
    );

    // ============================================================
    // RENDER SETTINGS
    // ============================================================

    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Render a box around the last block placed by this module.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the block box is rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the filled portion of the block box.")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Color of the outline of the block box.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
    );

    // ============================================================
    // STATE
    // ============================================================

    private BlockPos targetPos;

    /*
     * Position of the most recently placed block.
     *
     * This is what the rendering box follows.
     */
    private BlockPos renderedBlockPos;

    private boolean mining;
    private boolean waitingForGround;
    private boolean jumpingToPlace;
    private boolean waitingForEnderChest;
    private boolean burrowPhase;
    private boolean finished;

    private int previousSlot = -1;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public BurrowEChest() {
        super(
            QuinnAddon.CATEGORY,
            "burrow-echest",
            "Mines the block under your feet, falls, jumps back up, places an Ender Chest, then burrows."
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        /*
         * Remember the original block underneath the player.
         */
        targetPos = mc.player.getBlockPos().down();

        /*
         * Clear the old render position.
         */
        renderedBlockPos = null;

        mining = true;
        waitingForGround = false;
        jumpingToPlace = false;
        waitingForEnderChest = false;
        burrowPhase = false;
        finished = false;

        previousSlot =
            mc.player.getInventory().getSelectedSlot();

        /*
         * We need an Ender Chest for the safety phase.
         */
        if (!findEnderChest().found()) {
            error("No Ender Chest found in hotbar.");
            toggle();
            return;
        }

        /*
         * We also need the configured burrow block.
         */
        if (!findBurrowBlock().found()) {
            error("Selected burrow block is not in the hotbar.");
            toggle();
        }
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {
        if (mc.interactionManager != null && mining) {
            mc.interactionManager.cancelBlockBreaking();
        }

        if (mc.player != null && previousSlot >= 0) {
            InvUtils.swap(previousSlot, false);
        }

        targetPos = null;
        renderedBlockPos = null;

        mining = false;
        waitingForGround = false;
        jumpingToPlace = false;
        waitingForEnderChest = false;
        burrowPhase = false;
        finished = false;

        previousSlot = -1;
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (finished || targetPos == null) {
            return;
        }

        // --------------------------------------------------------
        // PHASE 1: MINE
        // --------------------------------------------------------

        if (mining) {
            mineTarget();
            return;
        }

        // --------------------------------------------------------
        // PHASE 2: WAIT FOR GROUND
        // --------------------------------------------------------

        if (waitingForGround) {
            waitForGround();
            return;
        }

        // --------------------------------------------------------
        // PHASE 3: JUMP AND PLACE E-CHEST
        // --------------------------------------------------------

        if (jumpingToPlace) {
            jumpAndPlaceEnderChest();
            return;
        }

        // --------------------------------------------------------
        // PHASE 4: WAIT ON E-CHEST
        // --------------------------------------------------------

        if (waitingForEnderChest) {
            waitForEnderChest();
            return;
        }

        // --------------------------------------------------------
        // PHASE 5: BURROW
        // --------------------------------------------------------

        if (burrowPhase) {
            performBurrow();
        }
    }

    // ============================================================
    // MINE TARGET
    // ============================================================

    private void mineTarget() {
        if (mc.world == null || targetPos == null) {
            return;
        }

        BlockState state =
            mc.world.getBlockState(targetPos);

        /*
         * Only continue once the block is completely gone.
         */
        if (state.isAir() || state.isReplaceable()) {
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }

            mining = false;

            /*
             * Let the player fall first.
             */
            waitingForGround = true;

            return;
        }

        FindItemResult pickaxe =
            findPickaxe();

        if (!pickaxe.found()) {
            error("No pickaxe found in hotbar.");
            finish();
            return;
        }

        /*
         * Automatically switch to the pickaxe.
         */
        InvUtils.swap(
            pickaxe.slot(),
            false
        );

        if (mc.interactionManager == null) {
            return;
        }

        /*
         * 1.21.11 API:
         *
         * isBreakingBlock() takes no arguments.
         */
        if (!mc.interactionManager.isBreakingBlock()) {
            boolean started =
                mc.interactionManager.attackBlock(
                    targetPos,
                    Direction.UP
                );

            if (!started) {
                error("Unable to start mining the block.");
                finish();
                return;
            }
        }

        /*
         * Continue breaking.
         */
        mc.interactionManager.updateBlockBreakingProgress(
            targetPos,
            Direction.UP
        );
    }

    // ============================================================
    // WAIT FOR GROUND
    // ============================================================

    private void waitForGround() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        /*
         * Do nothing while falling.
         */
        if (!mc.player.isOnGround()) {
            return;
        }

        /*
         * The player has landed.
         */
        waitingForGround = false;
        jumpingToPlace = true;

        /*
         * Jump back toward the original position.
         */
        mc.player.jump();
    }

    // ============================================================
    // JUMP AND PLACE ENDER CHEST
    // ============================================================

    private void jumpAndPlaceEnderChest() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        FindItemResult enderChest =
            findEnderChest();

        if (!enderChest.found()) {
            error("No Ender Chest found in hotbar.");
            finish();
            return;
        }

        /*
         * Switch to the Ender Chest.
         */
        InvUtils.swap(
            enderChest.slot(),
            false
        );

        double targetY =
            targetPos.getY();

        /*
         * Wait until the player is high enough.
         */
        if (mc.player.getY() < targetY + 1.0) {
            return;
        }

        /*
         * Attempt several placements immediately.
         */
        for (int i = 0; i < 5; i++) {
            BlockUtils.place(
                targetPos,
                enderChest,
                rotate.get(),
                0,
                true,
                true,
                true
            );

            BlockState state =
                mc.world.getBlockState(targetPos);

            if (state.isOf(Blocks.ENDER_CHEST)) {

                /*
                 * Remember this block for rendering.
                 */
                renderedBlockPos = targetPos.toImmutable();

                jumpingToPlace = false;
                waitingForEnderChest = true;

                return;
            }
        }
    }

    // ============================================================
    // WAIT FOR E-CHEST LANDING
    // ============================================================

    private void waitForEnderChest() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BlockState state =
            mc.world.getBlockState(targetPos);

        /*
         * If the Ender Chest disappeared, try placing it again.
         */
        if (!state.isOf(Blocks.ENDER_CHEST)) {
            waitingForEnderChest = false;
            jumpingToPlace = true;
            return;
        }

        double chestTop =
            targetPos.getY() + 1.0;

        /*
         * Check that the player is actually standing
         * on the Ender Chest.
         */
        boolean standingOnChest =
            mc.player.isOnGround()
            && mc.player.getY() >= chestTop - 0.15
            && mc.player.getY() <= chestTop + 0.25;

        if (!standingOnChest) {
            return;
        }

        waitingForEnderChest = false;
        burrowPhase = true;

        performBurrow();
    }

    // ============================================================
    // BURROW
    // ============================================================

    private void performBurrow() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        FindItemResult burrowItem =
            findBurrowBlock();

        if (!burrowItem.found()) {
            error("Selected burrow block is not in the hotbar.");
            finish();
            return;
        }

        /*
         * Switch to the configured burrow block.
         */
        InvUtils.swap(
            burrowItem.slot(),
            false
        );

        /*
         * Place the final burrow block.
         */
        BlockUtils.place(
            targetPos,
            burrowItem,
            rotate.get(),
            0,
            true,
            true,
            true
        );

        BlockState state =
            mc.world.getBlockState(targetPos);

        /*
         * Don't continue until the block actually exists.
         */
        if (!isSelectedBurrowBlock(state)) {
            return;
        }

        /*
         * Remember the final block too.
         */
        renderedBlockPos = targetPos.toImmutable();

        // --------------------------------------------------------
        // BURROW PACKETS
        // --------------------------------------------------------

        double x =
            mc.player.getX();

        double y =
            targetPos.getY();

        double z =
            mc.player.getZ();

        sendPosition(
            x,
            y + 0.1,
            z,
            false
        );

        sendPosition(
            x,
            y + packetOffset.get(),
            z,
            false
        );

        sendPosition(
            x,
            y,
            z,
            false
        );

        /*
         * Synchronize local position.
         */
        mc.player.setPosition(
            x,
            y,
            z
        );

        finishSuccessfully();
    }

    // ============================================================
    // RENDER
    // ============================================================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) {
            return;
        }

        if (renderedBlockPos == null) {
            return;
        }

        if (mc.world == null) {
            return;
        }

        /*
         * Only render the box while the block is still present.
         */
        BlockState state =
            mc.world.getBlockState(renderedBlockPos);

        if (state.isAir()) {
            return;
        }

        /*
         * Render exactly one block-sized box around the
         * block placed by this module.
         */
        event.renderer.box(
            renderedBlockPos,
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    // ============================================================
    // BURROW BLOCK CHECK
    // ============================================================

    private boolean isSelectedBurrowBlock(
        BlockState state
    ) {
        return switch (burrowBlock.get()) {
            case NetheriteBlock ->
                state.isOf(
                    Blocks.NETHERITE_BLOCK
                );

            case Obsidian ->
                state.isOf(
                    Blocks.OBSIDIAN
                );

            case CryingObsidian ->
                state.isOf(
                    Blocks.CRYING_OBSIDIAN
                );

            case HeavyCore ->
                state.isOf(
                    Blocks.HEAVY_CORE
                );

            case EnderChest ->
                state.isOf(
                    Blocks.ENDER_CHEST
                );
        };
    }

    // ============================================================
    // FIND PICKAXE
    // ============================================================

    private FindItemResult findPickaxe() {
        return InvUtils.findInHotbar(stack -> {
            if (stack.isEmpty()) {
                return false;
            }

            return stack.isOf(
                    Items.WOODEN_PICKAXE
                )
                || stack.isOf(
                    Items.STONE_PICKAXE
                )
                || stack.isOf(
                    Items.IRON_PICKAXE
                )
                || stack.isOf(
                    Items.GOLDEN_PICKAXE
                )
                || stack.isOf(
                    Items.DIAMOND_PICKAXE
                )
                || stack.isOf(
                    Items.NETHERITE_PICKAXE
                );
        });
    }

    // ============================================================
    // FIND ENDER CHEST
    // ============================================================

    private FindItemResult findEnderChest() {
        return InvUtils.findInHotbar(
            stack ->
                !stack.isEmpty()
                    && stack.isOf(
                        Items.ENDER_CHEST
                    )
        );
    }

    // ============================================================
    // FIND BURROW BLOCK
    // ============================================================

    private FindItemResult findBurrowBlock() {
        return switch (burrowBlock.get()) {
            case NetheriteBlock ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.isOf(
                            Items.NETHERITE_BLOCK
                        )
                );

            case Obsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.isOf(
                            Items.OBSIDIAN
                        )
                );

            case CryingObsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.isOf(
                            Items.CRYING_OBSIDIAN
                        )
                );

            case HeavyCore ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.isOf(
                            Items.HEAVY_CORE
                        )
                );

            case EnderChest ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.isOf(
                            Items.ENDER_CHEST
                        )
                );
        };
    }

    // ============================================================
    // POSITION PACKET
    // ============================================================

    private void sendPosition(
        double x,
        double y,
        double z,
        boolean onGround
    ) {
        if (mc.player == null) {
            return;
        }

        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(
                x,
                y,
                z,
                onGround,
                false
            )
        );
    }

    // ============================================================
    // FINISH
    // ============================================================

    private void finishSuccessfully() {
        finished = true;

        if (autoDisable.get()) {
            toggle();
        }
    }

    private void finish() {
        finished = true;

        if (autoDisable.get() && isActive()) {
            toggle();
        }
    }

    // ============================================================
    // BURROW BLOCK OPTIONS
    // ============================================================

    public enum BurrowBlock {
        NetheriteBlock,
        Obsidian,
        CryingObsidian,
        HeavyCore,
        EnderChest
    }
}
