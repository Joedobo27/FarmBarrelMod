package com.joedobo27.farmbarrelmod;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.CropTilePoller;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

class SowAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;
    private static WeakHashMap<Action, SowActionData> actionListener;


    SowAction(){
        actionListener = new WeakHashMap<>();
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Sow", "sowing", new int[] {ACTION_FATIGUE.getId(),
                ACTION_NON_LIBILAPRIEST.getId(), ACTION_MISSION.getId(), ACTION_ENEMY_ALWAYS.getId() });
        ModActions.registerAction(actionEntry);
        try {
            ReflectionUtil.setPrivateField(this.actionEntry,
                    ReflectionUtil.getField(Class.forName("com.wurmonline.server.behaviours.ActionEntry"), "maxRange"),
                    8);
            ReflectionUtil.setPrivateField(this.actionEntry,
                    ReflectionUtil.getField(Class.forName("com.wurmonline.server.behaviours.ActionEntry"), "isBlockedByUseOnGroundOnly"),
                    false);
        }catch (Exception ignored){}
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile){
        if (source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId() &&
                (Tiles.decodeType(encodedTile) == Tiles.TILE_TYPE_DIRT || TileUtilities.isFarmTile(encodedTile)) &&
                TileUtilities.performerIsWithinDistance(performer, tileX, tileY, 2)){
            return Collections.singletonList(actionEntry);
        }else {
            return null;
        }
    }


    @Override
    public short getActionId(){
        return actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short aActionId, float counter) {
        if (aActionId == actionId && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId() &&
                TileUtilities.performerIsWithinDistance(performer, tileX, tileY, 2)) {
            try {
                int time;
                final float TIME_TO_COUNTER_DIVISOR = 10.0f;
                final float ACTION_START_TIME = 1.0f;
                SowActionData sowActionData;

                if (counter == ACTION_START_TIME) {
                    sowActionData = new SowActionData(new Point(tileX, tileY, encodedTile), action, onSurface, performer, source, encodedTile);
                    actionListener.put(action, sowActionData);
                    if (hasAFailureCondition(sowActionData)) {
                        return true;
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);
                    action.setTimeLeft(sowActionData.getTotalTime());
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, sowActionData.getTotalTime());
                    return false;
                }
                time = action.getTimeLeft();
                sowActionData = actionListener.get(action);
                boolean isEndOfTileSowing = sowActionData.unitTimeJustTicked(counter);
                boolean timeLeft = counter - 1 < time / TIME_TO_COUNTER_DIVISOR;
                boolean tilesLeft = sowActionData.getPoints().size() > 0;
                if (isEndOfTileSowing && timeLeft && tilesLeft) {
                    if (hasAFailureCondition(sowActionData)) {
                        return true;
                    }
                    int cropId;
                    double cropDifficulty;
                    cropId = Crops.getCropIdFromSeedTemplateId(sowActionData.getSeedTemplateId());
                    cropDifficulty = Crops.getCropDifficultyFromCropId(cropId);

                    // skill check and use the unit time in sowActionData as counts
                    Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                    double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
                    farmingSkill.skillCheck(cropDifficulty, bonus, false, (float)(sowActionData.getUnitSowTimeInterval() / TIME_TO_COUNTER_DIVISOR));
                    // damage barrel
                    source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
                    // consume some stamina
                    performer.getStatus().modifyStamina(-2000.0f);
                    // change tile to a farm tile and update all the appropriate data.
                    // pop tile from sowActionData and use returned value to update mesh data, H in Point is an encodedTile int.
                    Point location = sowActionData.popSowTile();
                    if (cropId <= 15)
                        Server.setSurfaceTile(location.getX(), location.getY(),Tiles.decodeHeight(location.getH()), Tiles.Tile.TILE_FIELD.id,
                                TileUtilities.encodeSurfaceFarmTileData(true, 0, cropId));
                    else if (cropId > 15)
                        Server.setSurfaceTile(location.getX(), location.getY(),Tiles.decodeHeight(location.getH()), Tiles.Tile.TILE_FIELD2.id,
                                TileUtilities.encodeSurfaceFarmTileData(true, 0, cropId));
                    Players.getInstance().sendChangedTile(location.getX(), location.getY(), onSurface, false);
                    int resource = TileUtilities.encodeResourceFarmTileData(0, (int)Math.min(2047, 100.0 - farmingSkill.getKnowledge() +
                            source.getQualityLevel() +(source.getRarity() * 20) + (action.getRarity() * 50)));
                    Server.setWorldResource(location.getX(), location.getY(), resource);
                    CropTilePoller.addCropTile(location.getH(), location.getX(), location.getY(), cropId, onSurface);
                    // consume some seed volume in barrel
                    sowActionData.consumeSeed();

                }
                if (counter - 1 >= (time / TIME_TO_COUNTER_DIVISOR)) {
                    performer.getCommunicator().sendNormalServerMessage("You finish " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " finishes " + action.getActionString() + ".", performer, 5);
                    return true;
                }
            }catch (Exception e) {
                logger.log(Level.INFO, "This action does not exist?", e);
                return true;
            }
        }
        return false;
    }

    private boolean hasAFailureCondition(SowActionData sowActionData) {
        boolean noSeedWithin = FarmBarrelMod.decodeContainedCropId(sowActionData.getBarrel()) == Crops.EMPTY.getId();
        if (noSeedWithin) {
            sowActionData.getPerformer().getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return true;
        }
        boolean contentsNotSeed = !FarmBarrelMod.decodeIsSeed(sowActionData.getBarrel());
        if (contentsNotSeed) {
            sowActionData.getPerformer().getCommunicator().sendNormalServerMessage("Only seeds can be sown and the barrel has something" +
                    " which isn't a seed.");
            return true;
        }

        double sowBarrelRadius = sowActionData.getSowBarrelRadius();
        String sowArea = sowActionData.getSowDimension() + " by " + sowActionData.getSowDimension() + " area";
        boolean farmSkillNotEnoughForBarrelRadius = getMaxRadiusFromFarmSkill(sowActionData.getPerformer()) < sowBarrelRadius;
        if (farmSkillNotEnoughForBarrelRadius){
            sowActionData.getPerformer().getCommunicator().sendNormalServerMessage( "You don't have enough farming skill to sow a " + sowArea + ".");
            return true;
        }
        if (!sowActionData.enoughSeedForSowing()) {
            String seedName = "ERROR";
            try {
                seedName = Crops.getCropNameFromCropId(FarmBarrelMod.decodeContainedCropId(sowActionData.getBarrel()),
                        FarmBarrelMod.decodeIsSeed(sowActionData.getBarrel()));
            }catch (NoSuchTemplateException | CropsException ignored){}
            sowActionData.getPerformer().getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea + ".");
            return true;
        }
        if (sowActionData.getSowTileCount() < 1) {
            sowActionData.getPerformer().getCommunicator().sendNormalServerMessage("The " + sowArea + " needs at least one tile that can be sown");
            return true;
        }

        return false;
    }

    private static int getMaxRadiusFromFarmSkill(Creature performer) {
        double farmingLevel = performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge();
        ArrayList<Integer> sowRadius = FarmBarrelMod.getSowRadius();
        ArrayList<Double> skillUnlockPoints = FarmBarrelMod.getSkillUnlockPoints();

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

}
