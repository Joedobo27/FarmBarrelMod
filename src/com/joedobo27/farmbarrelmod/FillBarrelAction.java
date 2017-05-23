package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class FillBarrelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final ActionEntry actionEntry;
    private final short actionId;
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    FillBarrelAction() {
        this.actionId = Actions.FILL;
        this.actionEntry = Actions.actionEntrys[Actions.FILL];
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target){
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && target.getTemplateId() == ItemList.bulkItem && itemIsFarmCropOrSeed(target) && target.getParentId() != 10L && performer.isWithinDistanceTo(getParentItem(target), 8)){
            return Collections.singletonList(this.actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public short getActionId(){
        return this.actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item barrel, Item bulkItem, short aActionId, float counter) {
        if (aActionId == Actions.FILL && barrel.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId() &&
                 bulkItem.getParentId() != 10L && performer.isWithinDistanceTo(getParentItem(bulkItem), 8)) {
            if (hasAFailureCondition(performer, barrel, bulkItem))
                return true;
            if (barrel.getData1() == -1){
                FarmBarrelMod.encodeContainedQuality(barrel, 0);
                FarmBarrelMod.encodeContainedCropId(barrel, Crops.EMPTY.getId());
            }
            FarmBarrelMod.encodeIsSeed(barrel, true);
            if (bulkItem.getRealTemplate().isSeed()) {
                FarmBarrelMod.encodeContainedCropId(barrel, Crops.getCropIdFromSeedTemplateId(bulkItem.getRealTemplateId()));
            }
            else {
                FarmBarrelMod.encodeContainedCropId(barrel, Crops.getCropIdFromProductTemplateId(bulkItem.getRealTemplateId()));
            }
            int fillQuantity = Math.round(tallyFillQuantity(barrel, bulkItem));
            setBulkItemGrams(barrel, bulkItem, fillQuantity);
            setContainedQuality(barrel, bulkItem, fillQuantity);
            setBarrelWeight(barrel, bulkItem, fillQuantity);
            // update the custom naming tag even if it's already of correct name.
            barrel.updateName();
            return true;
        }
        return ActionPerformer.super.action(action, performer, barrel, bulkItem, aActionId, counter);
    }

    private static Item getParentItem(Item item){
        try {
            return item.getParent();
        } catch (Exception ignored){}
        return item;
    }

    private void setContainedQuality(Item barrel, Item bulkItem, int fillQuantity){
        int unitSeedGrams = bulkItem.getRealTemplate().getWeightGrams();
        if (!bulkItem.getRealTemplate().isSeed()) {
            try{unitSeedGrams = Crops.getSeedTemplateFromProductTemplate(bulkItem.getRealTemplate()).getWeightGrams();}catch (Exception ignored){}
        }
        int containedQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / unitSeedGrams;
        int containedQuality = FarmBarrelMod.decodeContainedQuality(barrel);
        float targetQuality = bulkItem.getQualityLevel();
        int weightedAverageQuality = (int)targetQuality;
        if (containedQuality != 0 && containedQuantity != 0)
            weightedAverageQuality = (int) (((fillQuantity * targetQuality) + (containedQuantity * containedQuality)) / (fillQuantity + containedQuantity));
        FarmBarrelMod.encodeContainedQuality(barrel, weightedAverageQuality);
    }


    private void setBarrelWeight(Item barrel, Item bulkItem, int fillQuantity){
        int unitSeedGrams = bulkItem.getRealTemplate().getWeightGrams();
        if (!bulkItem.getRealTemplate().isSeed()) {
            try{unitSeedGrams = Crops.getSeedTemplateFromProductTemplate(bulkItem.getRealTemplate()).getWeightGrams();}catch (Exception ignored){}
        }
        int barrelQuantity = barrel.getWeightGrams() - 1000 == 0 ? 0 : (barrel.getWeightGrams() - 1000) / unitSeedGrams;
        barrel.setWeight(1000 + (fillQuantity * unitSeedGrams) + (barrelQuantity * unitSeedGrams), false);
    }


    private static boolean itemIsFarmCropOrSeed(Item item){
        if (item == null) {
            return false;
        }
        boolean isSeed = item.getRealTemplate().isSeed();
        boolean isProduct = (Arrays.stream(Crops.values())
                .filter(crops -> item.getRealTemplateId() == crops.getProductTemplateId())
                .findFirst()
                .orElse(Crops.EMPTY)).getId() < Crops.getLastUsableEntry();
        return isSeed || isProduct;
    }

    private static void setBulkItemGrams(Item source, Item target, int fillQuantity){
        int bulkCount = Integer.parseInt(target.getDescription().replaceAll("x",""));
        int newBulkItemCount = bulkCount - fillQuantity;
        target.setDescription(Integer.toString(newBulkItemCount) +"x");
        target.setWeight(newBulkItemCount * target.getRealTemplate().getVolume(), false);
    }

    private static int tallyFillQuantity(Item source, Item target){
        int targetQuantity = (target.getTemplateId() == ItemList.bulkItem) ? Integer.parseInt(target.getDescription().replaceAll("x",""), 10) : 1;
        int fillQuantity = FarmBarrelMod.decodeSupplyQuantity(source);
        int weight = (target.getTemplateId() == ItemList.bulkItem) ? target.getRealTemplate().getWeightGrams() : target.getTemplate().getWeightGrams();
        int barrelQuantity = source.getWeightGrams() == 1000 ? 0 : (source.getWeightGrams() - 1000) / weight;
        return Math.min(fillQuantity - barrelQuantity, targetQuantity - 1);
    }

    private static boolean hasAFailureCondition(Creature performer, Item barrel, Item bulkItem){
        int seedId = FarmBarrelMod.decodeContainedCropId(barrel);
        int seedTemplateId = Crops.getSeedTemplateIdFromCropId(seedId);
        if (!itemIsFarmCropOrSeed(bulkItem)){
            performer.getCommunicator().sendNormalServerMessage("The barrel can only be filled with farm seeds.");
            return true;
        }
        boolean targetNotBulk = bulkItem.getTemplateId() != ItemList.bulkItem;
        if (targetNotBulk) {
            performer.getCommunicator().sendNormalServerMessage("The barrel only be filled with items inside bulk containers.");
            return true;
        }

        boolean barrelIsEmpty = seedId == Crops.getLastUsableEntry();
        boolean doesContainedMatchTarget = barrelIsEmpty ||
                seedTemplateId == bulkItem.getRealTemplateId();
        if (!doesContainedMatchTarget){
            performer.getCommunicator().sendNormalServerMessage("The barrel can only hold one type of seed at a time.");
            return true;
        }
        boolean targetIsTooFew = Integer.parseInt(bulkItem.getDescription().replaceAll("x", "")) < 2;
        if (targetIsTooFew) {
            performer.getCommunicator().sendNormalServerMessage("There isn't enough seed there to fill the barrel.");
            return true;
        }
        try {
            int contentsGrams = bulkItem.getRealTemplate().getWeightGrams();
            if (!bulkItem.getRealTemplate().isSeed()) {
                contentsGrams = Crops.getSeedTemplateFromProductTemplate(bulkItem.getRealTemplate()).getWeightGrams();
            }
            boolean canCarryFillQuantity = performer.canCarry(contentsGrams * FarmBarrelMod.decodeSupplyQuantity(barrel));
            if (!canCarryFillQuantity) {
                performer.getCommunicator().sendNormalServerMessage("You can't carry that many seeds.");
                return true;
            }
        } catch (NoSuchTemplateException | CropsException e) {
            logger.warning(e.getMessage());
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }

        return false;
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
