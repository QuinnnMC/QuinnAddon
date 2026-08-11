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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1743;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;

public class LogStripper extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<LogType> logType;
   private final Setting<Integer> actionDelay;
   private State state;
   private class_2338 currentLog;
   private int timer;
   private int actionAttempts;
   private static final int MAX_ACTION_ATTEMPTS = 10;
   private static final int OFFHAND_SLOT = 40;
   private int previousSlot;

  // i dont know about this one -ryk

   public LogStripper() {
      super(QuinnAddon.CATEGORY, "log-stripper", "Places a log above your head, strips it with an axe, breaks it and repeats.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.logType = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("log")).description("The type of log to place, strip and break.")).defaultValue(LogStripper.LogType.Oak)).build());
      this.actionDelay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("action-delay")).description("Ticks to wait between actions.")).defaultValue(4)).min(1).max(20).sliderMin(1).sliderMax(20).build());
      this.state = LogStripper.State.PLACE;
      this.previousSlot = -1;
   }

   public void onActivate() {
      this.timer = 0;
      this.currentLog = null;
      this.state = LogStripper.State.PLACE;
      this.actionAttempts = 0;
      if (this.mc.field_1724 != null) {
         this.previousSlot = this.mc.field_1724.method_31548().method_67532();
      } else {
         this.previousSlot = -1;
      }

      if (!this.findAxe().found()) {
         this.error("No axe found in hotbar.", new Object[0]);
         this.toggle();
      } else if (!this.findLog().found() && !this.hasSelectedLogInOffhand()) {
         this.error("No selected log found.", new Object[0]);
         this.toggle();
      } else {
         if (!this.setupHands()) {
            this.error("Could not move the log to the offhand.", new Object[0]);
            this.toggle();
         }

      }
   }

   public void onDeactivate() {
      this.cancelBreaking();
      this.currentLog = null;
      this.state = LogStripper.State.PLACE;
      this.timer = 0;
      this.actionAttempts = 0;
      if (this.mc.field_1724 != null && this.previousSlot >= 0) {
         InvUtils.swap(this.previousSlot, false);
      }

      this.previousSlot = -1;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (!this.findAxe().found()) {
            this.error("No axe found in hotbar.", new Object[0]);
            this.toggle();
         } else {
            if (!this.hasSelectedLogInOffhand()) {
               if (!this.findLog().found()) {
                  this.info("Out of selected logs.", new Object[0]);
                  this.toggle();
                  return;
               }

               if (!this.moveLogToOffhand()) {
                  return;
               }

               this.selectAxe();
            }

            if (!this.isAxeInMainHand()) {
               this.selectAxe();
               this.timer = 1;
            } else if (this.timer > 0) {
               --this.timer;
            } else {
               switch (this.state.ordinal()) {
                  case 0 -> this.handlePlace();
                  case 1 -> this.handleStrip();
                  case 2 -> this.handleBreak();
               }

            }
         }
      }
   }

   private boolean setupHands() {
      if (this.mc.field_1724 == null) {
         return false;
      } else {
         return !this.hasSelectedLogInOffhand() && !this.moveLogToOffhand() ? false : this.selectAxe();
      }
   }

   private boolean moveLogToOffhand() {
      FindItemResult log = this.findLog();
      if (!log.found()) {
         return this.hasSelectedLogInOffhand();
      } else if (this.hasSelectedLogInOffhand()) {
         return true;
      } else {
         InvUtils.move().from(log.slot()).to(40);
         return this.hasSelectedLogInOffhand();
      }
   }

   private boolean selectAxe() {
      FindItemResult axe = this.findAxe();
      return !axe.found() ? false : InvUtils.swap(axe.slot(), false);
   }

   private boolean isAxeInMainHand() {
      return this.mc.field_1724 != null && this.mc.field_1724.method_6047().method_7909() instanceof class_1743;
   }

   private boolean hasSelectedLogInOffhand() {
      if (this.mc.field_1724 == null) {
         return false;
      } else {
         class_1792 selectedLog = ((LogType)this.logType.get()).getItem();
         return this.mc.field_1724.method_6079().method_31574(selectedLog);
      }
   }

   private void handlePlace() {
      if (this.currentLog != null) {
         this.resetCurrentLog();
      } else if (!this.hasSelectedLogInOffhand()) {
         if (this.moveLogToOffhand()) {
            this.selectAxe();
         }
      } else if (!this.isAxeInMainHand()) {
         this.selectAxe();
      } else {
         if (this.placeLog()) {
            this.state = LogStripper.State.STRIP;
            this.actionAttempts = 0;
            this.timer = (Integer)this.actionDelay.get();
         } else {
            this.timer = (Integer)this.actionDelay.get();
         }

      }
   }

   private boolean placeLog() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         class_2338 pos = this.mc.field_1724.method_24515().method_10086(2);
         if (!this.mc.field_1687.method_8320(pos).method_45474()) {
            return false;
         } else {
            class_243 hitPos = new class_243((double)pos.method_10263() + (double)0.5F, (double)pos.method_10264() + (double)0.5F, (double)pos.method_10260() + (double)0.5F);
            class_3965 hit = new class_3965(hitPos, class_2350.field_11033, pos, false);
            class_1269 result = this.mc.field_1761.method_2896(this.mc.field_1724, class_1268.field_5810, hit);
            this.mc.field_1724.method_6104(class_1268.field_5810);
            if (!result.method_23665()) {
               return false;
            } else {
               class_2680 after = this.mc.field_1687.method_8320(pos);
               if (!this.isSelectedLog(after)) {
                  return false;
               } else {
                  this.currentLog = pos.method_10062();
                  this.selectAxe();
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private void handleStrip() {
      if (this.currentLog == null) {
         this.state = LogStripper.State.PLACE;
         this.actionAttempts = 0;
         this.timer = (Integer)this.actionDelay.get();
      } else {
         class_2680 stateAtTarget = this.mc.field_1687.method_8320(this.currentLog);
         if (this.isStrippedLog(stateAtTarget)) {
            this.state = LogStripper.State.BREAK;
            this.actionAttempts = 0;
            this.timer = (Integer)this.actionDelay.get();
         } else if (!stateAtTarget.method_26215() && !stateAtTarget.method_45474()) {
            if (!this.isSelectedLog(stateAtTarget)) {
               this.resetCurrentLog();
            } else if (!this.isAxeInMainHand()) {
               if (this.selectAxe()) {
                  this.timer = 1;
               }
            } else {
               ++this.actionAttempts;
               if (this.actionAttempts > 10) {
                  this.resetCurrentLog();
               } else {
                  this.stripLog();
                  this.timer = (Integer)this.actionDelay.get();
               }
            }
         } else {
            this.resetCurrentLog();
         }
      }
   }

   private boolean stripLog() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null && this.currentLog != null) {
         class_2680 before = this.mc.field_1687.method_8320(this.currentLog);
         if (this.isStrippedLog(before)) {
            return true;
         } else if (!this.isSelectedLog(before)) {
            return false;
         } else if (!this.isAxeInMainHand() && !this.selectAxe()) {
            return false;
         } else {
            class_243 hitPos = new class_243((double)this.currentLog.method_10263() + (double)0.5F, (double)this.currentLog.method_10264() + (double)0.5F, (double)this.currentLog.method_10260() + (double)0.5F);
            class_3965 hit = new class_3965(hitPos, class_2350.field_11033, this.currentLog, false);
            class_1269 result = this.mc.field_1761.method_2896(this.mc.field_1724, class_1268.field_5808, hit);
            this.mc.field_1724.method_6104(class_1268.field_5808);
            if (!result.method_23665()) {
               return false;
            } else {
               class_2680 after = this.mc.field_1687.method_8320(this.currentLog);
               return this.isStrippedLog(after) || this.isSelectedLog(after);
            }
         }
      } else {
         return false;
      }
   }

   private void handleBreak() {
      if (this.currentLog == null) {
         this.state = LogStripper.State.PLACE;
         this.actionAttempts = 0;
         this.timer = (Integer)this.actionDelay.get();
      } else {
         class_2680 stateAtTarget = this.mc.field_1687.method_8320(this.currentLog);
         if (!stateAtTarget.method_26215() && !stateAtTarget.method_45474()) {
            if (!this.isStrippedLog(stateAtTarget)) {
               this.resetCurrentLog();
            } else if (!this.isAxeInMainHand()) {
               if (this.selectAxe()) {
                  this.timer = 1;
               }
            } else {
               ++this.actionAttempts;
               if (this.actionAttempts > 10) {
                  this.resetCurrentLog();
               } else {
                  this.breakLog();
                  this.timer = (Integer)this.actionDelay.get();
               }
            }
         } else {
            this.cancelBreaking();
            this.currentLog = null;
            this.state = LogStripper.State.PLACE;
            this.actionAttempts = 0;
            this.timer = (Integer)this.actionDelay.get();
            if (this.hasSelectedLogInOffhand()) {
               this.selectAxe();
            } else if (this.findLog().found()) {
               this.moveLogToOffhand();
               this.selectAxe();
            }

         }
      }
   }

   private boolean breakLog() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null && this.currentLog != null) {
         class_2680 stateAtTarget = this.mc.field_1687.method_8320(this.currentLog);
         if (!stateAtTarget.method_26215() && !stateAtTarget.method_45474()) {
            if (!this.isStrippedLog(stateAtTarget)) {
               return false;
            } else if (!this.isAxeInMainHand() && !this.selectAxe()) {
               return false;
            } else {
               class_2350 direction = class_2350.field_11033;
               if (!this.mc.field_1761.method_2923()) {
                  boolean started = this.mc.field_1761.method_2910(this.currentLog, direction);
                  if (!started) {
                     return false;
                  }
               }

               this.mc.field_1761.method_2902(this.currentLog, direction);
               this.mc.field_1724.method_6104(class_1268.field_5808);
               class_2680 after = this.mc.field_1687.method_8320(this.currentLog);
               return after.method_26215() || after.method_45474();
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private void resetCurrentLog() {
      this.cancelBreaking();
      this.currentLog = null;
      this.state = LogStripper.State.PLACE;
      this.timer = (Integer)this.actionDelay.get();
      this.actionAttempts = 0;
   }

   private void cancelBreaking() {
      if (this.mc.field_1761 != null) {
         this.mc.field_1761.method_2925();
      }

   }

   private FindItemResult findLog() {
      class_1792 target = ((LogType)this.logType.get()).getItem();
      return InvUtils.findInHotbar((stack) -> !stack.method_7960() && stack.method_31574(target));
   }

   private FindItemResult findAxe() {
      return InvUtils.findInHotbar((stack) -> !stack.method_7960() && stack.method_7909() instanceof class_1743);
   }

   private boolean isSelectedLog(class_2680 state) {
      if (state == null) {
         return false;
      } else {
         boolean var10000;
         switch (((LogType)this.logType.get()).ordinal()) {
            case 0 -> var10000 = state.method_27852(class_2246.field_10431);
            case 1 -> var10000 = state.method_27852(class_2246.field_10037);
            case 2 -> var10000 = state.method_27852(class_2246.field_10511);
            case 3 -> var10000 = state.method_27852(class_2246.field_10306);
            case 4 -> var10000 = state.method_27852(class_2246.field_10533);
            case 5 -> var10000 = state.method_27852(class_2246.field_10010);
            case 6 -> var10000 = state.method_27852(class_2246.field_37545);
            case 7 -> var10000 = state.method_27852(class_2246.field_42729);
            case 8 -> var10000 = state.method_27852(class_2246.field_54715);
            case 9 -> var10000 = state.method_27852(class_2246.field_22118);
            case 10 -> var10000 = state.method_27852(class_2246.field_22111);
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private boolean isStrippedLog(class_2680 state) {
      if (state == null) {
         return false;
      } else {
         boolean var10000;
         switch (((LogType)this.logType.get()).ordinal()) {
            case 0 -> var10000 = state.method_27852(class_2246.field_10519);
            case 1 -> var10000 = state.method_27852(class_2246.field_10436);
            case 2 -> var10000 = state.method_27852(class_2246.field_10366);
            case 3 -> var10000 = state.method_27852(class_2246.field_10254);
            case 4 -> var10000 = state.method_27852(class_2246.field_10622);
            case 5 -> var10000 = state.method_27852(class_2246.field_10244);
            case 6 -> var10000 = state.method_27852(class_2246.field_37548);
            case 7 -> var10000 = state.method_27852(class_2246.field_42732);
            case 8 -> var10000 = state.method_27852(class_2246.field_54716);
            case 9 -> var10000 = state.method_27852(class_2246.field_22119);
            case 10 -> var10000 = state.method_27852(class_2246.field_22112);
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private static enum State {
      PLACE,
      STRIP,
      BREAK;

      // $FF: synthetic method
      private static State[] $values() {
         return new State[]{PLACE, STRIP, BREAK};
      }
   }

   public static enum LogType {
      Oak(class_1802.field_8583),
      Spruce(class_1802.field_8684),
      Birch(class_1802.field_8170),
      Jungle(class_1802.field_8125),
      Acacia(class_1802.field_8820),
      DarkOak(class_1802.field_8652),
      Mangrove(class_1802.field_37512),
      Cherry(class_1802.field_42692),
      PaleOak(class_1802.field_54603),
      Crimson(class_1802.field_21981),
      Warped(class_1802.field_21982);

      private final class_1792 item;

      private LogType(class_1792 item) {
         this.item = item;
      }

      public class_1792 getItem() {
         return this.item;
      }

      // $FF: synthetic method
      private static LogType[] $values() {
         return new LogType[]{Oak, Spruce, Birch, Jungle, Acacia, DarkOak, Mangrove, Cherry, PaleOak, Crimson, Warped};
      }
   }
}
