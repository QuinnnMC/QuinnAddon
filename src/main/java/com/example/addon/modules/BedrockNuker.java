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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2879;

public class BedrockNuker extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgRender;
   private final Setting<Double> range;
   private final Setting<SortMode> sortMode;
   private final Setting<MiningMode> miningMode;
   private final Setting<Boolean> pauseWhileEat;
   private final Setting<Boolean> resetOnRangeExit;
   private final Setting<Boolean> rotate;
   private final Setting<Boolean> render;
   private final Setting<Integer> renderGrowTicks;
   private final Setting<ShapeMode> shapeMode;
   private final Setting<SettingColor> sideColor;
   private final Setting<SettingColor> lineColor;
   private class_2338 miningPos;
   private int renderTicks;
   private class_2338 lastRenderPos;

   public BedrockNuker() {
      super(QuinnAddon.CATEGORY, "bedrock-nuker", "Automatically mines nearby bedrock.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgRender = this.settings.createGroup("Render");
      this.range = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("range")).description("Maximum distance to target bedrock.")).defaultValue((double)6.0F).min((double)1.0F).max((double)6.0F).sliderMin((double)1.0F).sliderMax((double)6.0F).build());
      this.sortMode = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("sort-mode")).description("Determines which bedrock block is targeted.")).defaultValue(BedrockNuker.SortMode.Closest)).build());
      this.miningMode = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("mining-mode")).description("Determines which bedrock blocks can be targeted.")).defaultValue(BedrockNuker.MiningMode.All)).build());
      this.pauseWhileEat = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("pause-while-eat")).description("Pauses bedrock mining while using an enchanted golden apple.")).defaultValue(true)).build());
      this.resetOnRangeExit = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("reset-on-range-exit")).description("Resets the current mining target when it moves out of range.")).defaultValue(true)).build());
      this.rotate = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("rotate")).description("Rotate toward the bedrock being mined.")).defaultValue(false)).build());
      this.render = this.sgRender.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render")).description("Render the block currently being mined.")).defaultValue(true)).build());
      this.renderGrowTicks = this.sgRender.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("render-grow-ticks")).description("Ticks required for the render box to grow to full size.")).defaultValue(20)).min(1).max(20).sliderMin(1).sliderMax(20).build());
      this.shapeMode = this.sgRender.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("shape-mode")).description("How the target block is rendered.")).defaultValue(ShapeMode.Both)).build());
      this.sideColor = this.sgRender.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("side-color")).description("Color of the target block's sides.")).defaultValue(new SettingColor(255, 255, 255, 40)).build());
      this.lineColor = this.sgRender.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("line-color")).description("Color of the target block's outline.")).defaultValue(new SettingColor(255, 255, 255, 255)).build());
   }

   public void onActivate() {
      this.miningPos = null;
      this.renderTicks = 0;
      this.lastRenderPos = null;
   }

   public void onDeactivate() {
      this.stopMining();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (!(Boolean)this.pauseWhileEat.get() || !this.isUsingEnchantedGoldenApple()) {
            if (this.miningPos != null) {
               if (!this.isBedrock(this.miningPos)) {
                  this.stopMining();
               } else if (!this.inRange(this.miningPos)) {
                  if (!(Boolean)this.resetOnRangeExit.get()) {
                     return;
                  }

                  this.stopMining();
               } else if (this.miningPos.method_10264() <= this.mc.field_1687.method_31607()) {
                  this.stopMining();
               }
            }

            if (this.miningPos == null) {
               class_2338 target = this.findBedrock();
               if (target == null) {
                  return;
               }

               this.startMining(target);
            }

            if (this.miningPos != null) {
               this.mineBlock();
            }

         }
      } else {
         this.stopMining();
      }
   }

   private void startMining(class_2338 pos) {
      if (this.mc.field_1761 != null) {
         this.miningPos = pos.method_10062();
         this.renderTicks = 0;
         this.lastRenderPos = this.miningPos.method_10062();
         this.mc.field_1761.method_2910(this.miningPos, class_2350.field_11036);
      }
   }

   private void mineBlock() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null && this.miningPos != null) {
         if (!this.isBedrock(this.miningPos)) {
            this.stopMining();
         } else if (this.miningPos.method_10264() <= this.mc.field_1687.method_31607()) {
            this.stopMining();
         } else if (!this.inRange(this.miningPos)) {
            this.stopMining();
         } else {
            this.mc.field_1761.method_2902(this.miningPos, class_2350.field_11036);
            this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
            if (this.renderTicks < (Integer)this.renderGrowTicks.get()) {
               ++this.renderTicks;
            }

         }
      } else {
         this.stopMining();
      }
   }

   private class_2338 findBedrock() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         class_2338 playerPos = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil((Double)this.range.get());
         double maxDistance = (Double)this.range.get() * (Double)this.range.get();
         class_2338 best = null;
         double bestDistance = (double)0.0F;
         int bestY = Integer.MIN_VALUE;

         for(int x = -radius; x <= radius; ++x) {
            for(int y = -radius; y <= radius; ++y) {
               for(int z = -radius; z <= radius; ++z) {
                  class_2338 pos = playerPos.method_10069(x, y, z);
                  if (this.isBedrock(pos) && pos.method_10264() > this.mc.field_1687.method_31607() && (this.miningMode.get() != BedrockNuker.MiningMode.Flatten || pos.method_10264() >= this.mc.field_1724.method_31478())) {
                     double distance = this.mc.field_1724.method_5649((double)pos.method_10263() + (double)0.5F, (double)pos.method_10264() + (double)0.5F, (double)pos.method_10260() + (double)0.5F);
                     if (!(distance > maxDistance)) {
                        if (this.sortMode.get() == BedrockNuker.SortMode.Closest) {
                           if (best == null || distance < bestDistance) {
                              best = pos.method_10062();
                              bestDistance = distance;
                           }
                        } else if (this.sortMode.get() == BedrockNuker.SortMode.Furthest) {
                           if (best == null || distance > bestDistance) {
                              best = pos.method_10062();
                              bestDistance = distance;
                           }
                        } else if (this.sortMode.get() == BedrockNuker.SortMode.TopDown && (best == null || pos.method_10264() > bestY || pos.method_10264() == bestY && distance < bestDistance)) {
                           best = pos.method_10062();
                           bestY = pos.method_10264();
                           bestDistance = distance;
                        }
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private boolean isUsingEnchantedGoldenApple() {
      if (this.mc.field_1724 == null) {
         return false;
      } else {
         return !this.mc.field_1724.method_6115() ? false : this.mc.field_1724.method_6030().method_31574(class_1802.field_8367);
      }
   }

   private boolean isBedrock(class_2338 pos) {
      if (this.mc.field_1687 == null) {
         return false;
      } else {
         class_2680 state = this.mc.field_1687.method_8320(pos);
         return state.method_27852(class_2246.field_9987);
      }
   }

   private boolean inRange(class_2338 pos) {
      if (this.mc.field_1724 == null) {
         return false;
      } else {
         double maxDistance = (Double)this.range.get() * (Double)this.range.get();
         double distance = this.mc.field_1724.method_5649((double)pos.method_10263() + (double)0.5F, (double)pos.method_10264() + (double)0.5F, (double)pos.method_10260() + (double)0.5F);
         return distance <= maxDistance;
      }
   }

   private void stopMining() {
      if (this.mc.field_1761 != null) {
         this.mc.field_1761.method_2925();
      }

      this.miningPos = null;
      this.renderTicks = 0;
      this.lastRenderPos = null;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get()) {
         if (this.mc.field_1687 != null && this.miningPos != null) {
            if (this.isBedrock(this.miningPos)) {
               if (this.miningPos.method_10264() > this.mc.field_1687.method_31607()) {
                  if (this.lastRenderPos == null || !this.lastRenderPos.equals(this.miningPos)) {
                     this.lastRenderPos = this.miningPos.method_10062();
                     this.renderTicks = 0;
                  }

                  double progress = (double)this.renderTicks / (double)(Integer)this.renderGrowTicks.get();
                  progress = Math.max((double)0.0F, Math.min((double)1.0F, progress));
                  double minSize = 0.05;
                  double size = minSize + ((double)1.0F - minSize) * progress;
                  double centerX = (double)this.miningPos.method_10263() + (double)0.5F;
                  double centerY = (double)this.miningPos.method_10264() + (double)0.5F;
                  double centerZ = (double)this.miningPos.method_10260() + (double)0.5F;
                  double half = size / (double)2.0F;
                  double minX = centerX - half;
                  double minY = centerY - half;
                  double minZ = centerZ - half;
                  double maxX = centerX + half;
                  double maxY = centerY + half;
                  double maxZ = centerZ + half;
                  event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
               }
            }
         }
      }
   }

   public static enum SortMode {
      Closest,
      Furthest,
      TopDown;

      // $FF: synthetic method
      private static SortMode[] $values() {
         return new SortMode[]{Closest, Furthest, TopDown};
      }
   }

   public static enum MiningMode {
      All,
      Flatten;

      // $FF: synthetic method
      private static MiningMode[] $values() {
         return new MiningMode[]{All, Flatten};
      }
   }
}
