package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import javassist.*;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener, ServerStartedListener {

    private static int sowBarrelTemplateId;

    private static ArrayList<Integer> sowRadius = new ArrayList<>(Arrays.asList(0,1,2,3,4,5));
    private static ArrayList<Double> skillUnlockPoints = new ArrayList<>(Arrays.asList(0d,10d,50d,70d,90d,100d));
    private static double minimumUnitActionTime = 20d; // tenths of a second.
    static Random r = new Random();

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    @Override
    public void configure(Properties properties) {
        final double EQUIVALENT_100 = 99.99999615;
        minimumUnitActionTime = Double.parseDouble(properties.getProperty("minimumUnitActionTime", Double.toString(minimumUnitActionTime)));

        if (properties.getProperty("sowRadius").length() > 0) {
            logger.log(Level.INFO, "sowRadius: " + properties.getProperty("sowRadius"));
            sowRadius = Arrays.stream(properties.getProperty("sowRadius").replaceAll("\\s", "").split(","))
                    .mapToInt(Integer::parseInt)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (properties.getProperty("skillUnlockPoints").length() > 0) {
            logger.log(Level.INFO, "skillUnlockPoints: " + properties.getProperty("skillUnlockPoints"));
            skillUnlockPoints = Arrays.stream(properties.getProperty("skillUnlockPoints").replaceAll("\\s", "").split(","))
                    .mapToDouble(Double::parseDouble)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            IntStream.range(0, skillUnlockPoints.size()).forEach(value -> {
                if (skillUnlockPoints.get(value) == 100)
                    skillUnlockPoints.set(value, EQUIVALENT_100);
            });
        }
    }

    @Override
    public void init() {
        try {
            ModActions.init();
            insertIntoGetName();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void onItemTemplatesCreated() {
        ItemTemplateBuilder sowBarrel = new ItemTemplateBuilder("jdbSowBarrel");
        sowBarrelTemplateId = IdFactory.getIdFor("jdbSowBarrel", IdType.ITEMTEMPLATE);
        sowBarrel.name("Seed barrel","seed barrels", "A tool used to sow seed over an area.");
        sowBarrel.size(3);
        //sowBarrel.descriptions();
        sowBarrel.itemTypes(new short[]{ItemTypes.ITEM_TYPE_WOOD, ItemTypes.ITEM_TYPE_NAMED, ItemTypes.ITEM_TYPE_REPAIRABLE,
                ItemTypes.ITEM_TYPE_COLORABLE, ItemTypes.ITEM_TYPE_HASDATA});
        sowBarrel.imageNumber((short) 245);
        sowBarrel.behaviourType((short) 1);
        sowBarrel.combatDamage(0);
        sowBarrel.decayTime(2419200L);
        sowBarrel.dimensions(30, 30, 50);
        sowBarrel.primarySkill(-10);
        //sowBarrel.bodySpaces();
        sowBarrel.modelName("model.container.barrel.small.");
        sowBarrel.difficulty(5);
        sowBarrel.weightGrams(1000);
        sowBarrel.material((byte) 14);
        sowBarrel.value(10000);
        sowBarrel.isTraded(true);
        //sowBarrel.armourType();
        try {
            sowBarrel.build();
        } catch (IOException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(new SowAction());
        ModActions.registerAction(new FillBarrelAction());
        ModActions.registerAction(new ConfigureSeedBarrelAction());
        ModActions.registerAction(new ExamineBarrelAction());
        ModActions.registerAction(new EmptyBarrelAction());
        ModActions.registerAction(new HarvestAction());

        AdvancedCreationEntry sowBarrel = CreationEntryCreator.createAdvancedEntry(SkillList.CARPENTRY,
                ItemList.plank, ItemList.pegWood, sowBarrelTemplateId, false, false, 0.0f, true, false,
                CreationCategories.TOOLS);
        sowBarrel.addRequirement(new CreationRequirement(1, ItemList.plank, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(2, ItemList.pegWood, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(3, ItemList.rope, 1, true));
    }

    /**
     * Insert a code block into Item.getName(Z) to handle custom naming when the seed barrel contains seeds. For example
     * name a barrel: Seed barrel [corn].
     * insert towards its end and just before this:
     *      builder.app end(this.name);
     *      line 1232: 2123
     *
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private void insertIntoGetName() throws NotFoundException, CannotCompileException {
        CtClass itemCtClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");

        CtClass returnType = HookManager.getInstance().getClassPool().get("java.lang.String");
        CtClass[] paramTypes = {CtPrimitiveType.booleanType};
        CtMethod getNameCtMethod = itemCtClass.getMethod("getName", Descriptor.ofMethod(returnType, paramTypes));

        String source = "" +
                "if (this.getTemplateId() == com.joedobo27.farmbarrelmod.FarmBarrelMod.getSowBarrelTemplateId() " +
                "&& com.joedobo27.farmbarrelmod.FarmBarrelMod.decodeContainedCropId(this) != " +
                "com.joedobo27.farmbarrelmod.Crops.EMPTY.getId()){ " +
                "stoSend = \" [\" + com.joedobo27.farmbarrelmod.Crops.getCropNameFromCropId(" +
                "com.joedobo27.farmbarrelmod.FarmBarrelMod.decodeContainedCropId(this), " +
                "com.joedobo27.farmbarrelmod.FarmBarrelMod.decodeIsSeed(this)) + \"]\";}" +
                "";

        getNameCtMethod.insertAt(1232, source);
    }

    @SuppressWarnings("WeakerAccess")
    public static int getSowBarrelTemplateId() {
        return sowBarrelTemplateId;
    }

    static ArrayList<Integer> getSowRadius() {
        return sowRadius;
    }

    static ArrayList<Double> getSkillUnlockPoints() {
        return skillUnlockPoints;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0xF0000000
     * This gives up to 15 potential sow radius values.
     *
     * @param seedBarrel WU Item object
     * @return int value, radius value for sow square.
     */
    static int decodeSowRadius(Item seedBarrel) {
        return (seedBarrel.getData1() & 0xF0000000) >>> 28;
    }

    public static boolean decodeIsSeed(Item seedBarrel) {
        return ((seedBarrel.getData1() & 0B1000000000000000000000000000) >>> 27) == 1;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table.
     * This gives up to 2047 potential to put in barrel.
     * 0xF800 FFFF or 1111 1000 0000 0000 1111 1111 1111 1111
     *
     * @param seedBarrel WU Item object
     * @return int value, how many seed to move into the seedBarrel for a supply action.
     */
    static int decodeSupplyQuantity(Item seedBarrel) {
        return (seedBarrel.getData1() & 0x7FF0000) >>> 16 ;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0000FF80.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from
     * WU. 0x0000FF80 or 0000 0000 0000 0000 1111 1111 1000 0000
     *
     * @param seedBarrel WU Item object
     * @return int value, an identifier for Wrap.Crop for the seed type in barrel.
     */
    @SuppressWarnings("WeakerAccess")
    public static int decodeContainedCropId(Item seedBarrel) {
        int id = (seedBarrel.getData1() & 0xFF80) >>> 7;
        if (id >= Crops.getLastUsableEntry())
            return Crops.getLastUsableEntry();
        else
            return id;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0000007F.
     * This gives up to 127 values even know we only need 100 for quality. I don't know how to isolate just 100.
     * 0x0000007F or 0000 0000 0000 0000 0000 0111 1111
     *
     * @param seedBarrel WU Item object
     * @return int value, The quality of contained crops.
     */
    static int decodeContainedQuality(Item seedBarrel){
        return seedBarrel.getData1() & 0x7F;
    }

    /**
     * Encode into Item.data1()0xF worth of data into bit positions 32-29(0xF0000000). This is the barrel's sow radius.
     * This gives 16 potential values, 0 for one tile all the way up to 15 for 961 tiles (31x31 square).
     * 0xF000 0000 or 1111 0000 0000 0000 0000 0000 0000 0000
     *
     * @param seedBarrel WU Item object
     * @param sowRadius int primitive
     */
    static void encodeSowRadius(Item seedBarrel, int sowRadius){
        int preservedData = seedBarrel.getData1() & 0x0FFFFFFF;
        seedBarrel.setData1( (sowRadius << 28) + preservedData);
    }

    static void encodeIsSeed(Item seedBarrel, boolean isSeed) {
        int preservedData = seedBarrel.getData1() & 0B11110111111111111111111111111111;
        if (isSeed){
            seedBarrel.setData1(preservedData + (1 << 27));
        }
        else
            seedBarrel.setData1(preservedData);
    }

    /**
     * Encode into Item.data1() 0x7FF worth of data into bit positions 28-17(0x0FFF0000). This is the barrel's supply quantity.
     * This gives up to 2047 potential filling values.
     * 0xF800 FFFF or 1111 1000 0000 0000 1111 1111 1111 1111
     *
     * @param seedBarrel WU Item object
     * @param supplyQuantity int primitive
     */
    static void encodeSupplyQuantity(Item seedBarrel, int supplyQuantity){
        int preservedData = seedBarrel.getData1() & 0xF800FFFF;
        seedBarrel.setData1((supplyQuantity << 16) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x1FF worth of data into bit positions 16-8(0xFFFF 007F). This is the barrel's seed ID.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from WU.
     * 0xFFFF 007F or 1111 1111 1111 1111 0000 0000 0111 1111
     *
     * @param seedBarrel WU Item object
     * @param cropId int primitive
     */
    static void encodeContainedCropId(Item seedBarrel, int cropId){
        int preservedData = seedBarrel.getData1() & 0xFFFF007F;
        seedBarrel.setData1((cropId << 7) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x7F worth of data into bit positions 7-1(0xFFFF FF80). This is the barrel's seed quality.
     * This gives up to 127 values even know we only need 100 for quality. I don't know how to isolate just 100.
     * 0xFFFF FF80 or 1111 1111 1111 1111 1111 1111 1000 0000
     *
     * @param seedBarrel WU Item object
     * @param quality int primitive
     */
    static void encodeContainedQuality(Item seedBarrel, int quality){
        int preservedData = seedBarrel.getData1() & 0xFFFFFF80;
        seedBarrel.setData1(quality + preservedData);
    }

    /**
     * It shouldn't be necessary to have a fantastic, 104woa, speed rune, 99ql, 99 skill in order to get the fastest time.
     * Aim for just skill as getting close to shortest time and the other boosts help at lower levels but aren't needed to have
     * the best at end game.
     */
    static double getBaseUnitActionTime(Item tool, Creature performer, Action action, int mainSkillId, int bonusSkillId){
        final int MAX_BONUS = 10;
        final double MAX_WOA_EFFECT = 0.20;
        final double TOOL_RARITY_EFFECT = 0.1;
        final double ACTION_RARITY_EFFECT = 0.33;
        final double MAX_SKILL = 100.0d;
        double time;
        double modifiedKnowledge = Math.min(MAX_SKILL, performer.getSkills().getSkillOrLearn(mainSkillId).getKnowledge(tool,
                Math.min(MAX_BONUS, performer.getSkills().getSkillOrLearn(bonusSkillId).getKnowledge() / 5)));
        time = Math.max(minimumUnitActionTime, (130.0 - modifiedKnowledge) * 1.3f / Servers.localServer.getActionTimer());

        // woa
        if (tool != null && tool.getSpellSpeedBonus() > 0.0f)
            time = Math.max(minimumUnitActionTime, time * (1 - (MAX_WOA_EFFECT * tool.getSpellSpeedBonus() / 100.0)));
        //rare barrel item, 10% speed reduction per rarity level.
        if (tool != null && tool.getRarity() > 0)
            time = Math.max(minimumUnitActionTime, time * (1 - (tool.getRarity() * TOOL_RARITY_EFFECT)));
        //rare action, 33% speed reduction per rarity level.
        if (action.getRarity() > 0)
            time = Math.max(minimumUnitActionTime, time * (1 - (action.getRarity() * ACTION_RARITY_EFFECT)));
        // rune effects
        if (tool != null && tool.getSpellEffects() != null && tool.getSpellEffects().getRuneEffect() != -10L)
            time = Math.max(minimumUnitActionTime, time * (1 - RuneUtilities.getModifier(tool.getSpellEffects().getRuneEffect(),
                    RuneUtilities.ModifierEffect.ENCH_USESPEED)));
        return time;
    }
}
