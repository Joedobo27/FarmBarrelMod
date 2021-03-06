package com.joedobo27.fbm;


import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static com.joedobo27.libs.action.ActionTypes.ACTION_NON_LIBILAPRIEST;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class EmptyBarrelActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final int actionId;
    private final ActionEntry actionEntry;

    EmptyBarrelActionPerformer(int actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final EmptyBarrelActionPerformer _performer;
        static {
            int transferActionId = ModActions.getNextActionId();
            _performer = new EmptyBarrelActionPerformer(transferActionId,
                    ActionEntry.createEntry((short) transferActionId, "Transfer", "transferring",
                            new int[] {}));
        }
    }

    static EmptyBarrelActionPerformer getEmptyBarrelActionPerformer(){
        return SingletonHelper._performer;
    }

    @Override
    public short getActionId(){
        return (short)this.actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item active, Item target){
        if (active == null || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId()
                || target == null || (!target.isBulkContainer() && !target.isCrate()))
            return null;
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, Item target, short actionId, float counter) {
        if (actionId != this.actionId || target == null || active == null ||
                active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                (!target.isBulkContainer() && !target.isCrate()))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        EmptyBarrelAction emptyBarrelAction = EmptyBarrelAction.getEmptyBarrelAction(action);
        if (emptyBarrelAction == null) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));

            ConfigureOptions.ActionOptions options = ConfigureOptions.getInstance().getEmptyBarrelAction();
            emptyBarrelAction = new EmptyBarrelAction(action, performer, active, SkillList.MISCELLANEOUS, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    failureTestFunctions, target, FarmBarrel.getOrMakeFarmBarrel(active));
        }

        if (emptyBarrelAction.isActionStartTime(counter) && emptyBarrelAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (emptyBarrelAction.isActionStartTime(counter)) {
            emptyBarrelAction.doActionStartMessages();
            emptyBarrelAction.setInitialTime(this.actionEntry);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!emptyBarrelAction.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (emptyBarrelAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);


        Item farmItem = emptyBarrelAction.makeItem();
        if (farmItem == null)
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        int moveCount = emptyBarrelAction.getMoveCount();

        farmItem.setWeight(farmItem.getWeightGrams() * moveCount, false);
        try{
            farmItem.moveToItem(performer, target.getWurmId(), true);
        }catch (NoSuchItemException | NoSuchPlayerException | NoSuchCreatureException e){
            FarmBarrelMod.logger.warning(e.getMessage());
            farmItem.setWeight(0, true);
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        emptyBarrelAction.getFarmBarrel().reduceContainedCount(moveCount);
        emptyBarrelAction.doActionEndMessages();
        emptyBarrelAction.getFarmBarrel().doFarmBarrelToInscriptionJson();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
