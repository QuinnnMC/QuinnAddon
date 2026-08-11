package com.example.addon;

import com.example.addon.modules.AntiPhase;
import com.example.addon.modules.AutoMine;
import com.example.addon.modules.AutoWeb;
import com.example.addon.modules.BedrockNuker;
import com.example.addon.modules.LiquidFiller;
import com.example.addon.modules.Speed;
import com.example.addon.modules.BurrowEChest;




import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class QuinnAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("QuinnAddon");

    @Override
    public void onInitialize() {
      // we removed it  Modules.get().add(new AutoMine());
        Modules.get().add(new AutoWeb());
        Modules.get().add(new AntiPhase());
        Modules.get().add(new BedrockNuker());
        Modules.get().add(new LiquidFiller());
        Modules.get().add(new Speed());
        Modules.get().add(new BurrowEChest());



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
