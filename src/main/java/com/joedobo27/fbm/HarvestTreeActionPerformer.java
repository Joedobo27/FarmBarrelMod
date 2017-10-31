package com.joedobo27.fbm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
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
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION;

public class HarvestTreeActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final short actionId;
    private final ActionEntry actionEntry;

    HarvestTreeActionPerformer(short actionId, ActionEntry actionEntry){
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final HarvestTreeActionPerformer _performer;
        static {
            _performer = new HarvestTreeActionPerformer( Actions.HARVEST, Actions.actionEntrys[Actions.HARVEST]);
        }
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item active, int tileX, int tileY, boolean onSurface,
                                              int encodedTile) {
        if (active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                !TileUtilities.treeHasFruit(TilePos.fromXY(tileX, tileY)))
            return BehaviourProvider.super.getBehavioursFor(performer, active, tileX, tileY, onSurface, encodedTile);
        return Collections.singletonList(actionEntry);

    }

    @Override
    public boolean action(Action action, Creature performer, Item active, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId != this.getActionId() || active == null || active.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                !TileUtilities.treeHasFruit(TilePos.fromXY(tileX, tileY)))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        HarvestTreeAction harvestTreeAction = HarvestTreeAction.getHarvestTreeAction(action);
        if (harvestTreeAction == null){
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TOON_HOLDING_MAX_WEIGHT));

            ConfigureOptions.ActionOptions options = ConfigureOptions.getInstance().getHarvestTreeAction();
            TilePos tilePos = TilePos.fromXY(tileX, tileY);
            harvestTreeAction = new HarvestTreeAction(action, performer, active, SkillList.FORESTRY, options.getMinSkill(),
                    options.getMaxSkill(), options.getLongestTime(), options.getShortestTime(), options.getMinimumStamina(),
                    tilePos, FarmBarrel.getOrMakeFarmBarrel(active), failureTestFunctions,
                    TileUtilities.getTreeType(tilePos), TileUtilities.getBushType(tilePos));
        }
        if (harvestTreeAction.isActionStartTime(counter) && harvestTreeAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (harvestTreeAction.isActionStartTime(counter)) {
            harvestTreeAction.doActionStartMessages();
            harvestTreeAction.setInitialTime(Actions.actionEntrys[Actions.HARVEST]);
            active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!harvestTreeAction.isActionTimedOut(action, counter)) {
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (harvestTreeAction.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);


        harvestTreeAction.removeTreesFruit();
        harvestTreeAction.doSkillCheckAndGetPower(counter);
        harvestTreeAction.depositInBarrel();
        active.setDamage(active.getDamage() + 0.0015f * active.getDamageModifier());
        performer.getStatus().modifyStamina(-10000.0f);
        harvestTreeAction.doActionEndMessages();
        harvestTreeAction.getFarmBarrel().doFarmBarrelToInscriptionJson();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    static HarvestTreeActionPerformer getHarvestTreeActionPerformer() {
        return SingletonHelper._performer;
    }
}
