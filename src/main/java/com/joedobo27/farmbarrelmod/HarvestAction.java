package com.joedobo27.farmbarrelmod;


import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.*;
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

import static com.joedobo27.farmbarrelmod.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

class HarvestAction implements ModAction, BehaviourProvider, ActionPerformer {

    @Override
    public short getActionId(){
        return Actions.HARVEST;
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

        HarvestFarmer harvestFarmer;
        if (!HarvestFarmer.hashMapContainsKey(action)) {
            ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_BARREL_CONTENT_MISMATCH));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CROPS_NOT_RIPE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TOON_HOLDING_MAX_WEIGHT));

            FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(active);
            ConfigureActionOptions options = ConfigureOptions.getInstance().getHarvestActionOptions();
            harvestFarmer = new HarvestFarmer(action, performer, active, SkillList.FARMING, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    failureTestFunctions, TilePos.fromXY(tileX, tileY), farmBarrel);
        }
        else
            harvestFarmer = HarvestFarmer.getActionDataWeakHashMap().get(action);

        if (harvestFarmer.isActionStartTime(counter) && harvestFarmer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (harvestFarmer.isActionStartTime(counter)) {
            harvestFarmer.doActionStartMessages();
            harvestFarmer.setInitialTime(Actions.actionEntrys[Actions.HARVEST]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!harvestFarmer.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (harvestFarmer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        double power = harvestFarmer.doSkillCheckAndGetPower(counter);
        int harvestYield = harvestFarmer.getYield();
        harvestFarmer.alterTileState();
        harvestFarmer.getFarmBarrel().increaseContainedCount(harvestYield, power,
                TileUtilities.getItemTemplateFromHarvestTile(harvestFarmer.getTargetTile()).getTemplateId());
        active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
        performer.getStatus().modifyStamina(-10000.0f);
        harvestFarmer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
