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
import java.util.logging.Logger;

import static com.joedobo27.farmbarrelmod.Wrap.Actions.*;

public class FillBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;

    FillBarrelAction() {
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Supply", "Supplying", new int[] {ACTION_FATIGUE.getId(), ACTION_NON_LIBILAPRIEST.getId(),
                ACTION_SHOW_ON_SELECT_BAR.getId(), ACTION_ENEMY_ALWAYS.getId() });
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target){
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()){
            return Collections.singletonList(actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public short getActionId(){
        return actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short actionId, float counter) {
        if (actionId == getActionId() && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && Wrap.Crops.templateIdIsSeed(target.getRealTemplateId())){
            if (hasAFailureConditions(source, target)){
                return true;
            }
        }
        return true;
    }

    private static boolean hasAFailureConditions(Item source, Item target){
        boolean isSeed = target.getRealTemplate().isSeed() || target.getTemplate().isSeed();
        if (!isSeed)
            return true;
        return false;
    }

    /*
    Use Item.getName() to construct a modified name for the seed barrel; ex: seed barrel [1000 corn]. or maybe just
    seed barrel [corn] and use examine event message for quantity. I'm not sure about the frequent updates to
    watcher.getCommunicator().sendAddToInventory().

    It should be possible to store the seed's templateId in the RealTemplate ItemDbColumn for the seed barrel. Item count
    can be tracked with the weight column; barrel weight + the weight of the seeds in it.

    data1 needs settings for sow radius and the amount to fill it up to. It would be nice if a simple drag but the inventory
    movement mechanics are a hassle to get  it to play nice with what this mod aims to do (a portable bsb which can be used
    to sow or harvest stuff). Use a makeshift serialized data object, which contains sow radius and filling data, into the data1 integer. And
     deserialize that same integer into useful data.
     */

}
