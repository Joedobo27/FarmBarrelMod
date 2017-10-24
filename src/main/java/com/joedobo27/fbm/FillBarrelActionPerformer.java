package com.joedobo27.fbm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
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

public class FillBarrelActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final short actionId;
    private final ActionEntry actionEntry;

    FillBarrelActionPerformer(short actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final FillBarrelActionPerformer _performer;
        static {
            _performer = new FillBarrelActionPerformer( Actions.FILL, Actions.actionEntrys[Actions.FILL]);
        }
    }

    @Override
    public short getActionId(){
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item active, Item target){
        if (active == null || target == null || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                target.getTemplateId() != ItemList.bulkItem)
            return BehaviourProvider.super.getBehavioursFor(performer, active, target);
        return Collections.singletonList(Actions.actionEntrys[Actions.FILL]);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, Item target, short actionId, float counter) {
        if (actionId != Actions.FILL || target == null || active == null ||
                active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() || target.getTemplateId() != ItemList.bulkItem)
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        FillBarrelAction fillBarrelAction = FillBarrelAction.getFillBarrelAction(action);
        if (fillBarrelAction == null){
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TARGET_NOT_FARM_ITEM));

            ConfigureOptions.ActionOptions options = ConfigureOptions.getInstance().getFillBarrelAction();
            fillBarrelAction = new FillBarrelAction(action, performer, active, SkillList.MISCELLANEOUS, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    failureTestFunctions, target, FarmBarrel.getOrMakeFarmBarrel(active));
        }

        if (fillBarrelAction.isActionStartTime(counter) && fillBarrelAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (fillBarrelAction.isActionStartTime(counter)) {
            fillBarrelAction.doActionStartMessages();
            fillBarrelAction.setInitialTime(Actions.actionEntrys[Actions.FILL]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!fillBarrelAction.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (fillBarrelAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        int moveCount = fillBarrelAction.getMoveCount();
        fillBarrelAction.getFarmBarrel().increaseContainedCount(moveCount, fillBarrelAction.getTargetItem().getQualityLevel(),
                fillBarrelAction.getTargetItem().getRealTemplateId());
        fillBarrelAction.subtractBulkTargetCount(moveCount);
        fillBarrelAction.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    static FillBarrelActionPerformer getFillBarrelActionPerformer() {
        return SingletonHelper._performer;
    }


    /*
    Use Item.getName() to construct a modified name for the seed barrel; ex: seed barrel [1000 corn]. or maybe just
    seed barrel [corn] and use examine event message for quantity. I'm not sure about the frequent updates to
    watcher.getCommunicator().sendAddToInventory() if seed count is added to name.

    It should be possible to store the seed's templateId in the RealTemplate ItemDbColumn for the seed barrel. Item count
    can be tracked with the weight column; barrel weight + the weight of the seeds in it. Although, that is not how RealTemplate is used
    so it might be better to use another part of the data1. It should be possible to used a smaller number in the crops enum
    to look up the larger int sized seed template.

    data1 needs settings for sow radius and the amount to fill it up to. It would be nice if a simple drag but the inventory
    movement mechanics are a hassle to get  it to play nice with what this mod aims to do (a portable bsb which can be used
    to sow or harvest stuff). Use a makeshift serialized data object, which contains sow radius and filling data, into the data1 integer. And
     deserialize that same integer into useful data.
     */
}
