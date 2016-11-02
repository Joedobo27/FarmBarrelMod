package com.Joedobo27.farmbarrelmod;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.CropTilePoller;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.Joedobo27.farmbarrelmod.Wrap.Actions.*;

class PropagateAction implements WurmServerMod, ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;

    PropagateAction(){
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Propagate", "Propagating", new int[] {ACTION_FATIGUE.getId(), ACTION_NON_LIBILAPRIEST.getId(),
                ACTION_MISSION.getId(), ACTION_SHOW_ON_SELECT_BAR.getId(), ACTION_ENEMY_ALWAYS.getId() });
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile){
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelId()){
            //noinspection ArraysAsListWithZeroOrOneArgument
            return Arrays.asList(actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public short getActionId(){
        return actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId == getActionId() && source.getTemplateId() == FarmBarrelMod.getSowBarrelId()) {
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
                        return true;
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);

                    time = getInitialActionTime(sowBarrelData, action, sowActionData);
                    performer.getCurrentAction().setTimeLeft(time);
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
                } else {
                    time = action.getTimeLeft();
                    sowActionData = getSowActionDataReflect(action);
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
                        Server.setSurfaceTile(tileX, tileY, Tiles.decodeHeight(encodedTile), Tiles.Tile.TILE_FIELD.id, (byte) (128 + cropId & 0xFF));
                        Players.getInstance().sendChangedTile(tileX, tileY, onSurface, false);
                        int resource = (int) (100.0 - sowActionData.modifiedKnowledge + source.getQualityLevel() + source.getRarity() * 20 + action.getRarity() * 50);
                        Server.setWorldResource(tileX, tileY, resource);
                        CropTilePoller.addCropTile(encodedTile, tileX, tileY, cropId, onSurface);
                        // pop tile from sowActionData
                        sowActionData.popSowTile();
                    }
                    if (counter * 10.0f > time) {
                        return true;
                    }
                }
                return false;
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
        ArrayList<Integer> sowRadius = FarmBarrelMod.getSowRadius();
        ArrayList<Integer> skillUnlockPoints = FarmBarrelMod.getSkillUnlockPoints();

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
}
