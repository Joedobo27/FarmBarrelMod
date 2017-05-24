package com.joedobo27.farmbarrelmod;


import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

class HarvestAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;

    HarvestAction(){
        this.actionId = (short) ModActions.getNextActionId();
        this.actionEntry = ActionEntry.createEntry(this.actionId, "Harvest", "harvesting", new int[] {ACTION_FATIGUE.getId(),
                ACTION_NON_LIBILAPRIEST.getId(), ACTION_NEED_FOOD.getId(), ACTION_ENEMY_ALWAYS.getId() });
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
    public short getActionId(){
        return this.actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile) {
        if (source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId() && TileUtilities.isFarmTile(encodedTile) &&
                TileUtilities.performerIsWithinDistance(performer, tileX, tileY, 2)){
            return Collections.singletonList(this.actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public boolean action(Action action, Creature performer, Item barrel, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short aActionId, float counter) {
        if (aActionId == this.actionId && barrel != null && barrel.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId() && TileUtilities.isFarmTile(encodedTile)){
            int time;
            final float TIME_TO_COUNTER_DIVISOR = 10.0f;
            final float ACTION_START_TIME = 1.0f;
            String youMessage;
            String broadcastMessage;

            if (counter == ACTION_START_TIME) {
                if (hasAFailureCondition(performer, barrel, tileX, tileY, onSurface, heightOffset, encodedTile, aActionId, counter))
                    return true;
                youMessage = String.format("You start %s.", action.getActionEntry().getVerbString());
                broadcastMessage = String.format("%s starts to %s.", performer.getName(), action.getActionString());
                performer.getCommunicator().sendNormalServerMessage(youMessage);
                Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
                time = (int)FarmBarrelMod.getBaseUnitActionTime(barrel, performer, action, SkillList.FARMING, SkillList.BODY_STRENGTH);
                action.setTimeLeft(time);
                performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
                performer.getStatus().modifyStamina(-1000.0f);
                return false;
            }
            time = action.getTimeLeft();
            if (counter >= (time / TIME_TO_COUNTER_DIVISOR)){
                if (hasAFailureCondition(performer, barrel, tileX, tileY, onSurface, heightOffset, encodedTile, aActionId, counter))
                    return true;
                if (barrel.getData1() == -1){
                    FarmBarrelMod.encodeIsSeed(barrel, false);
                    FarmBarrelMod.encodeContainedQuality(barrel, 0);
                    FarmBarrelMod.encodeContainedCropId(barrel, Crops.EMPTY.getId());
                }
                TilePos harvestTilePos = TilePos.fromXY(tileX, tileY);
                // do skill roll and power result is ignored.
                double cropDifficulty = Crops.getCropDifficultyFromCropId(TileUtilities.getFarmTileCropId(harvestTilePos));
                Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_STRENGTH).getKnowledge() / 5);
                farmingSkill.skillCheck(cropDifficulty, bonus, false, counter);
                // damage barrel
                barrel.setDamage(barrel.getDamage() + 0.0015f * barrel.getDamageModifier());
                // consume stamina
                performer.getStatus().modifyStamina(-10000.0f);
                //finally put the crops in the barrel.
                int farmCount = TileUtilities.getFarmTileTendCount(harvestTilePos);
                double modifiedSkill = farmingSkill.getKnowledge(barrel, bonus);
                double baseYield = (modifiedSkill / 15);
                double rakeYield = farmCount * baseYield / 2;
                int finalYield = (int)Math.round(baseYield + rakeYield);
                FarmBarrelMod.encodeContainedCropId(barrel, TileUtilities.getFarmTileCropId(harvestTilePos));
                setContainedQuality(barrel, TileUtilities.getFarmTileCropId(harvestTilePos), finalYield, modifiedSkill);
                setBarrelWeight(barrel, TileUtilities.getFarmTileCropId(harvestTilePos), finalYield, performer);
                // update the custom naming tag even if it's already of correct name.
                barrel.updateName();
                // change tile to a dirt tile and update all the appropriate data.
                Server.setWorldResource(harvestTilePos.x, harvestTilePos.y,  0);
                Server.setSurfaceTile(harvestTilePos.x, harvestTilePos.y, TileUtilities.getSurfaceHeight(harvestTilePos), Tiles.Tile.TILE_DIRT.id, (byte)0);
                Players.getInstance().sendChangedTile(harvestTilePos.x, harvestTilePos.y, onSurface, true);
                // do action finished messages.
                youMessage = String.format("You finish %s.", action.getActionEntry().getVerbString());
                broadcastMessage = String.format("%s finishes %s.", performer.getName(), action.getActionString());
                performer.getCommunicator().sendNormalServerMessage(youMessage);
                Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
                return true;
            }
            return false;
        }
        return true;
    }

    private static boolean hasAFailureCondition(Creature performer, Item barrel, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short aActionId, float counter){
        TilePos farmTilePos = TilePos.fromXY(tileX, tileY);


        boolean cropNotRipe = TileUtilities.getFarmTileAge(farmTilePos) < 5 || TileUtilities.getFarmTileAge(farmTilePos) > 6;
        if (cropNotRipe) {
            performer.getCommunicator().sendNormalServerMessage("The crops aren't ripe enough to harvest.");
            return true;
        }
        ItemTemplate cropTemplate;
        ItemTemplate seedTemplate;
        try {
            cropTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getProductTemplateIdFromCropId(
                    TileUtilities.getFarmTileCropId(farmTilePos)));
            seedTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getSeedTemplateIdFromCropId(
                    TileUtilities.getFarmTileCropId(farmTilePos)));
        }catch (NoSuchTemplateException e){
             logger.warning(e.getMessage());
             performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
             return true;
        }
        if (!TileUtilities.performerIsWithinDistance(performer, tileX, tileY, 2)) {
            performer.getCommunicator().sendNormalServerMessage("That tile is too far away.");
            return true;
        }

        boolean barrelContainsSeeds = cropTemplate.getTemplateId() != seedTemplate.getTemplateId() && FarmBarrelMod.decodeIsSeed(barrel);
        if (barrelContainsSeeds) {
            performer.getCommunicator().sendNormalServerMessage("The seeds in the barrel aren't the same item as what would be harvested.");
            return true;
        }
        boolean barrelIsFull = barrel.getWeightGrams() - 1000 != 0 && (barrel.getWeightGrams() - 1000) / cropTemplate.getWeightGrams()
                >= 2047;
        if (barrelIsFull) {
             performer.getCommunicator().sendNormalServerMessage("There is not enough space for more crops, it's full.");
             return true;
        }

        boolean containedCropNoMatchHarvest = FarmBarrelMod.decodeContainedCropId(barrel) != Crops.EMPTY.getId()
                && Crops.getProductTemplateIdFromCropId(FarmBarrelMod.decodeContainedCropId(barrel))
                 != cropTemplate.getTemplateId();
        if (containedCropNoMatchHarvest) {
            performer.getCommunicator().sendNormalServerMessage("The barrel can only hold one type of crop.");
            return true;
        }

        return false;
    }

    private void setBarrelWeight(Item barrel, int harvestCropId ,int harvestCount, Creature performer){
        try{
            final int MAX_BARREL_CAPACITY = 2047;
            int templateGrams = ItemTemplateFactory.getInstance().getTemplate(Crops.getProductTemplateIdFromCropId(harvestCropId)).getWeightGrams();
            int barrelQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / templateGrams;
            if (barrelQuantity + harvestCount > MAX_BARREL_CAPACITY) {
                performer.getCommunicator().sendNormalServerMessage("The barrel is full and some of the harvest may have been wasted.");
                harvestCount = MAX_BARREL_CAPACITY - barrelQuantity;
            }
            barrel.setWeight(1000 + (harvestCount * templateGrams) + (barrelQuantity * templateGrams), false);
        }catch (Exception ignored){
            // NoSuchTemplateException is pre-verified in hasAFailureCondition();
        }
    }

    private void setContainedQuality(Item barrel, int harvestCropId, int harvestCount, double harvestQuality){
        try{
            final int MAX_BARREL_CAPACITY = 2047;
            int templateGrams = ItemTemplateFactory.getInstance().getTemplate(Crops.getProductTemplateIdFromCropId(harvestCropId)).getWeightGrams();
            int containedQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / templateGrams;
            if (containedQuantity + harvestCount > MAX_BARREL_CAPACITY) {
                harvestCount = MAX_BARREL_CAPACITY - containedQuantity;
            }
            int containedQuality = FarmBarrelMod.decodeContainedQuality(barrel);
            int weightedAverageQuality = (int)harvestQuality;
            if (containedQuality != 0 && containedQuantity != 0)
                weightedAverageQuality = (int) (((harvestCount * harvestQuality) + (containedQuantity * containedQuality)) / (harvestCount + containedQuantity));
            FarmBarrelMod.encodeContainedQuality(barrel, weightedAverageQuality);
        }catch (Exception ignored){
            // NoSuchTemplateException is pre-verified in hasAFailureCondition();
        }
    }
}
