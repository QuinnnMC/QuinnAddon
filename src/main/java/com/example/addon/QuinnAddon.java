package com.example.addon;

import com.example.addon.modules.AntiPhase;
import com.example.addon.modules.LogStripper;
import com.example.addon.modules.AutoWeb;
import com.example.addon.modules.BedrockNuker;
import com.example.addon.modules.LiquidFiller;
import com.example.addon.modules.Speed;
import com.example.addon.modules.Surround;
import com.example.addon.modules.DeathSounds;
import com.example.addon.modules.AutoTotemPlus;
import com.example.addon.modules.MassInstaMine;





import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class QuinnAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("QuinnAddon");

    @Override
    public void onInitialize() {
        Modules.get().add(new LogStripper());
        Modules.get().add(new AutoWeb());
        Modules.get().add(new AntiPhase());
        Modules.get().add(new BedrockNuker());
        Modules.get().add(new LiquidFiller());
        Modules.get().add(new Speed());
        Modules.get().add(new Surround());
        Modules.get().add(new DeathSounds());
        Modules.get().add(new AutoTotemPlus());
        Modules.get().add(new MassInstaMine());



    }


    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}