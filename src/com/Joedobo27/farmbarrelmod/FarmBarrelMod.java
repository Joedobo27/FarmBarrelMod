package com.Joedobo27.farmbarrelmod;


import com.wurmonline.server.items.ItemTypes;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;

import java.util.Properties;

public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener{

    private static SowAction sowAction;
    private static int sowBarrelId;

    private static int sowBarrelX = 5;
    private static int sowBarrelY = 5;
    private static int sowBarrelZ = 7;
    private static float sowBarrelDifficulty = 5.0f;
    private static int sowBarrelGrams = 1000;
    private static int sowBarrelValue = 10000;
    private static int[] sowRadius = new int[]{0,1,2,3};
    private static int[] skillUnlockPoints = new int[]{0,50,70,100};


    @Override
    public void configure(Properties properties) {
        sowBarrelX = Integer.parseInt(properties.getProperty("sowBarrelX", Integer.toString(sowBarrelX)));
        sowBarrelY = Integer.parseInt(properties.getProperty("sowBarrelY", Integer.toString(sowBarrelY)));
        sowBarrelZ = Integer.parseInt(properties.getProperty("sowBarrelZ", Integer.toString(sowBarrelZ)));
        sowBarrelDifficulty = Float.parseFloat(properties.getProperty("sowBarrelDifficulty", Float.toString(sowBarrelDifficulty)));
        sowBarrelGrams = Integer.parseInt(properties.getProperty("sowBarrelGrams", Integer.toString(sowBarrelGrams)));
        sowBarrelValue = Integer.parseInt(properties.getProperty("sowBarrelValue", Integer.toString(sowBarrelValue)));

    }

    @Override
    public void init() {

    }

    @Override
    public void onItemTemplatesCreated() {
        ItemTemplateBuilder sowBarrel = new ItemTemplateBuilder("jdbSowBarrel");
        sowBarrelId = IdFactory.getIdFor("jdbSowBarrel", IdType.ITEMTEMPLATE);
        sowBarrel.name("seed barrel","seed barrels", "A tool used to sow seed over an area.");
        sowBarrel.size(3);
        //sowBarrel.descriptions();
        sowBarrel.itemTypes(new short[]{ItemTypes.ITEM_TYPE_WOOD, ItemTypes.ITEM_TYPE_BULKCONTAINER, ItemTypes.ITEM_TYPE_HOLLOW,
        ItemTypes.ITEM_TYPE_NAMED, ItemTypes.ITEM_TYPE_REPAIRABLE, ItemTypes.ITEM_TYPE_COLORABLE, ItemTypes.ITEM_TYPE_HASDATA});
        sowBarrel.imageNumber((short) 245);
        sowBarrel.behaviourType((short) 1);
        sowBarrel.combatDamage(0);
        sowBarrel.decayTime(2419200L);
        sowBarrel.dimensions(sowBarrelX, sowBarrelY, sowBarrelZ); // 175 L volume for 1k potato. 5 x 5 x 7
        sowBarrel.primarySkill(-10);
        //sowBarrel.bodySpaces();
        sowBarrel.modelName("model.container.barrel.small.");
        sowBarrel.difficulty(sowBarrelDifficulty);
        sowBarrel.weightGrams(sowBarrelGrams);
        sowBarrel.material((byte) 14);
        sowBarrel.value(sowBarrelValue);
        sowBarrel.isTraded(true);
        //sowBarrel.armourType();
    }

    public static int getSowBarrelId() {
        return sowBarrelId;
    }

    public static int[] getSowRadius() {
        return sowRadius;
    }

    public static int[] getSkillUnlockPoints() {
        return skillUnlockPoints;
    }
}
