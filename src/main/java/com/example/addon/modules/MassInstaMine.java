package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MassInstaMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<ListMode> listMode = sgGeneral.add(
        new EnumSetting.Builder<ListMode>()
            .name("list-mode")
            .description("Whether to blacklist or whitelist blocks.")
            .defaultValue(ListMode.Blacklist)
            .build()
    );

    private final Setting<List<Block>> blocksToSkip = sgGeneral.add(
        new BlockListSetting.Builder()
            .name("blocks-to-skip")
            .description("Skips instamining these blocks.")
            .build()
    );

    private final Setting<List<Block>> blocksToBreakList = sgGeneral.add(
        new BlockListSetting.Builder()
            .name("blocks-to-break")
            .description("Only instamines these blocks in whitelist mode.")
            .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(
        new IntSetting.Builder()
            .name("mine-radius")
            .description("Radius around the player to mine blocks.")
            .defaultValue(4)
            .min(1)
            .max(6)
            .sliderRange(1, 6)
            .build()
    );

    private final Setting<Integer> height = sgGeneral.add(
        new IntSetting.Builder()
            .name("mine-height")
            .description("Height range above and below the player.")
            .defaultValue(2)
            .min(1)
            .max(6)
            .sliderRange(1, 6)
            .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing-hand")
            .description("Swings the hand when instamining.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates toward the mined block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> dontMineBelowFeet = sgGeneral.add(
        new BoolSetting.Builder()
            .name("dont-mine-below-feet")
            .description("Prevents mining blocks below your feet.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Renders blocks being mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .defaultValue(new SettingColor(225, 25, 25, 45))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .defaultValue(new SettingColor(225, 25, 25, 255))
            .build()
    );

    private final List<BlockPos> blocksToBreak = new ArrayList<>();

    private Direction direction = Direction.UP;
    private boolean shouldMine;
    private boolean hasSentBurst;
    private int mineTimer;
    private BlockPos lastMinedPos;

    public MassInstaMine() {
        super(
            QuinnAddon.CATEGORY,
            "mass-insta-mine",
            "Mines nearby blocks when you start mining a block. Credits to H_ux, Discord h.u.x."
        );
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        blocksToBreak.clear();

        direction = Direction.UP;
        shouldMine = false;
        hasSentBurst = false;
        mineTimer = 0;
        lastMinedPos = null;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;

        if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;

        if (packet.getAction() != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();

        if (!isTool(stack)) return;

        direction = packet.getDirection();
        lastMinedPos = packet.getPos();

        if (!shouldMine) {
            shouldMine = true;
            hasSentBurst = false;
            mineTimer = 0;

            findBlocksToMine();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        if (!shouldMine) return;

        blocksToBreak.removeIf(pos ->
            mc.world.getBlockState(pos).isAir()
                || !shouldBreak(pos)
        );

        if (blocksToBreak.isEmpty()) {
            shouldMine = false;
            hasSentBurst = false;
            return;
        }

        if (!hasSentBurst) {
            hasSentBurst = true;

            sendBreakPackets();

            mineTimer = 5;
        }

        if (mineTimer > 0) {
            mineTimer--;

            if (mineTimer == 0) {
                shouldMine = false;
                hasSentBurst = false;
                blocksToBreak.clear();
            }
        }
    }

    private void findBlocksToMine() {
        blocksToBreak.clear();

        if (mc.player == null || mc.world == null) return;

        BlockPos center = mc.player.getBlockPos();

        int r = radius.get();
        int h = height.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -h; y <= h; y++) {
                for (int z = -r; z <= r; z++) {

                    BlockPos pos = center.add(x, y, z);

                    if (dontMineBelowFeet.get()
                        && pos.getY() < center.getY()) {
                        continue;
                    }

                    if (!shouldBreak(pos)) continue;

                    blocksToBreak.add(pos);
                }
            }
        }

        blocksToBreak.sort(
            Comparator.comparingDouble(
                pos -> pos.getSquaredDistance(center)
            )
        );
    }

    private boolean shouldBreak(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir()) return false;

        if (!BlockUtils.canBreak(pos)) return false;

        Block block = state.getBlock();

        if (listMode.get() == ListMode.Blacklist) {
            return !blocksToSkip.get().contains(block);
        }

        return blocksToBreakList.get().contains(block);
    }

    private void sendBreakPackets() {
        if (mc.getNetworkHandler() == null) return;

        Direction dir = direction == null
            ? Direction.UP
            : direction;

        for (BlockPos pos : blocksToBreak) {
            if (mc.world == null) return;

            if (mc.world.getBlockState(pos).isAir()) {
                continue;
            }

            if (!shouldBreak(pos)) {
                continue;
            }

            if (rotate.get()) {
                Rotations.rotate(
                    Rotations.getYaw(pos),
                    Rotations.getPitch(pos)
                );
            }

            mc.getNetworkHandler().sendPacket(
                new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    dir
                )
            );

            if (swing.get()) {
                mc.getNetworkHandler().sendPacket(
                    new HandSwingC2SPacket(Hand.MAIN_HAND)
                );

                if (mc.player != null) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }

            mc.getNetworkHandler().sendPacket(
                new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    dir
                )
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : blocksToBreak) {
            event.renderer.box(
                pos,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return stack.isOf(Items.WOODEN_PICKAXE)
            || stack.isOf(Items.STONE_PICKAXE)
            || stack.isOf(Items.IRON_PICKAXE)
            || stack.isOf(Items.GOLDEN_PICKAXE)
            || stack.isOf(Items.DIAMOND_PICKAXE)
            || stack.isOf(Items.NETHERITE_PICKAXE)

            || stack.isOf(Items.WOODEN_AXE)
            || stack.isOf(Items.STONE_AXE)
            || stack.isOf(Items.IRON_AXE)
            || stack.isOf(Items.GOLDEN_AXE)
            || stack.isOf(Items.DIAMOND_AXE)
            || stack.isOf(Items.NETHERITE_AXE)

            || stack.isOf(Items.WOODEN_SHOVEL)
            || stack.isOf(Items.STONE_SHOVEL)
            || stack.isOf(Items.IRON_SHOVEL)
            || stack.isOf(Items.GOLDEN_SHOVEL)
            || stack.isOf(Items.DIAMOND_SHOVEL)
            || stack.isOf(Items.NETHERITE_SHOVEL)

            || stack.isOf(Items.SHEARS);
    }

    public enum ListMode {
        Blacklist,
        Whitelist
    }
}
