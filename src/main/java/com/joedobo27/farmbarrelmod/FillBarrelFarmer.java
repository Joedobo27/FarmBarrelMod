package com.joedobo27.farmbarrelmod;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.function.Function;

public class FillBarrelFarmer extends FarmBarrelAction {

    private static WeakHashMap<Action, FillBarrelFarmer> actionDataWeakHashMap = new WeakHashMap<>();
    private final Item targetItem;
    private final Item activeTool;
    private final FarmBarrel farmBarrel;

    protected FillBarrelFarmer(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                               int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                               ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions, Item targetItem,
                               FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetItem = targetItem;
        this.activeTool = activeTool;
        this.farmBarrel = farmBarrel;
        actionDataWeakHashMap.put(action, this);
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
        int bulkVolume = this.targetItem.getVolume();
        int moveVolume = this.targetItem.getRealTemplate().getVolume() * moveCount;
        String newDescription = String.format("%dx", bulkCount - moveCount);
        this.targetItem.setWeight(bulkVolume - moveCount, true);
        this.targetItem.setDescription(newDescription);
    }

    static boolean hashMapContainsKey(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    @Override
    boolean hasAFailureCondition() {
        return this.getFailureTestFunctions().stream()
                .anyMatch(function -> function.apply(this));
    }

    @Override
    TilePos getTargetTile() {
        return null;
    }

    @Override
    Item getTargetItem() {
        return targetItem;
    }

    @Override
    Item getActiveTool() {
        return activeTool;
    }

    @Override
    public FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

    static WeakHashMap<Action, FillBarrelFarmer> getActionDataWeakHashMap() {
        return actionDataWeakHashMap;
    }
}
