package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class EmptyBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());
    private final ActionEntry actionEntry;
    private final short actionId;

    EmptyBarrelAction(){
        actionId = Actions.EMPTY;
        actionEntry = Actions.actionEntrys[Actions.EMPTY];
    }

    @Override
    public short getActionId(){
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target){
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && target != null && (target.isBulkContainer() || target.isCrate())){
            return Collections.singletonList(actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short aActionId, float counter) {
        if (aActionId == actionId && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && target != null && (target.isBulkContainer() || target.isCrate())) {
            if (hasAFailureCondition(performer, source, target))
                return true;
            Item seed = makeSeed(source);
            int seedCount = FarmBarrelMod.decodeSupplyQuantity(source);
            seed.setWeight(seed.getWeightGrams() * seedCount, false);
            try{seed.moveToItem(performer, target.getWurmId(), true);}catch (Exception ignored){}
            FarmBarrelMod.encodeContainedSeed(source, Crops.EMPTY.getId());
            source.updateName();
            return true;
        }
        return ActionPerformer.super.action(action, performer, source, target, aActionId, counter);
    }

    private Item makeSeed(Item source){
        Crops crops = Crops.getCrop(FarmBarrelMod.decodeContainedSeed(source));
        Item seedItem = null;
        try {seedItem = ItemFactory.createItem(
                crops.getSeedTemplateId(),
                FarmBarrelMod.decodeContainedQuality(source),
                crops.getSeedMaterial(), (byte)0, null);}catch (Exception ignored){}
        return seedItem;
    }

    private boolean hasAFailureCondition(Creature performer, Item source, Item target){

        return false;
    }
}
