package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestions;

import java.util.Collections;
import java.util.List;

import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class ConfigureSeedBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final int actionId;
    private final ActionEntry actionEntry;

    ConfigureSeedBarrelAction(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return (short)this.actionId;
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
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(target);
        ConfigureSeedBarrelQuestion barrelQuestion = new ConfigureSeedBarrelQuestion(farmBarrel,
                ConfigureOptions.getInstance().getConfigureBarrelQuestionId());
        barrelQuestion.sendQuestion(ModQuestions.createQuestion(performer, "Configure Barrel", "Configure this how?",
                target.getWurmId(), barrelQuestion));
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }
}
