package com.Joedobo27.farmbarrelmod;


import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.CropTilePoller;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.Joedobo27.farmbarrelmod.BytecodeTools.addConstantPoolReference;
import static com.Joedobo27.farmbarrelmod.BytecodeTools.findConstantPoolReference;

public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener, ServerStartedListener,
        ModAction, ActionPerformer, BehaviourProvider {

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
            takeBytecode();
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

    public short getActionId(){
        return Actions.SOW;
    }

    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile){
        if (performer instanceof Player && source != null && source.getTemplateId() == sowBarrelId){
            return Arrays.asList(Actions.actionEntrys[153]);
        }
        return BehaviourProvider.super.getBehavioursFor(performer, source, tileX, tileY, onSurface, encodedTile);
    }

    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId == Actions.SOW && source.getTemplateId() == FarmBarrelMod.sowBarrelId) {
            try {
                int time;
                SowBarrelData sowBarrelData = new SowBarrelData(performer, source);
                SowActionData sowActionData;
                float ACTION_START_TIME = 1.0f;
                if (counter == ACTION_START_TIME) {
                    sowActionData = new SowActionData(new Point(tileX, tileY), sowBarrelData.getSowBarrelRadius(), action,
                            onSurface, sowBarrelData);
                    setSowActionDataReflect(sowActionData);
                    if (!checkRequirements(sowBarrelData, sowActionData)) {
                        return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, encodedTile, actionId, counter);
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);

                    time = getInitialActionTime(sowBarrelData, action, sowActionData);
                    performer.getCurrentAction().setTimeLeft(time);
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
                    return false;
                } else {
                    time = action.getTimeLeft();
                    sowActionData = getSowActionDataReflect(action);
                }
                boolean isEndOfTileSowing = action.justTickedSecond() && action.currentSecond() % sowActionData.getSowTileCount() == 0;
                if (isEndOfTileSowing) {
                    int cropId = getCropIdReflection(sowBarrelData.seed.getRealTemplateId());
                    double cropDifficulty = getCropDifficultyReflection(cropId);

                    // skill check and use the unit time in sowActionData as counts
                    Skill farmingSkill = sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                    double bonus = Math.max(10, sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
                    float actionTimeToCounterConvert = 10.0f;
                    farmingSkill.skillCheck(cropDifficulty, bonus, false, sowActionData.unitSowTimeInterval / actionTimeToCounterConvert);
                    // consume some seed volume in barrel
                    sowBarrelData.consumeSeed();
                    // damage barrel

                    // consume some stamina

                    // change tile to a farm tile and update all the appropriate data.
                    Server.setSurfaceTile(tileX, tileY, Tiles.decodeHeight(encodedTile), Tiles.Tile.TILE_FIELD.id, (byte)(128 + cropId & 0xFF));
                    Players.getInstance().sendChangedTile(tileX, tileY, onSurface, false);
                    int resource = (int)(100.0 - sowActionData.modifiedKnowledge + source.getQualityLevel() + source.getRarity() * 20 + action.getRarity() * 50);
                    Server.setWorldResource(tileX, tileY, resource);
                    CropTilePoller.addCropTile(encodedTile, tileX, tileY, cropId, onSurface);
                    // pop tile from sowActionData
                    sowActionData.popSowTile();
                }
            }catch (NoSuchActionException e) {
                logger.log(Level.INFO, "This action does not exist?", e);
            }
        }
        return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, encodedTile, actionId, counter);
    }

    private class SowBarrelData {
        private Creature performer;
        private Item barrel;
        private Item seed;
        private int seedCount;

        SowBarrelData(Creature performer, Item barrel) {
            this.performer = performer;
            this.barrel = barrel;
            this.seed = Arrays.stream(barrel.getAllItems(false))
                    .findFirst()
                    .orElse(null);
            if (seed.isBulkItem() && seedIsSeed()) {
                int descriptionCount = Integer.parseInt(seed.getDescription().replaceAll("x",""));
                int volumeCount = Math.floorDiv(seed.getWeightGrams(), seed.getRealTemplate().getWeightGrams());
                seedCount = Math.min(descriptionCount, volumeCount);
            } else {
                this.seedCount = 0;
            }
        }

        private int getSowBarrelRadius() {
            int i = this.barrel.getData1() & 0x0f;
            // Item.getData1() returns -1 when the Item instance's data field is null.
            return i == -1 ? 0 : i;
        }

        private boolean seedIsSeed() {
            return seed.getRealTemplate().isSeed();
        }

        private boolean seedIsGreaterSow() {
            int seedCountNeeded = (int) Math.pow((getSowBarrelRadius() * 2) + 1, 2);
            return seedCount > seedCountNeeded;
        }

        @SuppressWarnings("ConstantConditions")
        private void consumeSeed() {
            // The Item.setWeight() methods uses ambiguous boolean logic control args. Added named for readability.
            boolean updateOwner = false;
            boolean destroyOnWeightZero = true;

            // Bulk items (ItemList.bulkItem or ID int 669) store volume in the weight DB entry.
            int totalBulkVolume = seed.getWeightGrams();
            int reduceBulkVolume = seedCount * seed.getRealTemplate().getWeightGrams();
            int newVolume = totalBulkVolume - reduceBulkVolume;
            seed.setWeight(newVolume, destroyOnWeightZero, updateOwner);
        }

        private int getSowDimension() {
            return (getSowBarrelRadius() * 2) + 1;
        }
    }

    private class SowActionData {
        private LinkedList<Point> points;
        private Action action;
        private short unitSowTimeInterval;
        private double modifiedKnowledge;

        SowActionData(Point centerTile, int radius, Action action, boolean surfaced, SowBarrelData sowBarrelData) {
            this.action = action;

            points = new LinkedList<>();
            IntStream.range(centerTile.getX() - radius, centerTile.getX() + radius + 1)
                    .forEach( posX ->
                            IntStream.range(centerTile.getY() - radius, centerTile.getY() + radius + 1)
                                    .forEach(posY -> points.add(new Point(posX, posY)))
                    );
            points = points.stream()
                    .filter(value -> isTileCompatibleWithSeed(value.getX(), value.getY(), surfaced, sowBarrelData))
                    .collect(Collectors.toCollection(LinkedList::new));
        }

        int getSowTileCount() {
            return points.size();
        }

        void setUnitSowTimeInterval(short unitSowTimeInterval) {
            this.unitSowTimeInterval = unitSowTimeInterval;
        }

        void setModifiedKnowledge(double knowledge) { this.modifiedKnowledge = knowledge; }

        /**
         * LIFO pop.
         *
         * @return WU Point object.
         */
        Point popSowTile() {
            return points.removeFirst();
        }

        private boolean isTileCompatibleWithSeed(int posX, int posY, boolean surfaced, SowBarrelData sowBarrelData) {
            MeshIO sowMesh;
            if (surfaced) {
                sowMesh = Server.surfaceMesh;
            } else {
                sowMesh = Server.caveMesh;
            }

            boolean isTileUnderWater = Terraforming.isCornerUnderWater(posX, posY, surfaced);
            boolean isSeedAquatic;
            switch (sowBarrelData.seed.getRealTemplateId()){
                case ItemList.reedSeed:
                    isSeedAquatic = true;
                    break;
                case ItemList.rice:
                    isSeedAquatic = true;
                    break;
                default:
                    isSeedAquatic = false;
            }

            boolean isSlopeMinimal = Terraforming.isFlat(posX, posY, surfaced, 4);

            byte seedNeededTile;
            switch (sowBarrelData.seed.getRealTemplateId()) {
                case ItemList.mushroomBlack:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                case ItemList.mushroomBlue:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                case ItemList.mushroomBrown:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                case ItemList.mushroomGreen:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                case ItemList.mushroomRed:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                case ItemList.mushroomYellow:
                    seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                    break;
                default:
                    seedNeededTile = Tiles.Tile.TILE_DIRT.id;
            }
            boolean seedsCompatibleWithTile = seedNeededTile == sowMesh.getTile(posX, posY);

            return isTileUnderWater == isSeedAquatic && isSlopeMinimal && seedsCompatibleWithTile;
        }
    }

    private static int getInitialActionTime(SowBarrelData sowBarrelData, Action action, SowActionData sowActionData){
        Skill farmingSkill = sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        double bonus = Math.max(10, sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
        Item lowestQualityItem = sowBarrelData.seed.getQualityLevel() > sowBarrelData.barrel.getQualityLevel() ? sowBarrelData.seed : sowBarrelData.barrel;
        double knowledge = farmingSkill.getKnowledge(lowestQualityItem, bonus);
        sowActionData.setModifiedKnowledge(knowledge);
        final float multiplier = 1.3f / Servers.localServer.getActionTimer();
        double time = (100.0 - knowledge) * multiplier;

        // woa
        if (sowBarrelData.barrel != null && sowBarrelData.barrel.getSpellSpeedBonus() > 0.0f) {
            time = 30.0 + time * (1.0 - 0.2 * sowBarrelData.barrel.getSpellSpeedBonus() / 100.0);
        } else {
            time += 30.0;
        }

        //rare barrel item, 10% speed reduction per rarity level.
        int barrelRarity = sowBarrelData.barrel.getRarity();
        double rarityBarrelBonus = barrelRarity == 0 ? 1 : barrelRarity * 0.1;
        time *= rarityBarrelBonus;
        //rare sowing action, 30% speed reduction per rarity level.
        int actionRarity = action.getRarity();
        double rarityActionBonus = actionRarity == 0 ? 1 : actionRarity * 0.3;
        time *= rarityActionBonus;
        sowActionData.setUnitSowTimeInterval((short) time);

        time *= sowActionData.getSowTileCount();
        return (int) Math.max(10, time);
    }

    private static boolean checkRequirements(SowBarrelData sowBarrelData, SowActionData sowActionData) {
        boolean noSeedWithin = Objects.equals(sowBarrelData.seed, null);
        if (noSeedWithin) {
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return false;
        }
        ItemTemplate seedTemplate = sowBarrelData.seed.getRealTemplate();
        if (!sowBarrelData.seed.isSeed()) {
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("Only seed can be sown.");
            return false;
        }
        int sowBarrelRadius = sowBarrelData.getSowBarrelRadius();
        String sowArea = sowBarrelData.getSowDimension() + " by " + sowBarrelData.getSowDimension() + " area.";
        boolean farmSkillNotEnoughForBarrelRadius = getMaxRadiusFromFarmSkill(sowBarrelData.performer) < sowBarrelRadius;
        if (farmSkillNotEnoughForBarrelRadius){
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage( "You don't have enough farming skill to sow a " + sowArea);
            return false;
        }
        if (!sowBarrelData.seedIsGreaterSow()) {
            String seedName = seedTemplate.getName();
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea);
            return false;
        }
        if (sowActionData.getSowTileCount() < 1) {
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("The " + sowArea + " needs at least one tile that can be sown");
            return false;
        }

        return true;
    }

    private static int getMaxRadiusFromFarmSkill(Creature performer) {
        double farmingLevel = performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge();
        ArrayList<Integer> sowRadius = FarmBarrelMod.sowRadius;
        ArrayList<Integer> skillUnlockPoints = FarmBarrelMod.skillUnlockPoints;

        int maxIndex = skillUnlockPoints.size() - 1;
        for (int i = 0; i <= maxIndex; i++) {
            if (i == maxIndex && farmingLevel >= skillUnlockPoints.get(i)) {
                return sowRadius.get(i);
            } else if (farmingLevel >= skillUnlockPoints.get(i) && farmingLevel < skillUnlockPoints.get(i + 1)) {
                return sowRadius.get(i);
            }
        }
        return sowRadius.get(0);
    }

    @SuppressWarnings("unused")
    private void moveInventory() {
        //<editor-fold desc="Modification document">
        /*
        was...
        if (targetItem.getOwnerId() > 0L && (targetItem.isBulkItem() || targetItem.isBulkContainer())) {
            this.sendNormalServerMessage("You are not allowed to do that.");
            return;
        }

        becomes...

         */
        //</editor-fold>
    }

    private void takeBytecode() throws NotFoundException, BadBytecode {
        //<editor-fold desc="Modification document">
        /*
        Taking something calls MethodItems.take().
        was...
        if ((target.isBulkContainer() || target.isTent()) && !target.isEmpty()) {
                return TakeResultEnum.TARGET_FILLED_BULK_CONTAINER;
        }

        becomes...
        boolean isOnlyTakeWhenEmpty = isOnlyTakeWhenEmptyHook(target);
        if (isOnlyTakeWhenEmpty) {
            return TakeResultEnum.TARGET_FILLED_BULK_CONTAINER;
        }
        */
        //</editor-fold>

        JAssistClassData MethodsItems = new JAssistClassData("com.wurmonline.server.behaviours.MethodsItems", classPool);

        //<editor-fold desc="find Bytecode construction">
        Bytecode find = new Bytecode(MethodsItems.getConstPool());
        find.addOpcode(Opcode.ALOAD_2);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        byte[] result = findConstantPoolReference(MethodsItems.getConstPool(),
                "// Method com/wurmonline/server/items/Item.isBulkContainer:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFNE); // 97: ifne          107
        find.add(0, 10);
        find.addOpcode(Opcode.ALOAD_2);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(MethodsItems.getConstPool(), "// Method com/wurmonline/server/items/Item.isTent:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFEQ); // 104: ifeq          118
        find.add(0, 14);
        find.addOpcode(Opcode.ALOAD_2);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        result = findConstantPoolReference(MethodsItems.getConstPool(), "// Method com/wurmonline/server/items/Item.isEmpty:()Z");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.IFNE); // 111: ifne          118
        find.add(0, 7);
        find.addOpcode(Opcode.GETSTATIC);
        result = findConstantPoolReference(MethodsItems.getConstPool(),
                "// Field com/wurmonline/server/behaviours/TakeResultEnum.TARGET_FILLED_BULK_CONTAINER:Lcom/wurmonline/server/behaviours/TakeResultEnum;");
        find.add(result[0], result[1]);
        find.addOpcode(Opcode.ARETURN);
        //</editor-fold>

        //<editor-fold desc="replace Bytecode construction">
        Bytecode replace = new Bytecode(MethodsItems.getConstPool());
        replace.addOpcode(Opcode.ALOAD_2); // put target on stack
        replace.addOpcode(Opcode.INVOKESTATIC);
        result = addConstantPoolReference(MethodsItems.getConstPool(),
                "// Method com/Joedobo27/farmbarrelmod/FarmBarrelMod.isOnlyTakeWhenEmptyHook:(Lcom/wurmonline/server/items/Item;)Z");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.ISTORE);
        replace.add(4);
        replace.addOpcode(Opcode.ILOAD);
        replace.add(4);
        replace.addOpcode(Opcode.IFEQ); // 9: ifne   16
        replace.add(0, 7);
        replace.addOpcode(Opcode.GETSTATIC);
        result = findConstantPoolReference(MethodsItems.getConstPool(),
                "// Field com/wurmonline/server/behaviours/TakeResultEnum.TARGET_FILLED_BULK_CONTAINER:Lcom/wurmonline/server/behaviours/TakeResultEnum;");
        replace.add(result[0], result[1]);
        replace.addOpcode(Opcode.ARETURN);
        //</editor-fold>

        JAssistMethodData take = new JAssistMethodData(MethodsItems,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Lcom/wurmonline/server/behaviours/TakeResultEnum;",
                "take");

        CodeReplacer codeReplacer = new CodeReplacer(take.getCodeAttribute());
        codeReplacer.replaceCode(find.get(), replace.get());

        take.getMethodInfo().rebuildStackMapIf6(classPool, MethodsItems.getClassFile());
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
        find.add(result[1]);
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
        find.add(result[1]);
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
        replace.addOpcode(Opcode.IFEQ); //     jump to #4215               #4171
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
        replace.add(result[1]);
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

    @SuppressWarnings("unused")
    public static boolean isRestrictedMovementBulk(Item targetItem) {
        if (targetItem.getOwnerId() <= 0L)
            return false;
        switch (targetItem.getTemplateId()){
            case ItemList.hopper:
                //FSB
                return true;
            case ItemList.bulkContainer:
                //BSB
                return true;
            default:
                return false;
        }
    }

    @SuppressWarnings("unused")
    public static boolean isOnlyTakeWhenEmptyHook(Item target) {
        switch (target.getTemplateId()){
            case ItemList.hopper:
                //FSB
                return !target.isEmpty();
            case ItemList.bulkContainer:
                //BSB
                return !target.isEmpty();
            case ItemList.tentExploration:
                return !target.isEmpty();
            case ItemList.tentMilitary:
                return !target.isEmpty();
            case ItemList.tent:
                return !target.isEmpty();
            default:
                return false;
        }
    }

    @SuppressWarnings("unused")
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
        JAssistClassData SowAction = new JAssistClassData("com.Joedobo27.farmbarrelmod.FarmBarrelMod$SowActionData", classPool);
        CtField f = new CtField(SowAction.getCtClass(), "SowActionData", Action.getCtClass());
        Action.getCtClass().addField(f);
    }

    private static double getCropDifficultyReflection(int cropId) {
        double d = -1;
        try {
            d = ReflectionUtil.callPrivateMethod(Class.forName("com.wurmonline.server.behaviours.Crops"),
                    ReflectionUtil.getMethod(Class.forName("com.wurmonline.server.behaviours.Crops"),
                            "getDifficultyFor"), cropId);
        } catch (ClassNotFoundException | NoSuchMethodException |IllegalAccessException | InvocationTargetException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return d;
    }

    private static int getCropIdReflection(int templateId) {
        int i = -1;
        try {
            i = ReflectionUtil.callPrivateMethod(Class.forName("com.wurmonline.server.behaviours.Crops"),
                    ReflectionUtil.getMethod(Class.forName("com.wurmonline.server.behaviours.Crops"),
                            "getTemplateId"), templateId);
        } catch (ClassNotFoundException | NoSuchMethodException |IllegalAccessException | InvocationTargetException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return i;
    }

    /**
     * Have to use reflection to set fields added at runtime with JA.
     * Further, the argument object reference needs to persist for the life of the action. An object reference needs
     * to be set for the action instance. This action method for SowAction is called multiple times during the life of the
     * action referenced in sowActionData. Any object reference solely referenced in that method would get
     * destroyed after each of its calls.
     * @param sowActionData SowAction object type.
     */
    private static void setSowActionDataReflect(SowActionData sowActionData) {
        try {
            ReflectionUtil.setPrivateField(sowActionData.action, ReflectionUtil.getField(Action.class.getClass(), "sowActionData"),
                    sowActionData);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private static SowActionData getSowActionDataReflect(Action action) {
        SowActionData toReturn = null;
        try {
            toReturn = ReflectionUtil.getPrivateField(action, ReflectionUtil.getField(Action.class.getClass(), "sowActionData"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return toReturn;
    }

}
