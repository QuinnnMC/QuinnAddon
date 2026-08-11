package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Delay between web placement attempts in milliseconds.")
            .defaultValue(50)
            .min(0)
            .sliderMax(500)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the placement position.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pauseUsingItem = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-using-item")
            .description("Pause AutoWeb while using an item.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> doubles = sgGeneral.add(
        new BoolSetting.Builder()
            .name("doubles")
            .description("Attempts to place a second web above the target.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(
        new BoolSetting.Builder()
            .name("predict-movement")
            .description("Predicts target movement.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> prediction = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("prediction")
            .description("Amount of movement prediction.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(3.0)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(
        new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Do not target players on your friends list.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> range = sgTarget.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance to target players.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMin(1.0)
            .sliderMax(8.0)
            .build()
    );

    private long lastPlaceTime;

    private BlockPos lastPlacedPos;
    private long lastPlacedRenderTime;

    public AutoWeb() {
        super(
            QuinnAddon.CATEGORY,
            "auto-web",
            "Automatically places cobwebs at the feet of nearby enemy players."
        );
    }

    @Override
    public void onActivate() {
        lastPlaceTime = 0;
        lastPlacedPos = null;
        lastPlacedRenderTime = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (pauseUsingItem.get() && mc.player.isUsingItem()) {
            return;
        }

        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) {
            return;
        }

        PlayerEntity target = findTarget();

        if (target == null) {
            return;
        }

        /*
         * AutoWeb ONLY searches for cobwebs.
         */
        FindItemResult web = InvUtils.findInHotbar(
            Items.COBWEB
        );

        if (!web.found()) {
            return;
        }

        /*
         * Always target the player's feet.
         */
        BlockPos targetPos;

        if (predictMovement.get()) {
            targetPos = getPredictedPosition(target);
        } else {
            targetPos = getTargetPosition(target);
        }

        if (!canPlace(targetPos)) {
            return;
        }

        boolean firstSuccess = BlockUtils.place(
            targetPos,
            web,
            rotate.get() ? 50 : 0,
            false
        );

        if (!firstSuccess) {
            return;
        }

        lastPlacedPos = targetPos;
        lastPlacedRenderTime = System.currentTimeMillis();

        if (doubles.get()) {
            BlockPos secondPos = targetPos.up();

            if (mc.world.getBlockState(secondPos).isReplaceable()) {
                boolean secondSuccess = BlockUtils.place(
                    secondPos,
                    web,
                    rotate.get() ? 50 : 0,
                    false
                );

                if (secondSuccess) {
                    lastPlacedPos = secondPos;
                    lastPlacedRenderTime =
                        System.currentTimeMillis();
                }
            }
        }

        lastPlaceTime = System.currentTimeMillis();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (lastPlacedPos == null) {
            return;
        }

        long elapsed =
            System.currentTimeMillis()
                - lastPlacedRenderTime;

        if (elapsed >= 1000) {
            lastPlacedPos = null;
            return;
        }

        double progress =
            elapsed / 1000.0;

        int alpha =
            (int) (255 * (1.0 - progress));

        Color sideColor = new Color(
            255,
            255,
            255,
            alpha / 3
        );

        Color lineColor = new Color(
            255,
            255,
            255,
            alpha
        );

        event.renderer.box(
            lastPlacedPos,
            sideColor,
            lineColor,
            ShapeMode.Both,
            0
        );
    }

    private PlayerEntity findTarget() {
        List<PlayerEntity> targets =
            new ArrayList<>();

        for (PlayerEntity player :
            mc.world.getPlayers()) {

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

            if (mc.player.distanceTo(player)
                > range.get()) {
                continue;
            }

            targets.add(player);
        }

        if (targets.isEmpty()) {
            return null;
        }

        targets.sort(
            Comparator.comparingDouble(
                player ->
                    mc.player.squaredDistanceTo(player)
            )
        );

        return targets.get(0);
    }

    private BlockPos getTargetPosition(
        PlayerEntity target
    ) {
        return BlockPos.ofFloored(
            target.getX(),
            target.getY(),
            target.getZ()
        );
    }

    private BlockPos getPredictedPosition(
        PlayerEntity target
    ) {
        Vec3d velocity =
            target.getVelocity();

        double x =
            target.getX()
                + velocity.x * prediction.get();

        double y =
            target.getY()
                + velocity.y * prediction.get();

        double z =
            target.getZ()
                + velocity.z * prediction.get();

        return BlockPos.ofFloored(
            x,
            y,
            z
        );
    }

    private boolean canPlace(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        if (!mc.world
            .getBlockState(pos)
            .isReplaceable()) {

            return false;
        }

        BlockPos below =
            pos.down();

        return mc.world
            .getBlockState(below)
            .isSolidBlock(
                mc.world,
                below
            );
    }
}
