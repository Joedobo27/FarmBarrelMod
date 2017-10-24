package com.joedobo27.fbm;


import com.joedobo27.libs.action.ActionMaster;
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

class EmptyBarrelAction extends ActionMaster {

    private final Item targetItem;
    private final FarmBarrel farmBarrel;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;

    private static WeakHashMap<Action, EmptyBarrelAction> performers = new WeakHashMap<>();


    EmptyBarrelAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                      int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                      ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, Item targetItem, FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
        this.targetItem = targetItem;
        this.farmBarrel = farmBarrel;
        performers.put(action, this);
    }

    @Nullable
    static EmptyBarrelAction getEmptyBarrelAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    boolean hasAFailureCondition() {
        boolean standardChecks =  failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        ItemTemplate containedItemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(
                farmBarrel.getContainedItemTemplateId());

        boolean foodInNonFoodContainer = containedItemTemplate != null && containedItemTemplate.isFood() && this.targetItem.getTemplateId() ==
                ItemList.bulkContainer;
        if (foodInNonFoodContainer) {
            this.performer.getCommunicator().sendNormalServerMessage("" +
                    "Food items go in the food storage bin, not a bulk storage bin.");
            return true;
        }
        boolean nonFoodInFoodContainer = containedItemTemplate != null && !containedItemTemplate.isFood() &&
                this.targetItem.getTemplateId() == ItemList.hopper;
        if (nonFoodInFoodContainer) {
            this.performer.getCommunicator().sendNormalServerMessage("" +
                    "Non-food items go in the bulk storage bin, not a food storage bin.");
            return true;
        }
        return false;
    }

    @Nullable
    Item makeItem() {
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
    public Item getTargetItem() {
        return targetItem;
    }

    @Override
    public TilePos getTargetTile() {
        return null;
    }

    @Override
    public Item getActiveTool() {
        return activeTool;
    }

    FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

}
