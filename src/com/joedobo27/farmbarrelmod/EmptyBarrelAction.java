package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class EmptyBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final ActionEntry actionEntry;
    private final short actionId;
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    EmptyBarrelAction(){
        this.actionId = (short) ModActions.getNextActionId();
        this.actionEntry = ActionEntry.createEntry(actionId, "Empty", "emptying", new int[] {});
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId(){
        return this.actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        return null;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item barrel, Item bulkContainer){
        if (performer instanceof Player && barrel != null && barrel.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && bulkContainer != null && (bulkContainer.isBulkContainer() || bulkContainer.isCrate()) && performer.isWithinDistanceTo(bulkContainer, 8)
                && FarmBarrelMod.decodeContainedCropId(barrel) != Crops.EMPTY.getId()){
            return Collections.singletonList(this.actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return ActionPerformer.super.action(action, performer, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item barrel, Item bulkContainer, short aActionId, float counter) {
        if (aActionId == this.actionId && barrel.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && bulkContainer != null && (bulkContainer.isBulkContainer() || bulkContainer.isCrate()) && performer.isWithinDistanceTo(bulkContainer, 8)
                && FarmBarrelMod.decodeContainedCropId(barrel) != Crops.EMPTY.getId()) {
            if (hasAFailureCondition(performer, barrel, bulkContainer))
                return true;
            Item seed = makeSeed(barrel);
            int seedCount = getContainedQuantity(barrel);
            seed.setWeight(seed.getWeightGrams() * seedCount, false);
            try{seed.moveToItem(performer, bulkContainer.getWurmId(), true);}catch (Exception ignored){}
            FarmBarrelMod.encodeContainedCropId(barrel, Crops.EMPTY.getId());
            FarmBarrelMod.encodeContainedQuality(barrel, 0);
            FarmBarrelMod.encodeIsSeed(barrel, false);
            barrel.updateName();
            barrel.setWeight(1000, false);
            return true;
        }
        return ActionPerformer.super.action(action, performer, barrel, bulkContainer, aActionId, counter);
    }

    private int getContainedQuantity(Item barrel) {
        int barrelQuantity = 1;
        ItemTemplate cropTemplate;
        ItemTemplate seedTemplate;
        try {
            cropTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getProductTemplateIdFromCropId(
                    FarmBarrelMod.decodeContainedCropId(barrel)));
            seedTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getSeedTemplateIdFromCropId(
                    FarmBarrelMod.decodeContainedCropId(barrel)));
            if (FarmBarrelMod.decodeIsSeed(barrel))
                barrelQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / seedTemplate.getWeightGrams();
            else
                barrelQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / cropTemplate.getWeightGrams();
        }catch (Exception ignored){}
        return barrelQuantity;
    }

    private Item makeSeed(Item source){
        Crops crops = Crops.getCrop(FarmBarrelMod.decodeContainedCropId(source));
        Item seedItem = null;
        try {seedItem = ItemFactory.createItem(
                FarmBarrelMod.decodeIsSeed(source) ? crops.getSeedTemplateId() : crops.getProductTemplateId(),
                FarmBarrelMod.decodeContainedQuality(source),
                crops.getSeedMaterial(), (byte)0, null);}catch (Exception ignored){}
        return seedItem;
    }

    private boolean hasAFailureCondition(Creature performer, Item barrel, Item bulkContainer){
        try {
            ItemTemplate containedTemplate = Crops.getProductTemplateFromCropId(FarmBarrelMod.decodeContainedCropId(barrel));
            if (FarmBarrelMod.decodeIsSeed(barrel))
                containedTemplate = Crops.getSeedTemplateFromCropId(FarmBarrelMod.decodeContainedCropId(barrel));

            boolean wrongFoodBulkContainer = containedTemplate.isFood() && (bulkContainer.getTemplateId() != ItemList.hopper || bulkContainer.isCrate());
            boolean wrongNonFoodBulkContainer = !containedTemplate.isFood() && (bulkContainer.getTemplateId() != ItemList.bulkContainer || bulkContainer.isCrate());
            if (wrongFoodBulkContainer || wrongNonFoodBulkContainer) {
                performer.getCommunicator().sendNormalServerMessage("That is the wrong container type to empty into.");
                return true;
            }
        }catch (NoSuchTemplateException | CropsException e) {
            logger.warning(e.getMessage());
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }
        return false;
    }
}
