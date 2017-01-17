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
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.Joedobo27.farmbarrelmod.Wrap.Actions.*;

class PropagateAction implements ModAction, BehaviourProvider, ActionPerformer {

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
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()){
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
        if (actionId == getActionId() && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()) {
            try {
                int time;
                final float TIME_TO_COUNTER_DIVISOR = 10.0f;
                final float ACTION_START_TIME = 1.0f;
                SowActionData sowActionData;

                if (counter == ACTION_START_TIME) {
                    sowActionData = new SowActionData(new Point(tileX, tileY), action, onSurface, performer, source, encodedTile);
                    setSowActionDataReflect(sowActionData);
                    if (!checkRequirements(sowActionData)) {
                        return true;
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);
                    action.setTimeLeft(sowActionData.totalTime);
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, sowActionData.totalTime);
                } else {
                    time = action.getTimeLeft();
                    sowActionData = getSowActionDataReflect(action);
                    boolean isEndOfTileSowing = action.justTickedSecond() &&
                            (int)counter % (int)(sowActionData.unitSowTimeInterval / TIME_TO_COUNTER_DIVISOR) == 0;
                    if (isEndOfTileSowing) {
                        int cropId = getCropIdReflection(sowActionData.seed.getRealTemplateId());
                        double cropDifficulty = getCropDifficultyReflection(cropId);

                        // skill check and use the unit time in sowActionData as counts
                        Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                        double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
                        farmingSkill.skillCheck(cropDifficulty, bonus, false, sowActionData.unitSowTimeInterval / TIME_TO_COUNTER_DIVISOR);
                        // damage barrel

                        // consume some stamina

                        // change tile to a farm tile and update all the appropriate data.
                        /*
                            TileData is 1111 1111 or 0xFF or byte.
                            int tileAgeMask = 0B01110000 // 0 to 7 for base 10.
                            boolean isFarmedMask = 0B10000000 // 0 or 1 for base 10.
                            int cropTypeMask = 0B00001111 // 0 to 15 for base 10
                            ...
                            1000 0000 = 128 = isFarmedMask as true + tileAge of 0. Thus, 128 + cropId & 0xFF.
                         */
                        // pop tile from sowActionData and use returned value to update mesh data, H in Point is an encodedTile int.
                        Point location = sowActionData.popSowTile();
                        Server.setSurfaceTile(location.getX(), location.getY(),Tiles.decodeHeight(location.getH()), Tiles.Tile.TILE_FIELD.id, (byte) (128 + cropId & 0xFF));
                        Players.getInstance().sendChangedTile(location.getX(), location.getY(), onSurface, false);
                        /*
                        int worldResource = Server.getWorldResource(tilex, tiley); // worldResource is a 0xFFFF size.
                        int farmedCountMask = 0B1111 1000 0000 0000 - 0 to 248 tho it should never exceed 5.
                        int farmedChanceMask = 0B0000 0111 1111 1111 - 0 to 2047
                         */

                        int resource = (int) (100.0 - farmingSkill.getKnowledge() + source.getQualityLevel() + (source.getRarity() * 20) + (action.getRarity() * 50));
                        Server.setWorldResource(location.getX(), location.getY(), resource);
                        CropTilePoller.addCropTile(location.getH(), location.getX(), location.getY(), cropId, onSurface);
                        // consume some seed volume in barrel
                        sowActionData.consumeSeed();

                    }
                    if (counter > time / TIME_TO_COUNTER_DIVISOR) {
                        logger.log(Level.INFO, "counter > time: true");
                        return true;
                    }
                }
            }catch (Exception e) {
                logger.log(Level.INFO, "This action does not exist?", e);
                return true;
            }
        }
       return false;
    }

    private class SowActionData {
        private Creature performer;
        private Item barrel;
        private Item seed;
        private int seedCount;
        private LinkedList<Point> points;  // H is an encodedTile int.
        private Action action;
        private int unitSowTimeInterval;
        private int totalTime;
        private double modifiedKnowledge;

        SowActionData(Point centerTile, Action action, boolean surfaced, Creature performer, Item barrel, int encodedTile) {
            this.action = action;
            this.performer = performer;
            this.barrel = barrel;


            points = new LinkedList<>();
            IntStream.range(centerTile.getX() - getSowBarrelRadius(), centerTile.getX() + getSowBarrelRadius() + 1)
                    .forEach( posX ->
                            IntStream.range(centerTile.getY() - getSowBarrelRadius(), centerTile.getY() + getSowBarrelRadius() + 1)
                                    .forEach(posY -> {
                                        points.add(new Point(posX, posY));
                                        points.getLast().setH(encodedTile);
                                    })
                    );
            points = points.stream()
                    .filter(value -> isTileCompatibleWithSeed(value.getX(), value.getY(), surfaced))
                    .collect(Collectors.toCollection(LinkedList::new));

            seed = Arrays.stream(barrel.getAllItems(false))
                    .findFirst()
                    .orElse(null);
            if (seed != null && seed.isBulkItem() && seedIsSeed()) {
                int descriptionCount = Integer.parseInt(seed.getDescription().replaceAll("x",""));
                int volumeCount = Math.floorDiv(seed.getWeightGrams(), seed.getRealTemplate().getVolume());
                seedCount = Math.min(descriptionCount, volumeCount);
            } else {
                seedCount = 0;
            }

            totalTime = getInitialActionTime();
        }

        private int getSowDimension() {
            return (getSowBarrelRadius() * 2) + 1;
        }

        private int getSowBarrelRadius() {
            int i = barrel.getData1();
            // Item.getData1() returns -1 when the Item instance's data field is null.
            return i == -1 ? 0 : i;
        }

        private boolean enoughSeedForSowing() {
            return seedCount >= getSowTileCount();
        }

        private boolean seedIsSeed() {
            return seed != null && seed.getRealTemplate().isSeed();
        }

        private int getInitialActionTime(){
            Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
            double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
            Item lowestQualityItem = seed.getQualityLevel() > barrel.getQualityLevel() ? seed : barrel;
            double knowledge = farmingSkill.getKnowledge(lowestQualityItem, bonus);
            modifiedKnowledge = knowledge;
            final float multiplier = 1.3f / Servers.localServer.getActionTimer();
            double time = (100.0 - knowledge) * multiplier;

            // woa
            if (barrel != null && barrel.getSpellSpeedBonus() > 0.0f) {
                time = 30.0 + time * (1.0 - 0.2 * barrel.getSpellSpeedBonus() / 100.0);
            } else {
                time += 30.0;
            }
            //rare barrel item, 10% speed reduction per rarity level.
            int barrelRarity = barrel.getRarity();
            double rarityBarrelBonus = 1 - (barrelRarity == 0 ? 1 : barrelRarity * 0.1);
            time *= rarityBarrelBonus == 0 ? 1 : rarityBarrelBonus;
            //rare sowing action, 30% speed reduction per rarity level.
            int actionRarity = action.getRarity();
            double rarityActionBonus = 1 - (actionRarity == 0 ? 1 : actionRarity * 0.3);
            time *= rarityActionBonus == 0 ? 1 : rarityActionBonus;

            // In order to ease use of modulo and trigger on action.justTickedSecond() round away the one's digit in time.
            MathContext mathContext = null;
            if (time < 100) {
                mathContext = new MathContext(1);
            }
            else if (time >= 100 && time < 1000) {
                mathContext = new MathContext(2);
            }
            else if (time >= 1000 && time < 10000) {
                mathContext = new MathContext(3);
            }
            BigDecimal bigDecimal = new BigDecimal(time);
            bigDecimal = bigDecimal.round(mathContext);
            int roundedTime = bigDecimal.intValue();
            unitSowTimeInterval = roundedTime;

            roundedTime *= getSowTileCount();
            return Math.max(10, roundedTime);
        }

        int getSowTileCount() {
            return points.size();
        }

        /**
         * LIFO pop.
         *
         * @return WU Point object.
         */
        Point popSowTile() {
            return points.removeFirst();
        }

        private boolean isTileCompatibleWithSeed(int posX, int posY, boolean surfaced) {
            if (!seedIsSeed())
                return false;
            MeshIO sowMesh;
            if (surfaced) {
                sowMesh = Server.surfaceMesh;
            } else {
                sowMesh = Server.caveMesh;
            }

            boolean isTileUnderWater = Terraforming.isCornerUnderWater(posX, posY, surfaced);
            boolean isSeedAquatic;
            switch (seed.getRealTemplateId()){
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
            switch (seed.getRealTemplateId()) {
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
            boolean seedsCompatibleWithTile = seedNeededTile == Tiles.decodeType(sowMesh.getTile(posX, posY));

            return isTileUnderWater == isSeedAquatic && isSlopeMinimal && seedsCompatibleWithTile;
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
    }

    private boolean checkRequirements(SowActionData sowActionData) {
        boolean noSeedWithin = Objects.equals(sowActionData.seed, null);
        if (noSeedWithin) {
            sowActionData.performer.getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return false;
        }
        ItemTemplate seedTemplate = sowActionData.seed.getRealTemplate();
        if (!sowActionData.seedIsSeed()) {
            sowActionData.performer.getCommunicator().sendNormalServerMessage("Only seed can be sown.");
            return false;
        }
        int sowBarrelRadius = sowActionData.getSowBarrelRadius();
        String sowArea = sowActionData.getSowDimension() + " by " + sowActionData.getSowDimension() + " area";
        boolean farmSkillNotEnoughForBarrelRadius = getMaxRadiusFromFarmSkill(sowActionData.performer) < sowBarrelRadius;
        if (farmSkillNotEnoughForBarrelRadius){
            sowActionData.performer.getCommunicator().sendNormalServerMessage( "You don't have enough farming skill to sow a " + sowArea + ".");
            return false;
        }
        if (!sowActionData.enoughSeedForSowing()) {
            String seedName = seedTemplate.getName();
            sowActionData.performer.getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea + ".");
            return false;
        }
        if (sowActionData.getSowTileCount() < 1) {
            sowActionData.performer.getCommunicator().sendNormalServerMessage("The " + sowArea + " needs at least one tile that can be sown");
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
            ReflectionUtil.setPrivateField(sowActionData.action, ReflectionUtil.getField(Class.forName(Action.class.getName()), "sowActionData"),
                    sowActionData);
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private static SowActionData getSowActionDataReflect(Action action) {
        SowActionData toReturn = null;
        try {
            toReturn = ReflectionUtil.getPrivateField(action, ReflectionUtil.getField(Class.forName(Action.class.getName()), "sowActionData"));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
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
