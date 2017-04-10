package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

/**
 *
 */
public class ConfigureSeedBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final short actionId;
    private final ActionEntry actionEntry;

    ConfigureSeedBarrelAction() {
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Configure", "Configuring", new int[] {ACTION_NON_LIBILAPRIEST.getId()});
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        return getBehavioursFor(performer, null, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) {
        if (performer instanceof Player && target.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()) {
            return Collections.singletonList(actionEntry);
        } else {
            return null;
        }
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        if (num == actionId) {
            new ConfigureSeedBarrelQuestion(
                    performer, "Configure Barrel", "Configure the barrel with.", 501, target.getWurmId());
            return true;
        }
        return false;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }
}
