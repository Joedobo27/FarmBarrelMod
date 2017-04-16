package com.joedobo27.farmbarrelmod;


import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;


import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ExamineBarrelAction implements ModAction, ActionPerformer{

    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    @Override
    public short getActionId() {
        return Actions.EXAMINE;
    }
    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        if (num == Actions.EXAMINE && target.getTemplateId() == FarmBarrelMod.getSowBarrelTemplateId()) {
            String string = target.getTemplate().getDescriptionLong();
            int cropId = FarmBarrelMod.decodeContainedSeed(target);
            if (cropId != -1) {
                String cropName = "";
                cropName = cropName.chars()
                        .mapToObj(value -> {
                            if (Character.isUpperCase(value))
                                value = Character.toLowerCase(value);
                            return String.valueOf(value);
                        })
                        .collect(Collectors.joining());
                string += " It has " + Integer.toString(FarmBarrelMod.decodeSupplyQuantity(target)) + " of " + cropName +
                        " seed at " + Integer.toString(FarmBarrelMod.decodeContainedQuality(target)) + " QL.";
            }
            performer.getCommunicator().sendNormalServerMessage(string);
        }
        return ActionPerformer.super.action(action, performer, target, num, counter);
    }
}
