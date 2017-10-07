package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.LinearScaldingFunction;
import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;


class SowFarmer extends FarmBarrelAction {

    private static WeakHashMap<Action, SowFarmer> actionDataWeakHashMap = new WeakHashMap<>();
    private final TilePos targetTile;
    private final Item activeTool;
    private final FarmBarrel farmBarrel;
    private int totalTileCount;

    protected SowFarmer(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill, int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina, ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions, TilePos targetTile, FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina, failureTestFunctions);
        this.targetTile = targetTile;
        this.activeTool = activeTool;
        this.farmBarrel = farmBarrel;
        actionDataWeakHashMap.put(action, this);
    }

    ArrayList<TilePos> selectSowTiles() {
        int sowRadius = this.farmBarrel.getSowRadius();
        int westY = this.targetTile.y - sowRadius;
        int eastY = this.targetTile.y + sowRadius;
        int northX = this.targetTile.x - sowRadius;
        int southX = this.targetTile.x + sowRadius;

        ArrayList<TilePos> sowTiles = new ArrayList<>();
        IntStream.range(northX, southX + 1)
                .forEach(X -> IntStream.range(westY, eastY + 1)
                        .forEach(Y -> {
                            TilePos tilePos = TilePos.fromXY(X, Y);
                            if (isValidSowTile(TilePos.fromXY(X, Y)))
                                 sowTiles.add(tilePos);
                        }));
        synchronized (this) {
            this.totalTileCount = sowTiles.size();
        }
        return sowTiles;
    }

    private boolean isValidSowTile(TilePos tilePos) {
        if (TileUtilities.getSurfaceTypeId(tilePos) != Tiles.TILE_TYPE_DIRT)
            return false;
        if (!Terraforming.isFlat(this.targetTile.x, this.targetTile.y, this.performer.isOnSurface(),
                ConfigureOptions.getInstance().getMaxSowingSlope())) {
            return false;
        }
        boolean isUnderWater = Terraforming.isCornerUnderWater(this.targetTile.x, this.targetTile.y,
                this.performer.isOnSurface());
        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(
                this.farmBarrel.getContainedItemTemplateId());
        boolean isWaterPlant = itemTemplate.getTemplateId() == ItemList.rice ||
                itemTemplate.getTemplateId() == ItemList.reedSeed;

        if (isUnderWater && !isWaterPlant) {
            return false;
        }
        if (isWaterPlant && !Terraforming.isAllCornersInsideHeightRange(this.targetTile.x, this.targetTile.y,
                this.performer.isOnSurface(), (short)(-1), (short)(-4))) {
            return false;
        }
        return true;
    }

    boolean unitTimeJustTicked(float counter){
        int unitTime = (int)(Math.floor((counter * 100) / (this.unitSowTimeInterval * 10)));
        if (unitTime != this.lastWholeUnitTime){
            this.lastWholeUnitTime = unitTime;
            return true;
        }
        return false;
    }

    @Override
    boolean hasAFailureCondition() {
        return this.getFailureTestFunctions().stream()
                .anyMatch(function -> function.apply(this));
    }

    static boolean hashMapContainsKey(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    @Override
    TilePos getTargetTile() {
        return targetTile;
    }

    @Override
    Item getTargetItem() {
        return null;
    }

    @Override
    Item getActiveTool() {
        return activeTool;
    }

    @Override
    FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

    @Override
    public void setInitialTime(ActionEntry actionEntry) {
        Skill toolSkill = null;
        double bonus = 0;
        if (this.activeTool != null && this.activeTool.hasPrimarySkill()) {
            try {
                toolSkill = this.performer.getSkills().getSkillOrLearn(this.activeTool.getPrimarySkill());
            } catch (NoSuchSkillException ignore) {}
        }
        if (toolSkill != null) {
            bonus = toolSkill.getKnowledge() / 10;
        }

        double modifiedKnowledge;
        if (this.usedSkill == null)
            modifiedKnowledge = 99;
        else
            modifiedKnowledge = this.performer.getSkills().getSkillOrLearn(this.usedSkill).getKnowledge(this.activeTool,
                    bonus);
        LinearScaldingFunction lsf = LinearScaldingFunction.make(this.minSkill, this.maxSkill, this.longestTime,
                this.shortestTime);
        int time = (int)lsf.doFunctionOfX(modifiedKnowledge);

        time = woaReduce(time);
        time = itemRarityReduce(time);
        time = actionRarityReduce(time);
        time = runeReduce(time);
        time *= this.totalTileCount;
        this.action.setTimeLeft(time);
        this.performer.sendActionControl(actionEntry.getVerbString(), true, time);
    }

    private int woaReduce(int time) {
        if (this.activeTool == null || this.activeTool.getSpellSpeedBonus() == 0.0f)
            return time;
        return (int)(Math.max(this.shortestTime, time * (1 -
                (MAX_WOA_EFFECT.reductionMultiple * this.activeTool.getSpellSpeedBonus() / 100.0))));
    }

    private int itemRarityReduce( int time) {
        if (this.activeTool == null || this.activeTool.getRarity() == 0)
            return time;
        return (int)(Math.max(this.shortestTime, time * (1 - (this.activeTool.getRarity() * TOOL_RARITY_EFFECT.reductionMultiple))));
    }

    private int actionRarityReduce(int time) {
        if (this.action == null || this.action.getRarity() == 0)
            return time;
        return (int)(Math.max(this.shortestTime, time * (1 - (this.action.getRarity() * ACTION_RARITY_EFFECT.reductionMultiple))));
    }

    private int runeReduce(int time) {
        if (this.activeTool == null || this.activeTool.getSpellEffects() == null ||
                this.activeTool.getSpellEffects().getRuneEffect() == -10L)
            return time;
        return (int)(Math.max(this.shortestTime, time * (1 - RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(),
                RuneUtilities.ModifierEffect.ENCH_USESPEED))));
    }

    int getTotalTileCount() {
        return totalTileCount;
    }

    static WeakHashMap<Action, SowFarmer> getActionDataWeakHashMap() {
        return actionDataWeakHashMap;
    }
}
