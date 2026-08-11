package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BedrockNuker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ============================================================
    // GENERAL SETTINGS
    // ============================================================

    private final Setting<Double> range = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance to target bedrock.")
            .defaultValue(6.0)
            .min(1.0)
            .max(6.0)
            .sliderMin(1.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(
        new EnumSetting.Builder<SortMode>()
            .name("sort-mode")
            .description("Determines which bedrock block is targeted.")
            .defaultValue(SortMode.Closest)
            .build()
    );

    private final Setting<MiningMode> miningMode = sgGeneral.add(
        new EnumSetting.Builder<MiningMode>()
            .name("mining-mode")
            .description("Determines which bedrock blocks can be targeted.")
            .defaultValue(MiningMode.All)
            .build()
    );

    private final Setting<Boolean> pauseWhileEat = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-while-eat")
            .description(
                "Pauses bedrock mining while using an enchanted golden apple."
            )
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> resetOnRangeExit = sgGeneral.add(
        new BoolSetting.Builder()
            .name("reset-on-range-exit")
            .description(
                "Resets the current mining target when it moves out of range."
            )
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the bedrock being mined.")
            .defaultValue(false)
            .build()
    );

    // ============================================================
    // RENDER SETTINGS
    // ============================================================

    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Render the block currently being mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> renderGrowTicks = sgRender.add(
        new IntSetting.Builder()
            .name("render-grow-ticks")
            .description(
                "Ticks required for the render box to grow to full size."
            )
            .defaultValue(20)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the target block is rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the target block's sides.")
            .defaultValue(
                new SettingColor(
                    255,
                    255,
                    255,
                    40
                )
            )
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Color of the target block's outline.")
            .defaultValue(
                new SettingColor(
                    255,
                    255,
                    255,
                    255
                )
            )
            .build()
    );

    // ============================================================
    // STATE
    // ============================================================

    private BlockPos miningPos;

    private int renderTicks;

    private BlockPos lastRenderPos;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public BedrockNuker() {
        super(
            QuinnAddon.CATEGORY,
            "bedrock-nuker",
            "Automatically mines nearby bedrock."
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        miningPos = null;
        renderTicks = 0;
        lastRenderPos = null;
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {
        stopMining();
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            stopMining();
            return;
        }

        // --------------------------------------------------------
        // PAUSE WHILE USING ENCHANTED GOLDEN APPLE
        // --------------------------------------------------------

        if (pauseWhileEat.get()
            && isUsingEnchantedGoldenApple()) {

            return;
        }

        // --------------------------------------------------------
        // CHECK CURRENT TARGET
        // --------------------------------------------------------

        if (miningPos != null) {

            // Target must still be bedrock.
            if (!isBedrock(miningPos)) {
                stopMining();
            }

            // Reset target when it leaves range.
            else if (!inRange(miningPos)) {

                if (resetOnRangeExit.get()) {
                    stopMining();
                } else {
                    return;
                }
            }

            // Never target bottom-most world layer.
            else if (
                miningPos.getY()
                    <= mc.world.getBottomY()
            ) {
                stopMining();
            }
        }

        // --------------------------------------------------------
        // FIND NEW TARGET
        // --------------------------------------------------------

        if (miningPos == null) {
            BlockPos target = findBedrock();

            if (target == null) {
                return;
            }

            startMining(target);
        }

        // --------------------------------------------------------
        // CONTINUE MINING
        // --------------------------------------------------------

        if (miningPos != null) {
            mineBlock();
        }
    }

    // ============================================================
    // START MINING
    // ============================================================

    private void startMining(BlockPos pos) {
        if (mc.interactionManager == null) {
            return;
        }

        miningPos = pos.toImmutable();

        // Reset render when a new target is selected.
        renderTicks = 0;
        lastRenderPos = miningPos.toImmutable();

        mc.interactionManager.attackBlock(
            miningPos,
            Direction.UP
        );
    }

    // ============================================================
    // MINE BLOCK
    // ============================================================

    private void mineBlock() {
        if (
            mc.player == null
            || mc.world == null
            || mc.interactionManager == null
            || miningPos == null
        ) {
            stopMining();
            return;
        }

        if (!isBedrock(miningPos)) {
            stopMining();
            return;
        }

        if (
            miningPos.getY()
                <= mc.world.getBottomY()
        ) {
            stopMining();
            return;
        }

        // Range check before every mining action.
        if (!inRange(miningPos)) {
            stopMining();
            return;
        }

        /*
         * Continue normal Minecraft block breaking.
         */
        mc.interactionManager.updateBlockBreakingProgress(
            miningPos,
            Direction.UP
        );

        /*
         * Send swing packet to the server.
         */
        mc.player.networkHandler.sendPacket(
            new HandSwingC2SPacket(
                Hand.MAIN_HAND
            )
        );

        /*
         * Advance render animation.
         */
        if (renderTicks < renderGrowTicks.get()) {
            renderTicks++;
        }
    }

    // ============================================================
    // FIND BEDROCK
    // ============================================================

    private BlockPos findBedrock() {
        if (
            mc.player == null
            || mc.world == null
        ) {
            return null;
        }

        BlockPos playerPos =
            mc.player.getBlockPos();

        int radius =
            (int) Math.ceil(range.get());

        double maxDistance =
            range.get() * range.get();

        BlockPos best = null;

        double bestDistance = 0.0;

        int bestY = Integer.MIN_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos =
                        playerPos.add(
                            x,
                            y,
                            z
                        );

                    // ------------------------------------------------
                    // BEDROCK ONLY
                    // ------------------------------------------------

                    if (!isBedrock(pos)) {
                        continue;
                    }

                    // ------------------------------------------------
                    // NEVER TARGET BOTTOM LAYER
                    // ------------------------------------------------

                    if (
                        pos.getY()
                            <= mc.world.getBottomY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // FLATTEN MODE
                    // ------------------------------------------------

                    if (
                        miningMode.get()
                            == MiningMode.Flatten
                        && pos.getY()
                            < mc.player.getBlockY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // RANGE
                    // ------------------------------------------------

                    double distance =
                        mc.player.squaredDistanceTo(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                        );

                    if (distance > maxDistance) {
                        continue;
                    }

                    // ------------------------------------------------
                    // CLOSEST
                    // ------------------------------------------------

                    if (
                        sortMode.get()
                            == SortMode.Closest
                    ) {

                        if (
                            best == null
                            || distance < bestDistance
                        ) {

                            best =
                                pos.toImmutable();

                            bestDistance =
                                distance;
                        }
                    }

                    // ------------------------------------------------
                    // FURTHEST
                    // ------------------------------------------------

                    else if (
                        sortMode.get()
                            == SortMode.Furthest
                    ) {

                        if (
                            best == null
                            || distance > bestDistance
                        ) {

                            best =
                                pos.toImmutable();

                            bestDistance =
                                distance;
                        }
                    }

                    // ------------------------------------------------
                    // TOP-DOWN
                    // ------------------------------------------------

                    else if (
                        sortMode.get()
                            == SortMode.TopDown
                    ) {

                        /*
                         * Highest Y has priority.
                         *
                         * Same Y = closest wins.
                         */
                        if (
                            best == null
                            || pos.getY() > bestY
                            || (
                                pos.getY() == bestY
                                && distance < bestDistance
                            )
                        ) {

                            best =
                                pos.toImmutable();

                            bestY =
                                pos.getY();

                            bestDistance =
                                distance;
                        }
                    }
                }
            }
        }

        return best;
    }

    // ============================================================
    // ENCHANTED GOLDEN APPLE CHECK
    // ============================================================

    private boolean isUsingEnchantedGoldenApple() {
        if (mc.player == null) {
            return false;
        }

        if (!mc.player.isUsingItem()) {
            return false;
        }

        return mc.player.getActiveItem().isOf(
            Items.ENCHANTED_GOLDEN_APPLE
        );
    }

    // ============================================================
    // BEDROCK CHECK
    // ============================================================

    private boolean isBedrock(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        BlockState state =
            mc.world.getBlockState(pos);

        return state.isOf(
            Blocks.BEDROCK
        );
    }

    // ============================================================
    // RANGE CHECK
    // ============================================================

    private boolean inRange(BlockPos pos) {
        if (mc.player == null) {
            return false;
        }

        double maxDistance =
            range.get() * range.get();

        double distance =
            mc.player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

        return distance <= maxDistance;
    }

    // ============================================================
    // STOP MINING
    // ============================================================

    private void stopMining() {

        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }

        miningPos = null;

        // Reset render completely.
        renderTicks = 0;
        lastRenderPos = null;
    }

    // ============================================================
    // RENDER
    // ============================================================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) {
            return;
        }

        if (
            mc.world == null
            || miningPos == null
        ) {
            return;
        }

        if (!isBedrock(miningPos)) {
            return;
        }

        if (
            miningPos.getY()
                <= mc.world.getBottomY()
        ) {
            return;
        }

        // --------------------------------------------------------
        // RESET IF TARGET CHANGED
        // --------------------------------------------------------

        if (
            lastRenderPos == null
            || !lastRenderPos.equals(miningPos)
        ) {

            lastRenderPos =
                miningPos.toImmutable();

            renderTicks = 0;
        }

        // --------------------------------------------------------
        // GROWING ANIMATION
        // --------------------------------------------------------

        double progress =
            (double) renderTicks
                / renderGrowTicks.get();

        progress =
            Math.max(
                0.0,
                Math.min(
                    1.0,
                    progress
                )
            );

        double minSize = 0.05;

        double size =
            minSize
                + (1.0 - minSize)
                * progress;

        // --------------------------------------------------------
        // CENTER
        // --------------------------------------------------------

        double centerX =
            miningPos.getX() + 0.5;

        double centerY =
            miningPos.getY() + 0.5;

        double centerZ =
            miningPos.getZ() + 0.5;

        double half =
            size / 2.0;

        double minX =
            centerX - half;

        double minY =
            centerY - half;

        double minZ =
            centerZ - half;

        double maxX =
            centerX + half;

        double maxY =
            centerY + half;

        double maxZ =
            centerZ + half;

        event.renderer.box(
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    // ============================================================
    // SORT MODES
    // ============================================================

    public enum SortMode {
        Closest,
        Furthest,
        TopDown
    }

    // ============================================================
    // MINING MODES
    // ============================================================

    public enum MiningMode {
        All,
        Flatten
    }
}