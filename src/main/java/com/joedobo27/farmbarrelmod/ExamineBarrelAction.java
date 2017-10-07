package com.joedobo27.farmbarrelmod;


import com.joedobo27.libs.ColorDefinitions;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.shared.util.MaterialUtilities;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;


public class ExamineBarrelAction implements ModAction, ActionPerformer{

    @Override
    public short getActionId() {
        return Actions.EXAMINE;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short actionId, float counter){
        if (actionId != Actions.EXAMINE || target == null || target.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId())
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);
        return action(action, performer, null, target, actionId, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item active, Item target, short actionId, float counter) {
        if (actionId != Actions.EXAMINE || target == null || target.getTemplateId() != FarmBarrelMod.getSowBarrelTemplateId())
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        FarmBarrel farmBarrel = FarmBarrel.getOrMakeFarmBarrel(target);

        String examineText = target.getTemplate().getDescriptionLong();

        String signatureText = target.getSignature();
        if (signatureText != null && !signatureText.isEmpty()) {
            examineText += String.format(" You can barely make out the signature of its maker,  '%s'.", signatureText);
        }

        if (target.getRarity() > MaterialUtilities.COMMON) {
            examineText += (MethodsItems.getRarityDesc(target.rarity));
        }
        if (target.color != ColorDefinitions.COLOR_ID_NONE) {
            examineText += MethodsItems.getColorDesc(target.color);
        }
        int sowArea = (farmBarrel.getSowRadius() * 2) + 1;
        int supplyQuantity = farmBarrel.getSupplyQuantity();
        examineText += String.format("The barrel is set to sow a %d square area and the supply amount is %d.", sowArea,
                supplyQuantity);

        String cropName = farmBarrel.getCropName();
        int containedCount = farmBarrel.getContainedCount();
        double containedQuality = farmBarrel.getContainedQuality();
        examineText += String.format(" It has %d of %s at %f quality.", containedCount, cropName, containedQuality);

        performer.getCommunicator().sendNormalServerMessage(examineText);

        target.sendEnchantmentStrings(performer.getCommunicator());

        String improvedBy = MethodsItems.getImpDesc(performer, target);
        if (!improvedBy.isEmpty()) {
            performer.getCommunicator().sendNormalServerMessage(improvedBy);
        }
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
