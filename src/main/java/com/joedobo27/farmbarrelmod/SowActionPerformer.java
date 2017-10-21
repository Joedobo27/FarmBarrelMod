package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.CropTilePoller;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.*;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

class SowActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final short actionId;
    private final ActionEntry actionEntry;

    SowActionPerformer(short actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final SowActionPerformer _performer;
        static {
            _performer = new SowActionPerformer( Actions.SOW, Actions.actionEntrys[Actions.SOW]);
        }
    }

    @Override
    public short getActionId(){
        return actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
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

        SowAction sowAction = SowAction.getSowAction(action);
        if (sowAction == null) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add((getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_BRIDGE_SUPPORT)));


            ConfigureOptions.ActionOptions options = ConfigureOptions.getInstance().getSowAction();
            sowAction = new SowAction(action, performer, active, SkillList.FARMING, options.getMinSkill(), options.getMaxSkill(),
                    options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    TilePos.fromXY(tileX, tileY), FarmBarrel.getOrMakeFarmBarrel(active), failureTestFunctions);
        }

        if (sowAction.isActionStartTime(counter) && sowAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        LinkedList<TilePos> sowTiles = sowAction.selectSowTiles();
        if (sowAction.isActionStartTime(counter)) {
            sowAction.doActionStartMessages();
            sowAction.setInitialTime(Actions.actionEntrys[Actions.SOW]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (sowAction.isActionTimedOut(action, counter) ||
                performer.getStatus().getStamina() < ConfigureOptions.getInstance().getSowAction().getMinimumStamina()) {
            sowAction.doActionEndMessages();
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (sowAction.unitTimeJustTicked(counter)) {
            sowAction.doSkillCheckAndGetPower();
            sowAction.getFarmBarrel().reduceContainedCount(1);
            //TODO This needs to reflection poping tilePos off the sowTiles linkedList and using them.
            sowAction.alterTileState();
            sowAction.updateMeshResourceData();
            CropTilePoller.addCropTile(TileUtilities.getSurfaceData(sowAction.getTargetTile()), tileX, tileY,
                    Crops.getCropIdFromTemplateId(sowAction.getFarmBarrel().getContainedItemTemplateId()), onSurface);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-2000.0f);
        }
        return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    static SowActionPerformer getSowActionPerformer() {
        return SingletonHelper._performer;
    }

}
