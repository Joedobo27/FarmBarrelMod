package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
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
        actionEntry = ActionEntry.createEntry(actionId, "Supply", "Supplying", new int[] {ACTION_NON_LIBILAPRIEST.getId(),
                ACTION_ENEMY_ALWAYS.getId() });
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target){
        if (performer instanceof Player && source != null && source.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()
                && (target.getTemplate().isSeed() || target.getRealTemplate().isSeed())){
            return Collections.singletonList(actionEntry);
        }else {
            return null;
        }
    }

    @Override
    public short getActionId(){
        return this.actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short actionId, float counter) {
        if (actionId == getActionId()) {
            if (hasAFailureCondition(performer, source, target))
                return true;

            int fillQuantity = tallyFillQuantity(source, target);
            int unitSeedWeight = target.isBulk() ? target.getRealTemplate().getWeightGrams() : target.getTemplate().getWeightGrams();
            // reduce weight in BSB
            if (target.isBulk()){
                setBulkItemWeight(target, fillQuantity);
            }
            // deal with non-bulk items.

            // set barrel weight
            source.setWeight(1000 + (fillQuantity * unitSeedWeight), false);
            // update the custom naming tag even if it's already of correct name.

            return true;
        }
        return false;
    }

    private static void setBulkItemWeight(Item target, int fillQuantity){
        int bulkCount = Integer.parseInt(target.getDescription().replaceAll("x",""));
        int newBulkItemCount = bulkCount - fillQuantity;
        target.setDescription(Integer.toString(newBulkItemCount) +"x");
        target.setWeight(newBulkItemCount * target.getRealTemplate().getVolume(), false);
    }

    private static int tallyFillQuantity(Item source, Item target){
        int targetQuantity = target.isBulk() ? Integer.parseInt(target.getDescription().replaceAll("x","")) : 1;
        int fillQuantity = FarmBarrelMod.decodeSupplyQuantity(source);
        int weight = target.isBulk() ? target.getRealTemplate().getWeightGrams() : target.getTemplate().getWeightGrams();
        int barrelQuantity = (source.getWeightGrams() - 1000) / weight;
        return Math.min(fillQuantity - barrelQuantity, targetQuantity - 1);
    }

    private static boolean hasAFailureCondition(Creature performer, Item source, Item target){
        int seedId = FarmBarrelMod.decodeContainedSeed(source);
        int seedTemplateId = Wrap.Crops.getSeedTemplateIdFromCropId(seedId);
        boolean barrelIsEmpty = seedId == 0;
        boolean doesContainedMatchTarget = barrelIsEmpty ||
                seedTemplateId == target.getRealTemplateId() ||
                seedTemplateId == target.getTemplateId();
        if (!doesContainedMatchTarget){
            performer.getCommunicator().sendNormalServerMessage("The barrel can only hold one type of seed at a time.");
            return true;
        }
        int weight = target.isBulk() ? target.getRealTemplate().getWeightGrams() : target.getTemplate().getWeightGrams();
        int fillQuantity = FarmBarrelMod.decodeSupplyQuantity(source);
        boolean canCarryFillQuantity = performer.canCarry(weight * fillQuantity);
        if (!canCarryFillQuantity){
            performer.getCommunicator().sendNormalServerMessage("You can't carry that many seeds.");
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
