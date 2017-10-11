package com.joedobo27.farmbarrelmod;


import com.wurmonline.math.TilePos;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.function.Function;

class EmptyBarrelFarmer extends FarmBarrelAction{

    private static WeakHashMap<Action, EmptyBarrelFarmer> actionDataWeakHashMap = new WeakHashMap<>();
    private final Item targetItem;
    private final Item activeTool;
    private final FarmBarrel farmBarrel;


    EmptyBarrelFarmer(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                      int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                      ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions, Item targetItem, FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetItem = targetItem;
        this.activeTool = activeTool;
        this.farmBarrel = farmBarrel;
        actionDataWeakHashMap.put(action, this);
    }

    static boolean hashMapContainsKey(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    @Nullable Item makeItem() {
        try {
            ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(this.farmBarrel.getContainedItemTemplateId());
            return ItemFactory.createItem(itemTemplate.getTemplateId(), (float) this.farmBarrel.getContainedQuality(),
                    (byte) 0, null);
        } catch (NoSuchTemplateException | FailedException e) {
            FarmBarrelMod.logger.warning(e.getMessage());
            return null;
        }
    }

    int getMoveCount() {
        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(this.farmBarrel.getContainedItemTemplateId());
        if (itemTemplate == null)
            return 0;
        int[] counts;
        int targetVolume = this.targetItem.getFreeVolume();
        int targetBulkCount = targetVolume / itemTemplate.getVolume();
        int containedCount = this.farmBarrel.getContainedCount();
        counts = new int[]{targetBulkCount, containedCount};
        Arrays.sort(counts);
        return counts[0];
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
    public Item getActiveTool() {
        return activeTool;
    }

    @Override
    FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

    static WeakHashMap<Action, EmptyBarrelFarmer> getActionDataWeakHashMap() {
        return actionDataWeakHashMap;
    }
}
