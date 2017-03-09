package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.Question;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

/**
 *
 */
public class ConfigureSeedBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;
    private WeakHashMap<Action, ConfigureSeedBarrelQuestion> actionListener = new WeakHashMap<>();

    ConfigureSeedBarrelAction() {
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Configure", "Configuring", new int[] {ACTION_NON_LIBILAPRIEST.getId(),
                ACTION_SHOW_ON_SELECT_BAR.getId()});
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
            ConfigureSeedBarrelQuestion configureSeedBarrelQuestion;
            if (counter == 1.0f) {
                 configureSeedBarrelQuestion = new ConfigureSeedBarrelQuestion(
                        performer, "Configure Barrel", "Configure the barrel with.", 501, target.getWurmId());
                actionListener.put(action, configureSeedBarrelQuestion);
                logger.log(Level.INFO, configureSeedBarrelQuestion.toString());
                return false;
            }
            else {
                configureSeedBarrelQuestion = actionListener.get(action);
                if (configureSeedBarrelQuestion.isAnswered()) {
                    logger.log(Level.INFO, "Question answered.");
                    return true;
                }
                if (counter > 100){
                    performer.getCommunicator().sendNormalServerMessage("Question unanswered.");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }
}
