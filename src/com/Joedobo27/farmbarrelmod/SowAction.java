package com.Joedobo27.farmbarrelmod;

import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


class SowAction implements ModAction, ActionPerformer {

    private static final Logger logger = Logger.getLogger(SowAction.class.getName());

    @Override
    public short getActionId(){
        return Actions.SOW;
    }

    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int tile, short actionId, float counter) {
        if (actionId == Actions.SOW && source.getTemplateId() == FarmBarrelMod.getSowBarrelId()) {
            try {
                int time;
                SowBarrelData sowBarrelData = new SowBarrelData(performer, source);
                float ACTION_START_TIME = 1.0f;
                if (counter == ACTION_START_TIME) {
                    if (!checkRequirements(sowBarrelData)) {
                        return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, tile, actionId, counter);
                    }
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);

                    time = getInitialActionTime(sowBarrelData, action);
                    performer.getCurrentAction().setTimeLeft(time);
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
                    return false;
                } else {
                    time = action.getTimeLeft();
                }
                boolean isEndOfTileSowing = action.currentSecond() % sowBarrelData.getSeedsToSow() == 0;
                boolean isStartOfTileSowing = action.currentSecond() % sowBarrelData.getSeedsToSow() == 1;
                if (isStartOfTileSowing) {
                    // check if the tile can be sown. If not reduce setTimeLeft(time).
                }
                if (isEndOfTileSowing) {
                    // sow a tile, consume a seed's worth of volume.
                    // do skill check
                }
                if (counter * 10.0f > time) {
                    // is this needed?
                }
            }catch (NoSuchActionException e) {
                SowAction.logger.log(Level.INFO, "This action does not exist?", e);
            }
        }
        return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, tile, actionId, counter);
    }

    private class SowBarrelData {
        private Creature performer;
        private Item barrel;
        private Item seed;
        private int seedCount;

        SowBarrelData(Creature performer, Item barrel) {
            this.performer = performer;
            this.barrel = barrel;
            this.seed = Arrays.stream(barrel.getAllItems(false))
                    .findFirst()
                    .orElse(null);
            if (seed.isBulkItem() && seedIsSeed()) {
                int descriptionCount = Integer.parseInt(seed.getDescription().replaceAll("x",""));
                int volumeCount = Math.floorDiv(seed.getWeightGrams(), seed.getRealTemplate().getWeightGrams());
                seedCount = Math.min(descriptionCount, volumeCount);
            } else {
                this.seedCount = 0;
            }
        }

        private int getSowBarrelRadius() {
            int i = this.barrel.getData1() & 0x0f;
            // Item.getData1() returns -1 when the Item instance's data field is null.
            return i == -1 ? 0 : i;
        }

        private boolean seedIsSeed() {
            return seed.getRealTemplate().isSeed();
        }

        private boolean seedIsGreaterSow() {
            int seedCountNeeded = (int) Math.pow((getSowBarrelRadius() * 2) + 1, 2);
            return seedCount > seedCountNeeded;
        }

        @SuppressWarnings("ConstantConditions")
        private void consumeSeed() {
            // The Item.setWeight() methods uses ambiguous boolean logic control args. Added named for readability.
            boolean updateOwner = false;
            boolean destroyOnWeightZero = true;

            // Bulk items (ItemList.bulkItem or ID int 669) store volume in the weight DB entry.
            int totalBulkVolume = seed.getWeightGrams();
            int reduceBulkVolume = seedCount * seed.getRealTemplate().getWeightGrams();
            int newVolume = totalBulkVolume - reduceBulkVolume;
            seed.setWeight(newVolume, destroyOnWeightZero, updateOwner);
        }

        private int getSowDimension() {
            return (getSowBarrelRadius() * 2) + 1;
        }

        private int getSeedsToSow() {
            return (int) Math.pow((getSowBarrelRadius() * 2) + 1, 2);
        }
    }

    private static int getInitialActionTime(SowBarrelData sowBarrelData, Action action){
        Skill farmingSkill = sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        double bonus = Math.max(10, sowBarrelData.performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
        Item lowestQualityItem = sowBarrelData.seed.getQualityLevel() > sowBarrelData.barrel.getQualityLevel() ? sowBarrelData.seed : sowBarrelData.barrel;
        double knowledge = farmingSkill.getKnowledge(lowestQualityItem, bonus);

        final float multiplier = 1.3f / Servers.localServer.getActionTimer();
        double time = (100.0 - knowledge) * multiplier;

        // woa
        if (sowBarrelData.barrel != null && sowBarrelData.barrel.getSpellSpeedBonus() > 0.0f) {
            time = 30.0 + time * (1.0 - 0.2 * sowBarrelData.barrel.getSpellSpeedBonus() / 100.0);
        } else {
            time += 30.0;
        }

        //rare barrel item, 10% speed reduction per rarity level.
        int barrelRarity = sowBarrelData.barrel.getRarity();
        double rarityBarrelBonus = barrelRarity == 0 ? 1 : barrelRarity * 0.1;
        time *= rarityBarrelBonus;
        //rare sowing action, 30% speed reduction per rarity level.
        int actionRarity = action.getRarity();
        double rarityActionBonus = actionRarity == 0 ? 1 : actionRarity * 0.3;
        time *= rarityActionBonus;

        time *= sowBarrelData.getSeedsToSow();
        return (int) Math.max(10, time);
    }

    private static boolean checkRequirements(SowBarrelData sowBarrelData) {
        boolean noSeedWithin = Objects.equals(sowBarrelData.seed, null);
        if (noSeedWithin) {
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("The seed barrel is empty.");
            return false;
        }
        ItemTemplate seedTemplate = sowBarrelData.seed.getRealTemplate();
        if (!sowBarrelData.seed.isSeed()) {
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("Only seed can be sown.");
            return false;
        }
        int sowBarrelRadius = sowBarrelData.getSowBarrelRadius();
        String sowArea = sowBarrelData.getSowDimension() + " by " + sowBarrelData.getSowDimension() + " area.";
        boolean farmSkillNotEnoughForBarrelRadius = getMaxRadiusFromFarmSkill(sowBarrelData.performer) < sowBarrelRadius;
        if (farmSkillNotEnoughForBarrelRadius){
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage( "You don't have enough farming skill to sow a " + sowArea);
            return false;
        }
        if (!sowBarrelData.seedIsGreaterSow()) {
            String seedName = seedTemplate.getName();
            sowBarrelData.performer.getCommunicator().sendNormalServerMessage("You don't have enough " + seedName + " to sow a " + sowArea);
            return false;
        }
        return true;
    }

    private static int getMaxRadiusFromFarmSkill(Creature performer) {
        double farmingLevel = performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge();
        int[] sowRadius = FarmBarrelMod.getSowRadius();
        int[] skillUnlockPoints = FarmBarrelMod.getSkillUnlockPoints();

        int maxIndex = skillUnlockPoints.length - 1;
        for (int i = 0; i <= maxIndex; i++) {
            if (i == maxIndex && farmingLevel >= skillUnlockPoints[i]) {
                return sowRadius[i];
            } else if (farmingLevel >= skillUnlockPoints[i] && farmingLevel < skillUnlockPoints[i + 1]) {
                return sowRadius[i];
            }
        }
        return sowRadius[0];
    }

}
