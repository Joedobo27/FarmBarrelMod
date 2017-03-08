package com.joedobo27.farmbarrelmod;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.CropTilePoller;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

class PropagateAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;
    private static WeakHashMap<Action, PropagateActionData> actionListener;

    PropagateAction(){
        actionListener = new WeakHashMap<>();
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
                PropagateActionData propagateActionData;

                if (counter == ACTION_START_TIME) {
                    propagateActionData = new PropagateActionData(new Point(tileX, tileY), action, onSurface, performer, source, encodedTile);
                    actionListener.put(action, propagateActionData);
                    if (!checkRequirements(propagateActionData)) {
                        return true;
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);
                    action.setTimeLeft(propagateActionData.getTotalTime());
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, propagateActionData.getTotalTime());
                    return false;
                }
                time = action.getTimeLeft();
                propagateActionData = actionListener.get(action);
                boolean isEndOfTileSowing = action.justTickedSecond() &&
                        (int)counter % (int)(propagateActionData.getUnitSowTimeInterval() / TIME_TO_COUNTER_DIVISOR) == 0;
                if (isEndOfTileSowing) {
                    int cropId;
                    double cropDifficulty;
                    cropId = Wrap.Crops.getCropIdFromSeedTemplateId(propagateActionData.getSeed().getRealTemplateId());
                    cropDifficulty = Wrap.Crops.getCropDifficultyFromCropId(cropId);

                    // skill check and use the unit time in propagateActionData as counts
                    Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                    double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
                    farmingSkill.skillCheck(cropDifficulty, bonus, false, propagateActionData.getUnitSowTimeInterval() / TIME_TO_COUNTER_DIVISOR);
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
                    // pop tile from propagateActionData and use returned value to update mesh data, H in Point is an encodedTile int.
                    Point location = propagateActionData.popSowTile();
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
                    propagateActionData.consumeSeed();

                }
                if (counter > time / TIME_TO_COUNTER_DIVISOR) {
                    logger.log(Level.INFO, "counter > time: true");
                    return true;
                }
            }catch (Exception e) {
                logger.log(Level.INFO, "This action does not exist?", e);
                return true;
            }
        }
       return false;
    }

    private boolean checkRequirements(PropagateActionData propagateActionData) {
        boolean noSeedWithin = Objects.equals(propagateActionData.getSeed(), null);
        if (noSeedWithin) {
            propagateActionData.getPerformer().getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return false;
        }
        ItemTemplate seedTemplate = propagateActionData.getSeed().getRealTemplate();
        if (!propagateActionData.seedIsSeed()) {
            propagateActionData.getPerformer().getCommunicator().sendNormalServerMessage("Only seed can be sown.");
            return false;
        }
        int sowBarrelRadius = propagateActionData.getSowBarrelRadius();
        String sowArea = propagateActionData.getSowDimension() + " by " + propagateActionData.getSowDimension() + " area";
        boolean farmSkillNotEnoughForBarrelRadius = getMaxRadiusFromFarmSkill(propagateActionData.getPerformer()) < sowBarrelRadius;
        if (farmSkillNotEnoughForBarrelRadius){
            propagateActionData.getPerformer().getCommunicator().sendNormalServerMessage( "You don't have enough farming skill to sow a " + sowArea + ".");
            return false;
        }
        if (!propagateActionData.enoughSeedForSowing()) {
            String seedName = seedTemplate.getName();
            propagateActionData.getPerformer().getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea + ".");
            return false;
        }
        if (propagateActionData.getSowTileCount() < 1) {
            propagateActionData.getPerformer().getCommunicator().sendNormalServerMessage("The " + sowArea + " needs at least one tile that can be sown");
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

}
