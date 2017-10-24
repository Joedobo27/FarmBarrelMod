package com.joedobo27.fbm;


import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.libs.action.ActionMaster.setActionEntryMaxRangeReflect;


public class FarmBarrelMod implements WurmServerMod, Initable, Configurable, ItemTemplatesCreatedListener,
                                      ServerStartedListener, PlayerMessageListener {

    private static int sowBarrelTemplateId;
    static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    @Override public boolean onPlayerMessage(Communicator communicator, String s) {
        return false;
    }

    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title) {
        if (communicator.getPlayer().getPower() == 5 && message.startsWith("/FarmBarrelMod properties")) {
            communicator.getPlayer().getCommunicator().sendNormalServerMessage(
                    "Reloading properties for FarmBarrelMod."
            );
            ConfigureOptions.resetOptions();
            if (ConfigureOptions.getInstance().getSkillUnlockPoints().size() !=
                    ConfigureOptions.getInstance().getSowRadius().size())
                communicator.getPlayer().getCommunicator().sendNormalServerMessage(
                        "sowRadius and skillUnlockPoints are not the same length. This will cause errors."
                );
            return MessagePolicy.DISCARD;
        }
        return MessagePolicy.PASS;
    }

    @Override
    public void configure(Properties properties) {
        ConfigureOptions.setOptions(properties);
        if (ConfigureOptions.getInstance().getSkillUnlockPoints().size() !=
                ConfigureOptions.getInstance().getSowRadius().size()) {
            logger.warning("sowRadius and skillUnlockPoints are not the same length. Setting with defaults instead.");
            ConfigureOptions.getInstance().setSowRadiusWithDefaults();
            ConfigureOptions.getInstance().setSkillUnlockPointsWithDefauts();
        }
    }

    @Override
    public void init() {
        //TODO Can't directly sow with harvested crops even when those crops are something that we should be able
        //TODO   to sow with. One can only sow with things taken from a bulk bin.
        //TODO Add an optional barrel configuration that will automatically replant whatever it just harvested.
        //TODO add a way to harvest bushes/Trees.
        //TODO switch the barrel data state mechanics from using data1&2 fields to using a serialized
        //TODO   object > base64 string > inscription DB entry.

        try {
            ModActions.init();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void onItemTemplatesCreated() {
        ItemTemplateBuilder sowBarrel = new ItemTemplateBuilder("jdbSowBarrel");
        sowBarrelTemplateId = IdFactory.getIdFor("jdbSowBarrel", IdType.ITEMTEMPLATE);
        sowBarrel.name("Seed barrel","seed barrels", "A tool used to sow seed over an area.");
        sowBarrel.size(3);
        //sowBarrel.descriptions();
        sowBarrel.itemTypes(new short[]{ItemTypes.ITEM_TYPE_WOOD, ItemTypes.ITEM_TYPE_NAMED, ItemTypes.ITEM_TYPE_REPAIRABLE,
                ItemTypes.ITEM_TYPE_COLORABLE, ItemTypes.ITEM_TYPE_HASDATA});
        sowBarrel.imageNumber((short) 245);
        sowBarrel.behaviourType((short) 1);
        sowBarrel.combatDamage(0);
        sowBarrel.decayTime(2419200L);
        sowBarrel.dimensions(30, 30, 50);
        sowBarrel.primarySkill(-10);
        //sowBarrel.bodySpaces();
        sowBarrel.modelName("model.container.barrel.small.");
        sowBarrel.difficulty(5);
        sowBarrel.weightGrams(1000);
        sowBarrel.material((byte) 14);
        sowBarrel.value(10000);
        sowBarrel.isTraded(true);
        //sowBarrel.armourType();
        try {
            sowBarrel.build();
        } catch (IOException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void onServerStarted() {

        ConfigureSeedBarrelActionPerformer configure = ConfigureSeedBarrelActionPerformer.getConfigureSeedBarrelActionPerformer();
        ModActions.registerAction(configure.getActionEntry());
        ModActions.registerAction(configure);

        EmptyBarrelActionPerformer empty = EmptyBarrelActionPerformer.getEmptyBarrelActionPerformer();
        ModActions.registerAction(empty);
        setActionEntryMaxRangeReflect(empty.getActionEntry(), 8, logger);

        ExamineBarrelActionPerformer examine = ExamineBarrelActionPerformer.getExamineBarrelActionPerformer();
        ModActions.registerAction(examine);

        FillBarrelActionPerformer fill = FillBarrelActionPerformer.getFillBarrelActionPerformer();
        ModActions.registerAction(fill);

        HarvestActionPerformer harvest = HarvestActionPerformer.getHarvestActionPerformer();
        ModActions.registerAction(harvest);
        setActionEntryMaxRangeReflect(harvest.getActionEntry(), 8, logger);

        SowActionPerformer sow = SowActionPerformer.getSowActionPerformer();
        ModActions.registerAction(sow);
        setActionEntryMaxRangeReflect(sow.getActionEntry(), 8, logger);

        AdvancedCreationEntry sowBarrel = CreationEntryCreator.createAdvancedEntry(SkillList.CARPENTRY,
                ItemList.plank, ItemList.pegWood, sowBarrelTemplateId, false, false, 0.0f, true, false,
                CreationCategories.TOOLS);
        sowBarrel.addRequirement(new CreationRequirement(1, ItemList.plank, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(2, ItemList.pegWood, 4, true));
        sowBarrel.addRequirement(new CreationRequirement(3, ItemList.rope, 1, true));
    }

    static int getSowBarrelTemplateId() {
        return sowBarrelTemplateId;
    }

}
