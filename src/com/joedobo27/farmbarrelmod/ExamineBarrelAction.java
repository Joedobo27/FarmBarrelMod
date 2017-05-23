package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;


public class ExamineBarrelAction implements ModAction, ActionPerformer{

    @Override
    public short getActionId() {
        return Actions.EXAMINE;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter){
        if (num == Actions.EXAMINE && target.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId())
            return action(action, performer, null, target, num, counter);
        else
            return ActionPerformer.super.action(action, performer, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        if (num == Actions.EXAMINE && target.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()) {


            String string = target.getTemplate().getDescriptionLong();
            String s = target.getSignature();
            if (s != null && !s.isEmpty()) {
                string += String.format(" You can barely make out the signature of its maker,  '%s'.", s);
            }
            if (target.getRarity() > 0) {
                string += (MethodsItems.getRarityDesc(target.rarity));
            }
            if (target.color != -1) {
                string += MethodsItems.getColorDesc(target.color);
            }
            performer.getCommunicator().sendNormalServerMessage(string);

            int sowArea = (FarmBarrelMod.decodeSowRadius(target) * 2) +1;
            int supplyQuantity = FarmBarrelMod.decodeSupplyQuantity(target);
            string = String.format("The barrel is set to sow a %d square area and the supply amount is %d.", sowArea, supplyQuantity);
            int cropId = FarmBarrelMod.decodeContainedCropId(target);
            if (cropId != Crops.getLastUsableEntry()) {
                String cropName1 = "";
                try {
                    cropName1 = Crops.getCropNameFromCropId(FarmBarrelMod.decodeContainedCropId(target),
                            FarmBarrelMod.decodeIsSeed(target));
                }catch (NoSuchTemplateException | CropsException ignore) {}
                int containedQuantity = target.getWeightGrams() - 1000 == 0 ? 0 : getContainedQuantity(target);
                string += String.format(" It has %d of %s at %d quality.", containedQuantity, cropName1, FarmBarrelMod.decodeContainedQuality(target));
            }
            performer.getCommunicator().sendNormalServerMessage(string);

            target.sendEnchantmentStrings(performer.getCommunicator());
            String improvedBy = MethodsItems.getImpDesc(performer, target);
            if (!improvedBy.isEmpty()) {
                performer.getCommunicator().sendNormalServerMessage(improvedBy);
            }
            if (target.getTemplate().isRune()) {
                String runeDesc = "";
                if (RuneUtilities.isEnchantRune(target)) {
                    runeDesc = runeDesc + "It can be attached to " + RuneUtilities.getAttachmentTargets(target) + " and will " + RuneUtilities.getRuneLongDesc(RuneUtilities.getEnchantForRune(target)) + ".";
                }
                else if (RuneUtilities.getModifier(RuneUtilities.getEnchantForRune(target), RuneUtilities.ModifierEffect.SINGLE_COLOR) > 0.0f || (RuneUtilities.getSpellForRune(target) != null && RuneUtilities.getSpellForRune(target).targetItem && !RuneUtilities.getSpellForRune(target).targetTile)) {
                    runeDesc = runeDesc + "It can be used on " + RuneUtilities.getAttachmentTargets(target) + " and will " + RuneUtilities.getRuneLongDesc(RuneUtilities.getEnchantForRune(target)) + ".";
                }
                else {
                    runeDesc = runeDesc + "It will " + RuneUtilities.getRuneLongDesc(RuneUtilities.getEnchantForRune(target)) + ".";
                }
                performer.getCommunicator().sendNormalServerMessage(runeDesc);
            }
            return true;
        }
        return ActionPerformer.super.action(action, performer, target, num, counter);
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
}
