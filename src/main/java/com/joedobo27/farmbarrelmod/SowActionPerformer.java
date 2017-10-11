package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
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
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;

import static com.joedobo27.farmbarrelmod.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

class SowActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private static WeakHashMap<Action, SowAction> actionListener = new WeakHashMap<>();

    @Override
    public short getActionId(){
        return Actions.SOW;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface,
                                              int encodedTile){
        if (source == null || source.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                (Tiles.decodeType(encodedTile) != Tiles.TILE_TYPE_DIRT && !TileUtilities.isFarmTile(encodedTile)))
            return BehaviourProvider.super.getBehavioursFor(performer, source, tileX, tileY, onSurface, encodedTile);
        return Collections.singletonList(Actions.actionEntrys[Actions.SOW]);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId != this.getActionId() || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                (Tiles.decodeType(encodedTile) != Tiles.TILE_TYPE_DIRT && !TileUtilities.isFarmTile(encodedTile)))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        SowFarmer sowFarmer;
        if (!SowFarmer.hashMapContainsKey(action)) {
            ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_SKILL_FOR_SOW_AREA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add((getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT)));

            FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(active);
            ConfigureActionOptions options = ConfigureOptions.getInstance().getSowActionOptions();
            sowFarmer = new SowFarmer(action, performer, active, SkillList.FARMING, options. getMinSkill(), options.getMaxSkill(),
                    options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(), failureTestFunctions,
                    TilePos.fromXY(tileX, tileY), farmBarrel);
        }
        else
            sowFarmer = SowFarmer.getActionDataWeakHashMap().get(action);

        if (sowFarmer.isActionStartTime(counter) && sowFarmer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        LinkedList<TilePos> sowTiles = sowFarmer.selectSowTiles();
        if (sowFarmer.isActionStartTime(counter)) {
            sowFarmer.doActionStartMessages();
            sowFarmer.setInitialTime(Actions.actionEntrys[Actions.SOW]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (sowFarmer.isActionTimedOut(action, counter) ||
                performer.getStatus().getStamina() < ConfigureOptions.getInstance().getMinimumStamina()) {
            sowFarmer.doActionEndMessages();
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (sowFarmer.unitTimeJustTicked(counter)) {
            sowFarmer.doSkillCheckAndGetPower();
            sowFarmer.getFarmBarrel().reduceContainedCount(1);
            sowFarmer.alterTileState();
            sowFarmer.updateMeshResourceData();
            //TODO This needs to reflection poping tilePos off the sowTiles linkedList and using them.
            CropTilePoller.addCropTile(TileUtilities.getSurfaceData(sowFarmer.getTargetTile()), tileX, tileY,
                    Crops.getCropIdFromTemplateId(sowFarmer.getFarmBarrel().getContainedItemTemplateId()), onSurface);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-2000.0f);
        }
        return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);




            try {
                int time;
                final float TIME_TO_COUNTER_DIVISOR = 10.0f;
                final float ACTION_START_TIME = 1.0f;
                SowAction sowAction;

                if (counter == ACTION_START_TIME) {
                    sowAction = new SowAction(new Point(tileX, tileY, encodedTile), action, onSurface, performer, active, encodedTile);
                    actionListener.put(action, sowAction);
                    if (hasAFailureCondition(sowAction)) {
                        return true;
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);
                    action.setTimeLeft(sowAction.getTotalTime());
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, sowAction.getTotalTime());
                    return false;
                }
                time = action.getTimeLeft();
                sowAction = actionListener.get(action);
                boolean isEndOfTileSowing = sowAction.unitTimeJustTicked(counter);
                boolean timeLeft = counter - 1 < time / TIME_TO_COUNTER_DIVISOR;
                boolean tilesLeft = sowAction.getPoints().size() > 0;
                if (isEndOfTileSowing && timeLeft && tilesLeft) {
                    if (hasAFailureCondition(sowAction)) {
                        return true;
                    }
                    int cropId;
                    double cropDifficulty;
                    cropId = Crops.getCropIdFromSeedTemplateId(sowAction.getSeedTemplateId());
                    cropDifficulty = Crops.getCropDifficultyFromCropId(cropId);

                    // skill check and use the unit time in sowAction as counts
                    Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
                    double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
                    farmingSkill.skillCheck(cropDifficulty, bonus, false, (float)(sowAction.getUnitSowTimeInterval() / TIME_TO_COUNTER_DIVISOR));
                    // damage barrel
                    active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
                    // consume some stamina
                    performer.getStatus().modifyStamina(-2000.0f);
                    // change tile to a farm tile and configureUpdate all the appropriate data.
                    // pop tile from sowAction and use returned value to configureUpdate mesh data, H in Point is an encodedTile int.
                    Point location = sowAction.popSowTile();
                    if (cropId <= 15)
                        Server.setSurfaceTile(location.getX(), location.getY(),Tiles.decodeHeight(location.getH()), Tiles.Tile.TILE_FIELD.id,
                                TileUtilities.encodeSurfaceFarmTileData(true, 0, cropId));
                    else if (cropId > 15)
                        Server.setSurfaceTile(location.getX(), location.getY(),Tiles.decodeHeight(location.getH()), Tiles.Tile.TILE_FIELD2.id,
                                TileUtilities.encodeSurfaceFarmTileData(true, 0, cropId));
                    Players.getInstance().sendChangedTile(location.getX(), location.getY(), onSurface, false);
                    int resource = TileUtilities.encodeResourceFarmTileData(0, (int)Math.min(2047, 100.0 - farmingSkill.getKnowledge() +
                            active.getQualityLevel() +(active.getRarity() * 20) + (action.getRarity() * 50)));
                    Server.setWorldResource(location.getX(), location.getY(), resource);
                    CropTilePoller.addCropTile(location.getH(), location.getX(), location.getY(), cropId, onSurface);
                    // consume some seed volume in barrel
                    sowAction.consumeSeed();

                }
                if (counter - 1 >= (time / TIME_TO_COUNTER_DIVISOR)) {
                    performer.getCommunicator().sendNormalServerMessage("You finish " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " finishes " + action.getActionString() + ".", performer, 5);
                    return true;
                }
            }catch (Exception e) {
                FarmBarrelMod.logger.log(Level.INFO, "This action does not exist?", e);
                return true;
            }
        }
        return false;
    }

    private boolean hasAFailureCondition(SowAction sowAction) {
        boolean noSeedWithin = FarmBarrelMod.decodeContainedCropId(sowAction.getBarrel()) == Crops.EMPTY.getId();
        if (noSeedWithin) {
            sowAction.getPerformer().getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return true;
        }
        boolean contentsNotSeed = !FarmBarrelMod.decodeIsSeed(sowAction.getBarrel());
        if (contentsNotSeed) {


        }


        if (!sowAction.enoughSeedForSowing()) {
            String seedName = "ERROR";
            try {
                seedName = Crops.getCropNameFromCropId(FarmBarrelMod.decodeContainedCropId(sowAction.getBarrel()),
                        FarmBarrelMod.decodeIsSeed(sowAction.getBarrel()));
            }catch (NoSuchTemplateException | CropsException ignored){}
            sowAction.getPerformer().getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea + ".");
            return true;
        }
        if (sowAction.getSowTileCount() < 1) {
            sowAction.getPerformer().getCommunicator().sendNormalServerMessage("The " + sowArea + " needs at least one tile that can be sown");
            return true;
        }

        return false;
    }



}
