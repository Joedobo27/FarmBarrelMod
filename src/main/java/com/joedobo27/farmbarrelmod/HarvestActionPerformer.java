package com.joedobo27.farmbarrelmod;


import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

class HarvestActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final short actionId;
    private final ActionEntry actionEntry;

    HarvestActionPerformer(short actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final HarvestActionPerformer _performer;
        static {
            _performer = new HarvestActionPerformer( Actions.HARVEST, Actions.actionEntrys[Actions.HARVEST]);
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
    public List<ActionEntry> getBehavioursFor(Creature performer, Item active, int tileX, int tileY, boolean onSurface,
                                              int encodedTile) {
        if (active == null || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                !TileUtilities.isFarmTile(encodedTile))
            return BehaviourProvider.super.getBehavioursFor(performer, active, tileX, tileY, onSurface, encodedTile);
        return Collections.singletonList(Actions.actionEntrys[Actions.HARVEST]);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId != this.getActionId() || active == null ||
                active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() || !TileUtilities.isFarmTile(encodedTile))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        HarvestAction harvestAction = HarvestAction.getHarvestAction(action);
        if (harvestAction == null) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CROPS_NOT_RIPE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TOON_HOLDING_MAX_WEIGHT));


            ConfigureOptions.ActionOptions options = ConfigureOptions.getInstance().getHarvestAction();
            harvestAction = new HarvestAction(action, performer, active, SkillList.FARMING, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    failureTestFunctions, TilePos.fromXY(tileX, tileY), FarmBarrel.getOrMakeFarmBarrel(active));
        }

        if (harvestAction.isActionStartTime(counter) && harvestAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (harvestAction.isActionStartTime(counter)) {
            harvestAction.doActionStartMessages();
            harvestAction.setInitialTime(Actions.actionEntrys[Actions.HARVEST]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!harvestAction.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (harvestAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        double power = harvestAction.doSkillCheckAndGetPower(counter);
        int harvestYield = harvestAction.getYield();
        harvestAction.alterTileState();
        harvestAction.getFarmBarrel().increaseContainedCount(harvestYield, power,
                TileUtilities.getItemTemplateFromHarvestTile(harvestAction.getTargetTile()).getTemplateId());
        active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
        performer.getStatus().modifyStamina(-10000.0f);
        harvestAction.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    static HarvestActionPerformer getHarvestActionPerformer() {
        return SingletonHelper._performer;
    }
}
