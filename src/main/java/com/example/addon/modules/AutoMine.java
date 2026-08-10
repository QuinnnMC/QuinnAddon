package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoMine extends Module {

    public AutoMine() {
        super(
            QuinnAddon.CATEGORY,
            "auto-mine",
            "Automatically mines blocks around enemy players."
        );
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgExplode = settings.createGroup("Explode");
    private final SettingGroup sgCev = settings.createGroup("Cev");
    private final SettingGroup sgAntiSurround = settings.createGroup("Anti Surround");
    private final SettingGroup sgAntiBurrow = settings.createGroup("Anti Burrow");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ==================== General ====================

    private final Setting<Boolean> pauseEat = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-eat")
            .description("Doesn't mine while using an item.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> pauseSword = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-sword")
            .description("Doesn't mine while holding a sword.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SwitchMode> pickAxeSwitchMode = sgGeneral.add(
        new EnumSetting.Builder<SwitchMode>()
            .name("pickaxe-switch-mode")
            .description("Method of switching to the mining tool.")
            .defaultValue(SwitchMode.Silent)
            .build()
    );

    private final Setting<SwitchMode> crystalSwitchMode = sgGeneral.add(
        new EnumSetting.Builder<SwitchMode>()
            .name("crystal-switch-mode")
            .description("Method of switching to crystals.")
            .defaultValue(SwitchMode.Silent)
            .build()
    );

    private final Setting<Boolean> autoMine = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-mine")
            .description("Automatically selects blocks to mine.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> manualMine = sgGeneral.add(
        new BoolSetting.Builder()
            .name("manual-mine")
            .description("Allows manually selected blocks to be mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> manualInsta = sgGeneral.add(
        new BoolSetting.Builder()
            .name("manual-instant")
            .description("Uses instant mining when manually mining.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> manualRemine = sgGeneral.add(
        new BoolSetting.Builder()
            .name("manual-remine")
            .description("Mines the manually selected block again.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> fastRemine = sgGeneral.add(
        new BoolSetting.Builder()
            .name("fast-remine")
            .description("Calculates mining progress from the previous block.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> manualRangeReset = sgGeneral.add(
        new BoolSetting.Builder()
            .name("manual-range-reset")
            .description("Resets manual mining when out of range.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> resetOnSwitch = sgGeneral.add(
        new BoolSetting.Builder()
            .name("reset-on-switch")
            .description("Resets mining when switching held items.")
            .defaultValue(false)
            .build()
    );

    // ==================== Speed ====================

    private final Setting<Double> speed = sgSpeed.add(
        new DoubleSetting.Builder()
            .name("speed")
            .description("Mining speed multiplier.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 2)
            .build()
    );

    private final Setting<Double> instaDelay = sgSpeed.add(
        new DoubleSetting.Builder()
            .name("instant-delay")
            .description("Delay between instant mines.")
            .defaultValue(0.5)
            .min(0)
            .sliderRange(0, 1)
            .build()
    );

    private final Setting<Boolean> onGroundCheck = sgSpeed.add(
        new BoolSetting.Builder()
            .name("on-ground-check")
            .description("Mining is slower while airborne.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> effectCheck = sgSpeed.add(
        new BoolSetting.Builder()
            .name("effect-check")
            .description("Accounts for haste and mining fatigue.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> waterCheck = sgSpeed.add(
        new BoolSetting.Builder()
            .name("water-check")
            .description("Mining is slower while underwater.")
            .defaultValue(true)
            .build()
    );

    // ==================== Explode ====================

    private final Setting<Double> explodeSpeed = sgExplode.add(
        new DoubleSetting.Builder()
            .name("explode-speed")
            .description("How many times per second to attack crystals.")
            .defaultValue(2)
            .min(0)
            .sliderRange(0, 10)
            .build()
    );

    private final Setting<Double> explodeTime = sgExplode.add(
        new DoubleSetting.Builder()
            .name("explode-time")
            .description("How long to try attacking a crystal.")
            .defaultValue(2)
            .min(0)
            .sliderRange(0, 10)
            .build()
    );

    // ==================== Cev ====================

    private final Setting<Priority> cevPriority = sgCev.add(
        new EnumSetting.Builder<Priority>()
            .name("cev-priority")
            .description("Priority of CEV mining.")
            .defaultValue(Priority.Normal)
            .build()
    );

    private final Setting<Boolean> instaCev = sgCev.add(
        new BoolSetting.Builder()
            .name("instant-cev")
            .description("Allows instant CEV remine.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Priority> trapCevPriority = sgCev.add(
        new EnumSetting.Builder<Priority>()
            .name("trap-cev-priority")
            .description("Priority of trap CEV mining.")
            .defaultValue(Priority.Normal)
            .build()
    );

    private final Setting<Boolean> instaTrapCev = sgCev.add(
        new BoolSetting.Builder()
            .name("instant-trap-cev")
            .description("Allows instant trap CEV remine.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Priority> surroundCevPriority = sgCev.add(
        new EnumSetting.Builder<Priority>()
            .name("surround-cev-priority")
            .description("Priority of surround CEV mining.")
            .defaultValue(Priority.Normal)
            .build()
    );

    private final Setting<Boolean> instaSurroundCev = sgCev.add(
        new BoolSetting.Builder()
            .name("instant-surround-cev")
            .description("Allows instant surround CEV remine.")
            .defaultValue(false)
            .build()
    );

    // ==================== Anti Surround ====================

    private final Setting<Priority> surroundMinerPriority = sgAntiSurround.add(
        new EnumSetting.Builder<Priority>()
            .name("surround-miner-priority")
            .description("Priority of surround mining.")
            .defaultValue(Priority.Normal)
            .build()
    );

    private final Setting<Boolean> instaSurroundMiner = sgAntiSurround.add(
        new BoolSetting.Builder()
            .name("instant-surround-miner")
            .description("Allows instant surround remine.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Priority> autoCityPriority = sgAntiSurround.add(
        new EnumSetting.Builder<Priority>()
            .name("auto-city-priority")
            .description("Priority of Auto City.")
            .defaultValue(Priority.Normal)
            .build()
    );

    private final Setting<Boolean> instaAutoCity = sgAntiSurround.add(
        new BoolSetting.Builder()
            .name("instant-auto-city")
            .description("Allows instant Auto City remine.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> explodeCrystal = sgAntiSurround.add(
        new BoolSetting.Builder()
            .name("explode-crystal")
            .description("Attacks the crystal placed by Auto City.")
            .defaultValue(false)
            .build()
    );

    // ==================== Anti Burrow ====================

    private final Setting<Priority> antiBurrowPriority = sgAntiBurrow.add(
        new EnumSetting.Builder<Priority>()
            .name("anti-burrow-priority")
            .description("Priority of anti-burrow.")
            .defaultValue(Priority.Normal)
            .build()
    );

    // ==================== Render ====================

    private final Setting<Boolean> renderTarget = sgRender.add(
        new BoolSetting.Builder()
            .name("render-target")
            .description("Renders the block currently being mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Which parts of the target box are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Target box side color.")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Target box line color.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    // ==================== State ====================

    private double minedFor = 0;
    private Target target = null;
    private boolean started = false;
    private BlockPos civPos = null;

    private List<AbstractClientPlayerEntity> enemies = new ArrayList<>();

    private long lastTime = 0;
    private long lastPlace = 0;
    private long lastExplode = 0;
    private long lastCiv = 0;

    private double delta = 0;

    private final Map<BlockPos, Long> explodeAt = new HashMap<>();

    private boolean reset = false;
    private boolean mined = false;

    private BlockState lastState = null;
    private BlockPos lastPos = null;

    @Override
    public void onActivate() {
        target = null;
        minedFor = 0;
        started = false;
        lastTime = System.currentTimeMillis();
        civPos = null;
        reset = false;
        mined = false;
        explodeAt.clear();
    }

    @Override
    public void onDeactivate() {
        target = null;
        started = false;
        minedFor = 0;
        civPos = null;
        explodeAt.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSend(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket
            && resetOnSwitch.get()) {

            reset = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (target != null) {
            if (lastState != null
                && target.pos.equals(lastPos)
                && target.manual
                && manualRemine.get()
                && !fastRemine.get()
                && lastState.isAir()
                && solid2(target.pos)) {

                started = false;
            }

            lastPos = target.pos;
            lastState = mc.world.getBlockState(target.pos);
        } else {
            lastPos = null;
            lastState = null;
        }

        long now = System.currentTimeMillis();

        if (lastTime == 0) {
            lastTime = now;
        }

        delta = (now - lastTime) / 1000d;
        lastTime = now;

        update();
        explodeUpdate();
        render(event.renderer);
    }

    // ==================== Networking ====================

    private void sendPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    private void sendAction(
        PlayerActionC2SPacket.Action action,
        BlockPos pos,
        Direction direction
    ) {
        if (mc.getNetworkHandler() == null) {
            return;
        }

        mc.getNetworkHandler().sendPacket(
            new PlayerActionC2SPacket(
                action,
                pos,
                direction
            )
        );
    }

    // ==================== Explosion ====================

    private void explodeUpdate() {
        if (explodeSpeed.get() <= 0) {
            return;
        }

        Entity targetCrystal = null;

        List<BlockPos> toRemove = new ArrayList<>();

        long now = System.currentTimeMillis();

        for (Map.Entry<BlockPos, Long> entry : explodeAt.entrySet()) {
            if (now - entry.getValue()
                > explodeTime.get() * 1000) {

                toRemove.add(entry.getKey());
                continue;
            }

            EndCrystalEntity crystal = crystalAt(entry.getKey());

            if (crystal != null) {
                targetCrystal = crystal;
                break;
            }
        }

        toRemove.forEach(explodeAt::remove);

        if (targetCrystal != null
            && !isPaused()
            && mined
            && now - lastExplode
                > (1000 / explodeSpeed.get())) {

            sendPacket(
                PlayerInteractEntityC2SPacket.attack(
                    targetCrystal,
                    mc.player.isSneaking()
                )
            );

            lastExplode = now;
        }
    }

    public double getMineProgress() {
        if (target == null) {
            return -1;
        }

        int slot = fastestSlot();

        if (slot == -1) {
            return 0;
        }

        float ticks = getMineTicks(slot, true);

        if (ticks <= 0) {
            return 0;
        }

        return minedFor / ticks;
    }

    // ==================== Render ====================

    private void render(Renderer3D r) {
        if (!renderTarget.get() || target == null) {
            return;
        }

        int slot = fastestSlot();

        if (slot == -1) {
            return;
        }

        float ticks = getMineTicks(slot, true);

        if (ticks <= 0) {
            return;
        }

        double p = MathHelper.clamp(minedFor / ticks, 0, 1);

        Box box = new Box(
            target.pos.getX() + 0.5 - p / 2,
            target.pos.getY() + 0.5 - p / 2,
            target.pos.getZ() + 0.5 - p / 2,
            target.pos.getX() + 0.5 + p / 2,
            target.pos.getY() + 0.5 + p / 2,
            target.pos.getZ() + 0.5 + p / 2
        );

        r.box(
            box,
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    // ==================== Main Update ====================

    private void update() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        if (reset) {
            if (target != null && !target.manual) {
                target = null;
            }

            started = false;
            reset = false;
        }

        enemies = mc.world.getPlayers()
            .stream()
            .filter(player ->
                player != mc.player
                    && !Friends.get().isFriend(player)
                    && player.distanceTo(mc.player) < 10
            )
            .map(player -> (AbstractClientPlayerEntity) player)
            .toList();

        BlockPos previousTarget =
            target == null || target.pos == null
                ? null
                : target.pos;

        if (target != null
            && target.manual
            && manualRangeReset.get()
            && !inMineRange(target.pos)) {

            target = null;
            started = false;
        }

        if (target == null || !target.manual) {
            target = getTarget();
        }

        if (target == null) {
            return;
        }

        if (target.pos != null
            && !target.pos.equals(previousTarget)) {

            if (started) {
                sendAction(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    target.pos,
                    Direction.DOWN
                );
            }

            started = false;
        }

        if (!started) {
            started = true;
            minedFor = 0;
            civPos = null;

            Direction dir = getPlaceOnDirection(target.pos);

            sendAction(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.pos,
                dir == null ? Direction.UP : dir
            );

            mined = false;
        }

        if (!started) {
            return;
        }

        minedFor += delta * 20;

        if (isPaused()) {
            return;
        }

        if (!miningCheck(fastestSlot())) {
            return;
        }

        if (!civCheck()) {
            return;
        }

        if (!crystalCheck()) {
            return;
        }

        if (!solid2(target.pos)) {
            return;
        }

        endMine();
    }

    private boolean isPaused() {
        if (pauseEat.get() && mc.player.isUsingItem()) {
            return true;
        }

        if (pauseSword.get()) {
            ItemStack stack = mc.player.getMainHandStack();

            if (stack.isIn(ItemTags.SWORDS)) {
                return true;
            }
        }

        return false;
    }

    private boolean civCheck() {
        if (civPos == null) {
            return true;
        }

        return System.currentTimeMillis() - lastCiv
            >= instaDelay.get() * 1000;
    }

    // ==================== Mining ====================

    private void endMine() {
        int slot = fastestSlot();

        if (slot == -1) {
            return;
        }

        Direction dir = getPlaceOnDirection(target.pos);

        if (dir == null) {
            return;
        }

        boolean switched = miningCheck(slot);
        boolean swapBack = false;

        if (!switched) {
            switch (pickAxeSwitchMode.get()) {
                case Silent, InvSwitch, PickSilent -> {
                    switched = true;
                    InvUtils.swap(slot, true);
                    swapBack = true;
                }
            }
        }

        if (!switched) {
            return;
        }

        sendAction(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            target.pos,
            dir
        );

        mined = true;

        if (target.civ) {
            civPos = target.pos;
            lastCiv = System.currentTimeMillis();
        } else if (target.manual && manualRemine.get()) {
            minedFor = 0;
        } else {
            target = null;
            minedFor = 0;
        }

        started = false;

        if (swapBack) {
            InvUtils.swapBack();
        }
    }

    // ==================== Crystal ====================

    private boolean crystalCheck() {
        switch (target.type) {
            case Cev, TrapCev, SurroundCev -> {
                if (crystalAt(target.crystalPos) != null) {
                    return true;
                }

                if (!EntityUtils.intersectsWithEntity(
                    Box.from(new BlockBox(target.crystalPos))
                        .withMaxY(target.crystalPos.getY() + 1),
                    entity -> !entity.isSpectator()
                )) {
                    placeCrystal();
                    return false;
                }
            }

            case AutoCity -> {
                if (crystalAt(target.crystalPos) != null) {
                    return true;
                }

                if (!EntityUtils.intersectsWithEntity(
                    Box.from(new BlockBox(target.crystalPos))
                        .withMaxY(target.crystalPos.getY() + 1),
                    entity -> !entity.isSpectator()
                )) {
                    return placeCrystal();
                }
            }

            default -> {
                return true;
            }
        }

        return false;
    }

    private EndCrystalEntity crystalAt(BlockPos pos) {
        if (mc.world == null) {
            return null;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal
                && crystal.getBlockPos().equals(pos)) {

                return crystal;
            }
        }

        return null;
    }

    private boolean placeCrystal() {
        if (System.currentTimeMillis() - lastPlace < 250) {
            return false;
        }

        Hand hand = getHand();

        int crystalSlot = InvUtils.find(Items.END_CRYSTAL).slot();

        if (hand == null && crystalSlot < 0) {
            return false;
        }

        Direction dir = getPlaceOnDirection(target.crystalPos.down());

        if (dir == null) {
            return false;
        }

        boolean switched = hand != null;

        if (!switched) {
            switch (crystalSwitchMode.get()) {
                case Silent, PickSilent, InvSwitch -> {
                    switched = true;
                    InvUtils.swap(crystalSlot, true);
                }
            }
        }

        if (!switched) {
            return false;
        }

        Hand useHand = hand == null ? Hand.MAIN_HAND : hand;

        mc.interactionManager.interactBlock(
            mc.player,
            useHand,
            new BlockHitResult(
                target.crystalPos.down().toCenterPos(),
                dir,
                target.crystalPos.down(),
                false
            )
        );

        lastPlace = System.currentTimeMillis();

        if (shouldExplode()) {
            addExplode();
        }

        if (hand == null) {
            InvUtils.swapBack();
        }

        return true;
    }

    private void addExplode() {
        explodeAt.remove(target.crystalPos);
        explodeAt.put(target.crystalPos, System.currentTimeMillis());
    }

    private boolean shouldExplode() {
        return switch (target.type) {
            case Cev, SurroundCev, TrapCev -> true;
            case SurroundMiner, AntiBurrow, Manual -> false;
            case AutoCity -> explodeCrystal.get();
        };
    }

    // ==================== Target Selection ====================

    private Target getTarget() {
        Target best = null;

        if (!autoMine.get()) {
            return null;
        }

        if (priorityCheck(best, cevPriority.get())) {
            Target t = getCev();

            if (t != null) {
                best = t;
            }
        }

        if (priorityCheck(best, trapCevPriority.get())) {
            Target t = getTrapCev();

            if (t != null) {
                best = t;
            }
        }

        if (priorityCheck(best, surroundCevPriority.get())) {
            Target t = getSurroundCev();

            if (t != null) {
                best = t;
            }
        }

        if (priorityCheck(best, surroundMinerPriority.get())) {
            Target t = getSurroundMiner();

            if (t != null) {
                best = t;
            }
        }

        if (priorityCheck(best, autoCityPriority.get())) {
            Target t = getAutoCity();

            if (t != null) {
                best = t;
            }
        }

        if (priorityCheck(best, antiBurrowPriority.get())) {
            Target t = getAntiBurrow();

            if (t != null) {
                best = t;
            }
        }

        return best;
    }

    private Target getCev() {
        boolean civ = instaCev.get();

        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos pos = new BlockPos(
                player.getBlockX(),
                (int) Math.floor(player.getBoundingBox().maxY) + 1,
                player.getBlockZ()
            );

            Block block = getBlock(pos);

            if (!(civ && pos.equals(civPos))
                && block != Blocks.OBSIDIAN) {
                continue;
            }

            if ((civ && pos.equals(civPos))
                && !(block instanceof AirBlock)
                && block != Blocks.OBSIDIAN) {
                continue;
            }

            if (getBlock(pos.up()) != Blocks.AIR) {
                continue;
            }

            if (!inMineRange(pos)) {
                continue;
            }

            if (!inPlaceRange(pos)) {
                continue;
            }

            double d = mc.player.getEyePos()
                .distanceTo(Vec3d.ofCenter(pos));

            if (distanceCheck(civ, pos, distance, d)) {
                best = new Target(
                    pos,
                    pos.up(),
                    MineType.Cev,
                    cevPriority.get().priority
                        + (civ && pos.equals(civPos) ? 0.1 : 0),
                    civ,
                    false
                );

                distance = d;
            }
        }

        return best;
    }

    private Target getTrapCev() {
        boolean civ = instaTrapCev.get();

        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos base = new BlockPos(
                player.getBlockX(),
                (int) Math.floor(player.getBoundingBox().maxY),
                player.getBlockZ()
            );

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = base.offset(dir);

                Block block = getBlock(pos);

                if (!(civ && pos.equals(civPos))
                    && block != Blocks.OBSIDIAN) {
                    continue;
                }

                if ((civ && pos.equals(civPos))
                    && !(block instanceof AirBlock)
                    && block != Blocks.OBSIDIAN) {
                    continue;
                }

                if (getBlock(pos.up()) != Blocks.AIR) {
                    continue;
                }

                if (!inMineRange(pos)) {
                    continue;
                }

                if (!inPlaceRange(pos)) {
                    continue;
                }

                double d = mc.player.getEyePos()
                    .distanceTo(Vec3d.ofCenter(pos));

                if (distanceCheck(civ, pos, distance, d)) {
                    best = new Target(
                        pos,
                        pos.up(),
                        MineType.TrapCev,
                        trapCevPriority.get().priority
                            + (civ && pos.equals(civPos) ? 0.1 : 0),
                        civ,
                        false
                    );

                    distance = d;
                }
            }
        }

        return best;
    }

    private Target getSurroundCev() {
        boolean civ = instaSurroundCev.get();

        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos base = new BlockPos(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ()
            );

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = base.offset(dir);

                Block block = getBlock(pos);

                if (!(civ && pos.equals(civPos))
                    && block != Blocks.OBSIDIAN) {
                    continue;
                }

                if ((civ && pos.equals(civPos))
                    && !(block instanceof AirBlock)
                    && block != Blocks.OBSIDIAN) {
                    continue;
                }

                if (getBlock(pos.up()) != Blocks.AIR) {
                    continue;
                }

                if (!inMineRange(pos)) {
                    continue;
                }

                if (!inPlaceRange(pos)) {
                    continue;
                }

                double d = mc.player.getEyePos()
                    .distanceTo(Vec3d.ofCenter(pos));

                if (distanceCheck(civ, pos, distance, d)) {
                    best = new Target(
                        pos,
                        pos.up(),
                        MineType.SurroundCev,
                        surroundCevPriority.get().priority
                            + (civ && pos.equals(civPos) ? 0.1 : 0),
                        civ,
                        false
                    );

                    distance = d;
                }
            }
        }

        return best;
    }

    private Target getSurroundMiner() {
        boolean civ = instaSurroundMiner.get();

        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos base = new BlockPos(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ()
            );

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = base.offset(dir);

                if (((!civ || !pos.equals(civPos))
                    && !solid2(pos))
                    || getBlock(pos) == Blocks.BEDROCK) {
                    continue;
                }

                if (!inMineRange(pos)) {
                    continue;
                }

                double d = mc.player.getEyePos()
                    .distanceTo(Vec3d.ofCenter(pos));

                if (distanceCheck(civ, pos, distance, d)) {
                    best = new Target(
                        pos,
                        null,
                        MineType.SurroundMiner,
                        surroundMinerPriority.get().priority
                            + (civ && pos.equals(civPos) ? 0.1 : 0),
                        civ,
                        false
                    );

                    distance = d;
                }
            }
        }

        return best;
    }

    private Target getAutoCity() {
        boolean civ = instaAutoCity.get();

        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos base = new BlockPos(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ()
            );

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = base.offset(dir);

                if (((!civ || !pos.equals(civPos))
                    && !solid2(pos))
                    || getBlock(pos) == Blocks.BEDROCK) {
                    continue;
                }

                BlockPos crystalPos = pos.offset(dir);

                if (getBlock(crystalPos) != Blocks.AIR) {
                    continue;
                }

                if (!crystalBlock(crystalPos.down())) {
                    continue;
                }

                if (!inMineRange(pos)) {
                    continue;
                }

                if (!inPlaceRange(crystalPos.down())) {
                    continue;
                }

                if (blocked(crystalPos)) {
                    continue;
                }

                double d = mc.player.getEyePos()
                    .distanceTo(Vec3d.ofCenter(pos));

                if (distanceCheck(civ, pos, distance, d)) {
                    best = new Target(
                        pos,
                        crystalPos,
                        MineType.AutoCity,
                        autoCityPriority.get().priority
                            + (civ && pos.equals(civPos) ? 0.1 : 0),
                        civ,
                        false
                    );

                    distance = d;
                }
            }
        }

        return best;
    }

    private Target getAntiBurrow() {
        Target best = null;
        double distance = 1000;

        for (AbstractClientPlayerEntity player : enemies) {
            BlockPos pos = new BlockPos(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ()
            );

            if (!solid2(pos) || getBlock(pos) == Blocks.BEDROCK) {
                continue;
            }

            if (!inMineRange(pos)) {
                continue;
            }

            double d = mc.player.getEyePos()
                .distanceTo(Vec3d.ofCenter(pos));

            if (d < distance) {
                best = new Target(
                    pos,
                    null,
                    MineType.AntiBurrow,
                    antiBurrowPriority.get().priority,
                    false,
                    false
                );

                distance = d;
            }
        }

        return best;
    }

    private boolean distanceCheck(
        boolean civ,
        BlockPos pos,
        double closest,
        double distance
    ) {
        if (civ && pos.equals(civPos)) {
            return true;
        }

        if (target != null && pos.equals(target.pos)) {
            return true;
        }

        return distance < closest;
    }

    private boolean priorityCheck(
        Target current,
        Priority priority
    ) {
        if (priority.priority < 0) {
            return false;
        }

        if (current == null) {
            return true;
        }

        return priority.priority >= current.priority;
    }

    // ==================== Helpers ====================

    private Block getBlock(BlockPos pos) {
        if (mc.world == null) {
            return Blocks.AIR;
        }

        return mc.world.getBlockState(pos).getBlock();
    }

    private Hand getHand() {
        if (mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
            return Hand.OFF_HAND;
        }

        if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) {
            return Hand.MAIN_HAND;
        }

        return null;
    }

    private boolean miningCheck(int slot) {
        if (target == null || target.pos == null || slot == -1) {
            return false;
        }

        return minedFor * speed.get()
            >= getMineTicks(slot, true);
    }

    private float getTime(
        BlockPos pos,
        int slot,
        boolean speedMod
    ) {
        if (mc.world == null || mc.player == null) {
            return 0;
        }

        BlockState state = mc.world.getBlockState(pos);

        float hardness = state.getHardness(mc.world, pos);

        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack =
            mc.player.getInventory().getStack(slot);

        float multiplier =
            stack.getMiningSpeedMultiplier(state);

        float divisor =
            stack.isSuitableFor(state) ? 30 : 100;

        return multiplier / hardness / divisor;
    }

    private float getMineTicks(
        int slot,
        boolean speedMod
    ) {
        if (slot == -1 || target == null) {
            return -1;
        }

        float time =
            getTime(target.pos, slot, speedMod);

        if (time <= 0) {
            return -1;
        }

        return (float) (1 / (time * speed.get()));
    }

    private int fastestSlot() {
        if (mc.player == null
            || mc.world == null
            || target == null) {

            return -1;
        }

        int bestSlot = -1;
        float bestSpeed = 0;

        int maxSlot =
            pickAxeSwitchMode.get() == SwitchMode.Silent
                ? 9
                : 36;

        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack =
                mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) {
                continue;
            }

            BlockState state =
                mc.world.getBlockState(target.pos);

            if (!stack.isSuitableFor(state)) {
                continue;
            }

            float miningSpeed =
                stack.getMiningSpeedMultiplier(state);

            if (miningSpeed > bestSpeed) {
                bestSpeed = miningSpeed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // ==================== Manual Mining ====================

    public void onStart(BlockPos pos) {
        if (target != null
            && target.manual
            && pos.equals(target.pos)) {

            abort(pos);

            civPos = null;
            target = null;

            return;
        }

        if (manualMine.get()
            && getBlock(pos) != Blocks.BEDROCK) {

            started = false;

            target = new Target(
                pos,
                null,
                MineType.Manual,
                0,
                manualInsta.get(),
                true
            );
        }
    }

    public void onAbort(BlockPos pos) {
        if (target != null
            && target.manual
            && target.pos.equals(pos)) {

            abort(pos);
            target = null;
            started = false;
            minedFor = 0;
        }
    }

    public void onStop(BlockPos pos) {
    }

    private void abort(BlockPos pos) {
        sendAction(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            pos,
            Direction.UP
        );

        started = false;
    }

    // ==================== Block Checks ====================

    private boolean solid2(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);

        return !state.isAir()
            && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean inMineRange(BlockPos pos) {
        if (mc.player == null) {
            return false;
        }

        return mc.player.getEyePos()
            .distanceTo(Vec3d.ofCenter(pos)) <= 6;
    }

    private boolean inPlaceRange(BlockPos pos) {
        if (mc.player == null) {
            return false;
        }

        return mc.player.getEyePos()
            .distanceTo(Vec3d.ofCenter(pos)) <= 6;
    }

    private Direction getPlaceOnDirection(BlockPos pos) {
        if (mc.world == null) {
            return null;
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.offset(direction);

            if (!mc.world.getBlockState(neighbour)
                .getCollisionShape(mc.world, neighbour)
                .isEmpty()) {

                return direction.getOpposite();
            }
        }

        return null;
    }

    private boolean crystalBlock(BlockPos pos) {
        return getBlock(pos) == Blocks.OBSIDIAN
            || getBlock(pos) == Blocks.BEDROCK;
    }

    private boolean blocked(BlockPos pos) {
        Box box = new Box(
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            pos.getX() + 1,
            pos.getY() + 1,
            pos.getZ() + 1
        );

        return EntityUtils.intersectsWithEntity(
            box,
            entity ->
                entity instanceof PlayerEntity
                    && !entity.isSpectator()
        );
    }

    public BlockPos targetPos() {
        return target == null ? null : target.pos;
    }

    // ==================== Enums ====================

    private enum SwitchMode {
        Silent,
        PickSilent,
        InvSwitch
    }

    private enum Priority {
        Highest(6),
        Higher(5),
        High(4),
        Normal(3),
        Low(2),
        Lower(1),
        Lowest(0),
        Disabled(-1);

        public final int priority;

        Priority(int priority) {
            this.priority = priority;
        }
    }

    private enum MineType {
        Cev,
        TrapCev,
        SurroundCev,
        SurroundMiner,
        AutoCity,
        AntiBurrow,
        Manual
    }

    private record Target(
        BlockPos pos,
        BlockPos crystalPos,
        MineType type,
        double priority,
        boolean civ,
        boolean manual
    ) {
    }
}