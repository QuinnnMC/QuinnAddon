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

import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BedrockNuker extends Module {
private final SettingGroup sgGeneral = settings.getDefaultGroup();
private final SettingGroup sgRender = settings.createGroup("Render");

private final Setting<Double> range = sgGeneral.add(
    new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to search for bedrock.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderMin(1.0)
        .sliderMax(12.0)
        .build()
);

private final Setting<Boolean> flatten = sgGeneral.add(
    new BoolSetting.Builder()
        .name("flatten")
        .description("Only targets bedrock at your current Y level and above.")
        .defaultValue(false)
        .build()
);

private final Setting<Integer> delay = sgGeneral.add(
    new IntSetting.Builder()
        .name("delay")
        .description("Delay before selecting another bedrock block.")
        .defaultValue(0)
        .min(0)
        .sliderMax(500)
        .build()
);

private final Setting<Boolean> rotate = sgGeneral.add(
    new BoolSetting.Builder()
        .name("rotate")
        .description("Use the face closest to the player when mining.")
        .defaultValue(false)
        .build()
);

private final Setting<Boolean> render = sgRender.add(
    new BoolSetting.Builder()
        .name("render")
        .description("Render a selection box around the block being mined.")
        .defaultValue(true)
        .build()
);

private final Setting<SettingColor> sideColor = sgRender.add(
    new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the selection box sides.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
);

private final Setting<SettingColor> lineColor = sgRender.add(
    new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the selection box outline.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
);

private final Setting<ShapeMode> shapeMode = sgRender.add(
    new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the target selection box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
);

private BlockPos target;
private long lastTargetTime;

public BedrockNuker() {
    super(
        QuinnAddon.CATEGORY,
        "bedrock-nuker",
        "Mines one bedrock block at a time using normal block breaking."
    );
}

@Override
public void onActivate() {
    target = null;
    lastTargetTime = 0;
}

@Override
public void onDeactivate() {
    target = null;
}

@EventHandler
private void onTick(TickEvent.Pre event) {
    if (mc.player == null
        || mc.world == null
        || mc.interactionManager == null
        || mc.getNetworkHandler() == null) {

        target = null;
        return;
    }

    if (target != null && !isBedrock(target)) {
        target = null;
    }

    if (target == null) {
        if (System.currentTimeMillis() - lastTargetTime < delay.get()) {
            return;
        }

        target = findNearestBedrock();

        if (target == null) {
            return;
        }

        target = target.toImmutable();
        lastTargetTime = System.currentTimeMillis();

        sendStartMiningPacket(target);
    }

    if (!isBedrock(target)) {
        target = null;
        return;
    }

    Direction direction = getBestDirection(target);

    mc.interactionManager.attackBlock(
        target,
        direction
    );

    mc.player.swingHand(Hand.MAIN_HAND);
}

@EventHandler
private void onRender(Render3DEvent event) {
    if (!render.get()) {
        return;
    }

    if (target == null) {
        return;
    }

    if (mc.world == null || !isBedrock(target)) {
        return;
    }

    event.renderer.box(
        target,
        sideColor.get(),
        lineColor.get(),
        shapeMode.get(),
        0
    );
}

private void sendStartMiningPacket(BlockPos pos) {
    if (mc.getNetworkHandler() == null) {
        return;
    }

    Direction direction = getBestDirection(pos);

    mc.getNetworkHandler().sendPacket(
        new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            pos,
            direction
        )
    );
}

private BlockPos findNearestBedrock() {
    if (mc.player == null || mc.world == null) {
        return null;
    }

    BlockPos playerPos = mc.player.getBlockPos();

    int radius = (int) Math.ceil(range.get());
    double maxDistance = range.get() * range.get();

    BlockPos best = null;
    double bestDistance = Double.MAX_VALUE;

    for (int x = -radius; x <= radius; x++) {
        for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {

                BlockPos pos = playerPos.add(
                    x,
                    y,
                    z
                );

                if (flatten.get()
                    && pos.getY() < playerPos.getY()) {
                    continue;
                }

                if (!isBedrock(pos)) {
                    continue;
                }

                double dx =
                    mc.player.getX()
                        - (pos.getX() + 0.5);

                double dy =
                    mc.player.getEyeY()
                        - (pos.getY() + 0.5);

                double dz =
                    mc.player.getZ()
                        - (pos.getZ() + 0.5);

                double distance =
                    dx * dx
                        + dy * dy
                        + dz * dz;

                if (distance > maxDistance) {
                    continue;
                }

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.toImmutable();
                }
            }
        }
    }

    return best;
}

private boolean isBedrock(BlockPos pos) {
    if (mc.world == null || pos == null) {
        return false;
    }

    return mc.world
        .getBlockState(pos)
        .isOf(Blocks.BEDROCK);
}

private Direction getBestDirection(BlockPos pos) {
    if (mc.player == null || !rotate.get()) {
        return Direction.UP;
    }

    double x =
        mc.player.getX()
            - (pos.getX() + 0.5);

    double y =
        mc.player.getEyeY()
            - (pos.getY() + 0.5);

    double z =
        mc.player.getZ()
            - (pos.getZ() + 0.5);

    double absX = Math.abs(x);
    double absY = Math.abs(y);
    double absZ = Math.abs(z);

    if (absX >= absY && absX >= absZ) {
        return x > 0
            ? Direction.EAST
            : Direction.WEST;
    }

    if (absY >= absX && absY >= absZ) {
        return y > 0
            ? Direction.UP
            : Direction.DOWN;
    }

    return z > 0
        ? Direction.SOUTH
        : Direction.NORTH;
}

public double range() {
    return range.get();
}

public boolean flatten() {
    return flatten.get();
}

public BlockPos target() {
    return target;
}

}
