package com.joedobo27.fbm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
import org.jetbrains.annotations.Nullable;

import java.net.CacheRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.function.Function;

public class FillBarrelAction extends ActionMaster {

    private final Item targetItem;
    private final Item activeTool;
    private final FarmBarrel farmBarrel;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;

    private static WeakHashMap<Action, FillBarrelAction> performers = new WeakHashMap<>();

    FillBarrelAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                               int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                               ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, Item targetItem,
                               FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
        this.targetItem = targetItem;
        this.activeTool = activeTool;
        this.farmBarrel = farmBarrel;
        performers.put(action, this);
    }

    int getMoveCount() {
        int[] counts;
        ItemTemplate fillTemplate = this.targetItem.getRealTemplate();
        int targetBulkCount = Integer.parseInt(this.targetItem.getDescription().replaceAll("x",""));
        int performerHoldCount = this.performer.getCarryingCapacityLeft() / fillTemplate.getWeightGrams();
        int barrelSupplyCount = this.farmBarrel.getSupplyQuantity() - this.farmBarrel.getContainedCount();
        counts = new int[]{targetBulkCount, performerHoldCount, barrelSupplyCount};
        Arrays.sort(counts);
        return counts[0];
    }

    void subtractBulkTargetCount(int moveCount) {
        int bulkCount = Integer.parseInt(this.targetItem.getDescription().replaceAll("x",""));
        int bulkTotalVolume = this.targetItem.getWeightGrams(); // bulk items use weight value for volume.
        int moveVolume = this.targetItem.getRealTemplate().getVolume() * moveCount;
        this.targetItem.setWeight(bulkTotalVolume - moveVolume, true);
        // bulk items use description data for whole unit counts.
        String newCount = String.format("%dx", bulkCount - moveCount);
        this.targetItem.setDescription(newCount);
    }

    @Nullable static FillBarrelAction getFillBarrelAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    boolean hasAFailureCondition() {
        boolean standardChecks =  failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        boolean fillWithNonFarmSeed = false;
        try {
            fillWithNonFarmSeed = Crops.getCropIdFromTemplateId(this.targetItem.getRealTemplateId()) >=
                    Crops.getCropTypes().length;
        } catch (CropsException e) {
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "The seed barrel won't hold "+targetItem.getRealTemplate().getName()+".");
            return true;
        }
        if (fillWithNonFarmSeed){
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "The seed barrel won't hold "+targetItem.getRealTemplate().getName()+".");
            return true;
        }
        try {
            boolean barrelContentMismatch = this.farmBarrel.getContainedItemTemplateId() != -1 &&
                    Crops.getCropIdFromTemplateId(targetItem.getRealTemplateId()) !=
                            Crops.getCropIdFromTemplateId(this.farmBarrel.getContainedItemTemplateId());
            if (barrelContentMismatch) {
                getPerformer().getCommunicator().sendNormalServerMessage(
                        String.format("The seed barrel won't hold both %s and %s.",
                                Crops.getCropNameFromTemplateId(targetItem.getRealTemplateId()),
                                Crops.getCropNameFromTemplateId(this.farmBarrel.getContainedItemTemplateId())));
                return true;
            }
        } catch (CropsException e) {

        }

        return false;
    }

    @Override
    public TilePos getTargetTile() {
        return null;
    }

    @Override
    public Item getTargetItem() {
        return targetItem;
    }

    @Override
    public Item getActiveTool() {
        return activeTool;
    }

    public FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }
}
