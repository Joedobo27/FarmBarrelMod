package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;
import static com.joedobo27.farmbarrelmod.ActionFailureFunction.*;

public class EmptyBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final int actionId;
    private final ActionEntry actionEntry;

    EmptyBarrelAction(int actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId(){
        return (short)this.actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item active, Item target){
        if (active == null || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId()
                || target == null || (!target.isBulkContainer() && !target.isCrate()))
            return BehaviourProvider.super.getBehavioursFor(performer, active, target);
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, Item target, short actionId, float counter) {
        if (actionId != this.actionId || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId()
                || target == null || (!target.isBulkContainer() && !target.isCrate()))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        EmptyBarrelFarmer emptyBarrelFarmer;
        if (!EmptyBarrelFarmer.hashMapContainsKey(action)) {
            ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_FOOD_IN_NON_FOOD_CONTAINER));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_NON_FOOD_IN_FOOD_CONTAINER));

            FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(active);
            ConfigureActionOptions options = ConfigureOptions.getInstance().getEmptyBarrelActionOptions();
            emptyBarrelFarmer = new EmptyBarrelFarmer(action, performer, active, SkillList.MISCELLANEOUS, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    failureTestFunctions, target, farmBarrel);
        }
        else
            emptyBarrelFarmer = EmptyBarrelFarmer.getActionDataWeakHashMap().get(action);

        if (emptyBarrelFarmer.isActionStartTime(counter) && emptyBarrelFarmer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (emptyBarrelFarmer.isActionStartTime(counter)) {
            emptyBarrelFarmer.doActionStartMessages();
            emptyBarrelFarmer.setInitialTime(this.actionEntry);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!emptyBarrelFarmer.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (emptyBarrelFarmer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);


        Item farmItem = emptyBarrelFarmer.makeItem();
        if (farmItem == null)
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        int moveCount = emptyBarrelFarmer.getMoveCount();

        farmItem.setWeight(farmItem.getWeightGrams() * moveCount, false);
        try{
            farmItem.moveToItem(performer, target.getWurmId(), true);
        }catch (NoSuchItemException | NoSuchPlayerException | NoSuchCreatureException e){
            FarmBarrelMod.logger.warning(e.getMessage());
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        emptyBarrelFarmer.getFarmBarrel().reduceContainedCount(moveCount);
        emptyBarrelFarmer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
