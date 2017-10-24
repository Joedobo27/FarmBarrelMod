package com.joedobo27.fbm;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestions;

import java.util.Collections;
import java.util.List;

import static com.joedobo27.libs.action.ActionTypes.ACTION_NON_LIBILAPRIEST;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class ConfigureSeedBarrelActionPerformer implements ModAction, BehaviourProvider, ActionPerformer {

    private final int actionId;
    private final ActionEntry actionEntry;

    ConfigureSeedBarrelActionPerformer(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final ConfigureSeedBarrelActionPerformer _performer;
        static {
            int configureActionId = ModActions.getNextActionId();
            _performer = new ConfigureSeedBarrelActionPerformer(configureActionId,
                    ActionEntry.createEntry((short) configureActionId, "Configure", "configuring",
                            new int[] {ACTION_NON_LIBILAPRIEST.getId()}));
        }
    }

    static ConfigureSeedBarrelActionPerformer getConfigureSeedBarrelActionPerformer(){
        return SingletonHelper._performer;
    }

    @Override
    public short getActionId() {
        return (short)this.actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        return getBehavioursFor(performer, null, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) {
        if (target.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() || target.getWurmId() == -10L)
            return null;
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short actionId, float counter) {
        if (actionId != this.actionId || target.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId() ||
                target.getWurmId() == -10L)
            return propagate(action);
        FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(target);
        ConfigureSeedBarrelQuestion barrelQuestion = new ConfigureSeedBarrelQuestion(farmBarrel,
                ConfigureOptions.getInstance().getConfigureBarrelQuestionId());
        barrelQuestion.sendQuestion(ModQuestions.createQuestion(performer, "Configure Barrel", "Configure this how?",
                target.getWurmId(), barrelQuestion));
        return propagate(action, FINISH_ACTION);
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }
}
