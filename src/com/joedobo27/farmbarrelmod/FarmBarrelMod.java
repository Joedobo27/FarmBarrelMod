package com.joedobo27.farmbarrelmod;


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


public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener, ServerStartedListener {

    private static int sowBarrelTemplateId;

    private static ArrayList<Integer> sowRadius = new ArrayList<>(Arrays.asList(0,1,2,3,4,5));
    private static ArrayList<Integer> skillUnlockPoints = new ArrayList<>(Arrays.asList(0,10,50,70,90,100));
    static double minimumUnitActionTime = 1.0d; // tenths of a second.

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    @Override
    public void configure(Properties properties) {
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
                    .mapToInt(Integer::parseInt)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
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
     *      builder.append(this.name);
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
                "" +
                "if (this.getTemplateId() == com.joedobo27.farmbarrelmod.FarmBarrelMod.getSowBarrelTemplateId() " +
                "&& com.joedobo27.farmbarrelmod.FarmBarrelMod.decodeContainedSeed(this) != -1){ " +
                "stoSend = \" [\" + com.joedobo27.farmbarrelmod.Crops.getCropNameFromCropId(" +
                "com.joedobo27.farmbarrelmod.FarmBarrelMod.decodeContainedSeed(this)) + \"]\";}" +
                "";

        getNameCtMethod.insertAt(1193, source);
    }

    public static int getSowBarrelTemplateId() {
        return sowBarrelTemplateId;
    }

    static ArrayList<Integer> getSowRadius() {
        return sowRadius;
    }

    static ArrayList<Integer> getSkillUnlockPoints() {
        return skillUnlockPoints;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0xF0000000
     *
     * @param seedBarrel WU Item object
     * @return int value, radius value for sow square.
     */
    static int decodeSowRadius(Item seedBarrel) {
        return (seedBarrel.getData1() & 0xF0000000) >>> 28;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0FFF0000
     * This gives up to 15 potential sow radius values.
     *
     * @param seedBarrel WU Item object
     * @return int value, how many seed to move into the seedBarrel for a supply action.
     */
    static int decodeSupplyQuantity(Item seedBarrel) {
        return (seedBarrel.getData1() & 0xFFF0000) >>> 16 ;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0000FF80.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from
     * WU. 0x0000FF80 or 0000 0000 0000 0000 1111 1111 1000 0000
     *
     * @param seedBarrel WU Item object
     * @return int value, an identifier for Wrap.Crop for the seed type in barrel.
     */
    public static int decodeContainedSeed(Item seedBarrel) {
        int id = (seedBarrel.getData1() & 0xFF80) >> 7;
        if (id > Crops.getLastUsableEntry())
            return -1;
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
     *
     * @param seedBarrel WU Item object
     * @param sowRadius int primitive
     */
    static void encodeSowRadius(Item seedBarrel, int sowRadius){
        int preservedData = seedBarrel.getData1() & 0x0FFFFFFF;
        seedBarrel.setData1( (sowRadius << 28) + preservedData);
    }

    /**
     * Encode into Item.data1() 0xFFF worth of data into bit positions 28-17(0x0FFF0000). This is the barrel's supply quantity.
     * This gives up to 4095 potential filling values.
     *
     * @param seedBarrel WU Item object
     * @param supplyQuantity int primitive
     */
    static void encodeSupplyQuantity(Item seedBarrel, int supplyQuantity){
        int preservedData = seedBarrel.getData1() & 0xF000FFFF;
        seedBarrel.setData1((supplyQuantity << 16) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x1FF worth of data into bit positions 16-8(0xFFFF007F). This is the barrel's seed ID.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from
     * WU. 0xFFFF007F or 1111 1111 1111 1111 0000 0000 0111 1111
     *
     * @param seedBarrel WU Item object
     * @param cropId int primitive
     */
    static void encodeContainedSeed(Item seedBarrel, int cropId){
        int preservedData = seedBarrel.getData1() & 0xFFFF007F;
        seedBarrel.setData1((cropId << 7) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x7F worth of data into bit positions 7-1(0xFFFFF8). This is the barrel's seed quality.
     * This gives up to 127 values even know we only need 100 for quality. I don't know how to isolate just 100.
     * 0xFFFFF8 or 1111 1111 1111 1111 1111 1000 0000
     *
     * @param seedBarrel WU Item object
     * @param quality int primitive
     */
    static void encodeContainedQuality(Item seedBarrel, int quality){
        int preservedData = seedBarrel.getData1() & 0xFFFFF800;
        seedBarrel.setData1(quality + preservedData);
    }
}
