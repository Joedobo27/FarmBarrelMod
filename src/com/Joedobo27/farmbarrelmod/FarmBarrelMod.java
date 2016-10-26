package com.Joedobo27.farmbarrelmod;


import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.Joedobo27.farmbarrelmod.BytecodeTools.findConstantPoolReference;

public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener, ServerStartedListener {

    private static SowAction sowAction;
    private static int sowBarrelId;
    private static ClassPool classPool;

    private static int sowBarrelX = 5;
    private static int sowBarrelY = 5;
    private static int sowBarrelZ = 7;
    private static float sowBarrelDifficulty = 5.0f;
    private static int sowBarrelGrams = 1000;
    private static int sowBarrelValue = 10000;
    private static ArrayList<Integer> sowRadius = new ArrayList<>(Arrays.asList(1,2,3,4,5));
    private static ArrayList<Integer> skillUnlockPoints = new ArrayList<>(Arrays.asList(0,50,70,90,100));

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());


    @Override
    public void configure(Properties properties) {
        sowRadius.clear();
        for (String a :  properties.getProperty("sowRadius", sowRadius.toString().replaceAll("\\[|\\]", "")).split(",")){
            sowRadius.add(Integer.valueOf(a));
        }
        skillUnlockPoints.clear();
        for (String a :  properties.getProperty("skillUnlockPoints", skillUnlockPoints.toString().replaceAll("\\[|\\]", "")).split(",")){
            skillUnlockPoints.add(Integer.valueOf(a));
        }

        sowBarrelX = Integer.parseInt(properties.getProperty("sowBarrelX", Integer.toString(sowBarrelX)));
        sowBarrelY = Integer.parseInt(properties.getProperty("sowBarrelY", Integer.toString(sowBarrelY)));
        sowBarrelZ = Integer.parseInt(properties.getProperty("sowBarrelZ", Integer.toString(sowBarrelZ)));
        sowBarrelDifficulty = Float.parseFloat(properties.getProperty("sowBarrelDifficulty", Float.toString(sowBarrelDifficulty)));
        sowBarrelGrams = Integer.parseInt(properties.getProperty("sowBarrelGrams", Integer.toString(sowBarrelGrams)));
        sowBarrelValue = Integer.parseInt(properties.getProperty("sowBarrelValue", Integer.toString(sowBarrelValue)));

    }

    @Override
    public void init() {
        try {
            classPool = HookManager.getInstance().getClassPool();
            addActionData();
            moveToItemBytecode();
        } catch (NotFoundException | CannotCompileException | BadBytecode e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
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
        try {
            sowBarrel.build();
        } catch (IOException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void onServerStarted() {
        AdvancedCreationEntry sowBarrel = CreationEntryCreator.createAdvancedEntry(SkillList.CARPENTRY,
                ItemList.plank, ItemList.pegWood, sowBarrelId, false, false, 0.0f, true, false,
                CreationCategories.TOOLS);
        sowBarrel.addRequirement(new CreationRequirement(1, ItemList.plank, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(2, ItemList.pegWood, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(2, ItemList.rope, 1, true));
    }

    private void moveToItemBytecode() throws NotFoundException, BadBytecode {
        //<editor-fold desc="Modification document">
        /*
        Item.moveToItem() needs to be altered as it's blocking putting farm products in sowbarrel.
        current....
        if (this.isFood()) {
            if (target.getTemplateId() != 661 && !target.isCrate()) {
                if (mover != null) {
                    mover.getCommunicator().sendNormalServerMessage("The " + this.getName() + " would be destroyed.");
                }
                return false;
            }
        }
        else if (target.getTemplateId() != 662 && !target.isCrate()) {
            if (mover != null) {
                mover.getCommunicator().sendNormalServerMessage("The " + this.getName() + " would be destroyed.");
            }
            return false;
        }

        becomes....
        boolean isItemCompatibleWithBulk = com.Joedobo27.farmbarrelmod.FarmBarrelMod.canItemGoInBulkHook(this, target);
        // this is the bulk item, target is bulk container.
        if (!isItemCompatibleWithBulk) {
            if (mover != null) {
                mover.getCommunicator().sendNormalServerMessage("The " + this.getName() + " would be destroyed.");
            }
            return false;
        }

        Futher, use expression editor hook method to replace the returned value from canItemGoInBulk(). That method is effectly
        a blank dummy method.
         */
        //</editor-fold>

        JAssistClassData Item = new JAssistClassData("com.wurmonline.server.items.Item", classPool);

        //<editor-fold desc="find Bytecode construction">
        Bytecode find = new Bytecode(Item.getConstPool());
        find.addOpcode(Opcode.ALOAD_0);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        byte[] result = findConstantPoolReference(Item.getConstPool(), "// Method isFood:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFEQ); // 4165: ifeq          4228; = 63
        find.add(0, 63);
        find.addOpcode(Opcode.ALOAD);
        find.add(8);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method getTemplateId:()I");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.SIPUSH); // 4173: sipush        661 or 0x295
        find.add(2, 149); // 0x02, 0x95 or 2, 149
        find.addOpcode(Opcode.IF_ICMPEQ); // 4176: if_icmpeq     4288; = 112
        find.add(0, 112);
        find.addOpcode(Opcode.ALOAD);
        find.add(8);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method isCrate:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFNE); // 4184: ifne          4288; = 104
        find.add(0, 104);
        find.addOpcode(Opcode.ALOAD_1);
        find.addOpcode(Opcode.IFNULL); // 4188: ifnull        4226; = 38
        find.add(0, 38);
        find.addOpcode(Opcode.ALOAD_1);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Creature.getCommunicator:()Lcom/wurmonline/server/creatures/Communicator;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.NEW);
        result = findConstantPoolReference(Item.getConstPool(), "// class java/lang/StringBuilder");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.DUP);
        find.addOpcode(Opcode.INVOKESPECIAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method java/lang/StringBuilder.<init>:()V");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.LDC);
        result = findConstantPoolReference(Item.getConstPool(), "// String The ");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.ALOAD_0);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method getName:()Ljava/lang/String;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.LDC_W);
        result = findConstantPoolReference(Item.getConstPool(), "// String  would be destroyed.");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.toString:()Ljava/lang/String;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Communicator.sendNormalServerMessage:(Ljava/lang/String;)V");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.ICONST_0);
        find.addOpcode(Opcode.IRETURN);
        find.addOpcode(Opcode.ALOAD);
        find.add(8);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method getTemplateId:()I");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.SIPUSH); // 4233: sipush        662 or 0x296
        find.add(2, 150); // 0x02, 0x96 or 2, 150
        find.addOpcode(Opcode.IF_ICMPEQ); // 4236: if_icmpeq     4288; =52
        find.add(0, 52);
        find.addOpcode(Opcode.ALOAD);
        find.add(8);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method isCrate:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFNE); // 4244: ifne          4288; =44
        find.add(0, 44);
        find.addOpcode(Opcode.ALOAD_1);
        find.addOpcode(Opcode.IFNULL); // 4248: ifnull        4286; =38
        find.add(0, 38);
        find.addOpcode(Opcode.ALOAD_1);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Creature.getCommunicator:()Lcom/wurmonline/server/creatures/Communicator;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.NEW);
        result = findConstantPoolReference(Item.getConstPool(), "// class java/lang/StringBuilder");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.DUP);
        find.addOpcode(Opcode.INVOKESPECIAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method java/lang/StringBuilder.<init>:()V");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.LDC);
        result = findConstantPoolReference(Item.getConstPool(), "// String The ");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.ALOAD_0);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(), "// Method getName:()Ljava/lang/String;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.LDC_W);
        result = findConstantPoolReference(Item.getConstPool(), "// String  would be destroyed.");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.toString:()Ljava/lang/String;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Communicator.sendNormalServerMessage:(Ljava/lang/String;)V");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.ICONST_0);
        find.addOpcode(Opcode.IRETURN);
        try {
            BytecodeTools.byteCodePrint(
                    "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\meFind.txt",
                    find.toCodeAttribute().iterator());
        } catch (FileNotFoundException ignored){}
        //</editor-fold>

        //<editor-fold desc="replace bytecode construction">
        Bytecode replace = new Bytecode(Item.getConstPool());
        replace.addOpcode(Opcode.ALOAD_0); // put item on stack     #4161
        replace.addOpcode(Opcode.ALOAD);  //                          #4162
        replace.add(8); // put the bulk container target on the stack
        replace.addOpcode(Opcode.INVOKESTATIC); //                      #4164
        result = BytecodeTools.addConstantPoolReference(Item.getConstPool(),
                "// Method com/Joedobo27/farmbarrelmod/FarmBarrelMod.canItemGoInBulkHook:(Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;)Z");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.ISTORE);  //                           #4167
        replace.add(15);
        replace.addOpcode(Opcode.ILOAD); //                             #4169
        replace.add(15);
        replace.addOpcode(Opcode.IFNE); //     jump to #4215               #4171
        replace.add(0, 44);
        replace.addOpcode(Opcode.ALOAD_1); //                           #4174
        replace.addOpcode(Opcode.IFNULL); // jump to        4213; = 38     #4175
        replace.add(0, 38);
        replace.addOpcode(Opcode.ALOAD_1); //                           #4178
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                        #4179
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Creature.getCommunicator:()Lcom/wurmonline/server/creatures/Communicator;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.NEW); //                               #4182
        result = findConstantPoolReference(Item.getConstPool(), "// class java/lang/StringBuilder");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.DUP); //                               #4185
        replace.addOpcode(Opcode.INVOKESPECIAL); //                     #4186
        result = findConstantPoolReference(Item.getConstPool(), "// Method java/lang/StringBuilder.<init>:()V");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.LDC); //                               #4189
        result = findConstantPoolReference(Item.getConstPool(), "// String The ");
        replace.add(result[0]);
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4191
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.ALOAD_0); //                           #4194
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4195
        result = findConstantPoolReference(Item.getConstPool(), "// Method getName:()Ljava/lang/String;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4198
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.LDC_W); //                             #4201
        result = findConstantPoolReference(Item.getConstPool(), "// String  would be destroyed.");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4204
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4207
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method java/lang/StringBuilder.toString:()Ljava/lang/String;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.INVOKEVIRTUAL); //                     #4210
        result = findConstantPoolReference(Item.getConstPool(),
                "// Method com/wurmonline/server/creatures/Communicator.sendNormalServerMessage:(Ljava/lang/String;)V");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.ICONST_0); //                          #4213
        replace.addOpcode(Opcode.IRETURN); //                           #4214
                                            //                          #4215
        try {
            BytecodeTools.byteCodePrint(
                    "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\meReplace.txt",
                    replace.toCodeAttribute().iterator());
        } catch (FileNotFoundException ignored) {}
        //</editor-fold>


        JAssistMethodData moveToItem = new JAssistMethodData(Item, "(Lcom/wurmonline/server/creatures/Creature;JZ)Z",
                "moveToItem");
        try {
            BytecodeTools.byteCodePrint(
                    "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\moveToItemByte.txt",
                    moveToItem.getCodeIterator());
        } catch (FileNotFoundException ignored) {}
        CodeReplacer codeReplacer = new CodeReplacer(moveToItem.getCodeAttribute());
        codeReplacer.replaceCode(find.get(), replace.get());

        moveToItem.getMethodInfo().rebuildStackMapIf6(classPool, Item.getClassFile());
    }

    public static boolean canItemGoInBulkHook(Item bulkItem, Item bulkContainer) {
        if (!bulkItem.isBulkItem())
            return false;
        switch (bulkContainer.getTemplateId()){
            case ItemList.hopper:
                // FSB
                return bulkItem.isFood();
            case ItemList.bulkContainer:
                // BSB
                return !bulkItem.isFood();
            default:
                return bulkContainer.isBulkContainer();
        }
    }

    private static void addActionData() throws NotFoundException, CannotCompileException {
        JAssistClassData Action = new JAssistClassData("com.wurmonline.server.behaviours.Action", classPool);
        JAssistClassData SowAction = new JAssistClassData("com.Joedobo27.farmbarrelmod.SowAction$SowActionData", classPool);
        CtField f = new CtField(SowAction.getCtClass(), "SowActionData", Action.getCtClass());
        Action.getCtClass().addField(f);
    }

    public static int getSowBarrelId() {
        return sowBarrelId;
    }

    public static ArrayList<Integer> getSowRadius() {
        return sowRadius;
    }

    public static ArrayList<Integer> getSkillUnlockPoints() {
        return skillUnlockPoints;
    }

}
