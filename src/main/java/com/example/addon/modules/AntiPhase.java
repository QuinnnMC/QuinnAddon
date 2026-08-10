package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AntiPhase extends Module {
    private final SettingGroup sgGeneral =
        settings.getDefaultGroup();

    private final SettingGroup sgTarget =
        settings.createGroup("Targeting");

    private final Setting<List<Block>> materials =
        sgGeneral.add(
            new BlockListSetting.Builder()
                .name("material")
                .description("Blocks to use for AntiPhase.")
                .defaultValue(
                    Blocks.LADDER,
                    Blocks.VINE,
                    Blocks.SCAFFOLDING,
                    Blocks.DARK_OAK_BUTTON
                )
                .build()
        );

    private final Setting<Integer> bpt =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("blocks-per-tick")
                .description("Blocks per tick to place.")
                .defaultValue(2)
                .min(1)
                .max(6)
                .sliderMax(6)
                .build()
        );

    private final Setting<Integer> delay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("delay")
                .description("Delay between placements in milliseconds.")
                .defaultValue(50)
                .min(0)
                .sliderMax(500)
                .build()
        );

    private final Setting<Boolean> rotate =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("rotate")
                .description("Rotate towards the block when placing.")
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> pauseOnEat =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("pause-on-eat")
                .description("Pause placement when using an item.")
                .defaultValue(false)
                .build()
        );

    private final Setting<Double> range =
        sgTarget.add(
            new DoubleSetting.Builder()
                .name("range")
                .description("Maximum target range for AntiPhase.")
                .defaultValue(4.5)
                .min(1.0)
                .sliderMin(1.0)
                .sliderMax(12.0)
                .build()
        );

    private final Setting<Boolean> ignoreFriends =
        sgTarget.add(
            new BoolSetting.Builder()
                .name("ignore-friends")
                .description("Do not target players on your friends list.")
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> ignoreNaked =
        sgTarget.add(
            new BoolSetting.Builder()
                .name("ignore-naked")
                .description("Ignore players with no armor equipped.")
                .defaultValue(false)
                .build()
        );

    private long lastPlaceTime = 0;

    public AntiPhase() {
        super(
            QuinnAddon.CATEGORY,
            "antiphase",
            "Prevents your targets from phasing into blocks."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (pauseOnEat.get() && mc.player.isUsingItem()) {
            return;
        }

        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) {
            return;
        }

        List<PlayerEntity> targets = findTargets();

        if (targets.isEmpty()) {
            return;
        }

        targets.sort(
            Comparator.comparingDouble(
                player -> mc.player.squaredDistanceTo(player)
            )
        );

        FindItemResult blockItem =
            InvUtils.findInHotbar(stack -> {
                if (!(stack.getItem() instanceof BlockItem block)) {
                    return false;
                }

                return materials.get().contains(block.getBlock());
            });

        if (!blockItem.found()) {
            return;
        }

        int placed = 0;

        for (PlayerEntity target : targets) {
            if (placed >= bpt.get()) {
                break;
            }

            BlockPos targetPos =
                BlockPos.ofFloored(
                    target.getX(),
                    target.getY(),
                    target.getZ()
                );

            if (targetPos.equals(
                BlockPos.ofFloored(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
                )
            )) {
                continue;
            }

            Block blockAtPos =
                mc.world
                    .getBlockState(targetPos)
                    .getBlock();

            if (materials.get().contains(blockAtPos)) {
                continue;
            }

            if (!mc.world
                .getBlockState(targetPos)
                .isReplaceable()) {

                continue;
            }

            boolean success =
                BlockUtils.place(
                    targetPos,
                    blockItem,
                    rotate.get() ? 50 : 0,
                    false
                );

            if (success) {
                placed++;
                lastPlaceTime = System.currentTimeMillis();
            }
        }
    }

    private List<PlayerEntity> findTargets() {
        List<PlayerEntity> list =
            new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            if (!player.isAlive()) {
                continue;
            }

            if (ignoreFriends.get()
                && Friends.get().isFriend(player)) {

                continue;
            }

            if (ignoreNaked.get()
                && isNaked(player)) {

                continue;
            }

            if (mc.player.distanceTo(player) > range.get()) {
                continue;
            }

            list.add(player);
        }

        return list;
    }

    private boolean isNaked(PlayerEntity player) {
        return isEmpty(
            player.getEquippedStack(EquipmentSlot.HEAD)
        )
        && isEmpty(
            player.getEquippedStack(EquipmentSlot.CHEST)
        )
        && isEmpty(
            player.getEquippedStack(EquipmentSlot.LEGS)
        )
        && isEmpty(
            player.getEquippedStack(EquipmentSlot.FEET)
        );
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}