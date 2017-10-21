package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.LinearScalingFunction;
import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionFailureFunction;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.shared.util.MaterialUtilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

class SowAction extends ActionMaster {

    private final TilePos targetTile;
    private final FarmBarrel farmBarrel;
    private int totalTileCount;
    private int lastWholeUnitTime;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;

    private static WeakHashMap<Action, SowAction> performers = new WeakHashMap<>();

    SowAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                        int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina, TilePos targetTile,
                        FarmBarrel farmBarrel, ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.targetTile = targetTile;
        this.farmBarrel = farmBarrel;
        this.failureTestFunctions = failureTestFunctions;
        performers.put(action, this);
    }

    @Nullable static SowAction getSowAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    LinkedList<TilePos> selectSowTiles() {
        int sowRadius = this.farmBarrel.getSowRadius();
        int westY = this.targetTile.y - sowRadius;
        int eastY = this.targetTile.y + sowRadius;
        int northX = this.targetTile.x - sowRadius;
        int southX = this.targetTile.x + sowRadius;

        LinkedList<TilePos> sowTiles = new LinkedList<>();
        IntStream.range(northX, southX + 1)
                .forEach(X -> IntStream.range(westY, eastY + 1)
                        .forEach(Y -> {
                            TilePos tilePos = TilePos.fromXY(X, Y);
                            if (isValidSowTile(tilePos))
                                 sowTiles.add(tilePos);
                        }));
        synchronized (this) {
            this.totalTileCount = sowTiles.size();
        }
        return sowTiles;
    }

    private boolean isValidSowTile(TilePos tilePos) {
        // can only sow on dirt tiles.
        if (TileUtilities.getSurfaceTypeId(tilePos) != Tiles.TILE_TYPE_DIRT)
            return false;

        // is the tile flat enough?
        if (!Terraforming.isFlat(this.targetTile.x, this.targetTile.y, this.performer.isOnSurface(),
                ConfigureOptions.getInstance().getMaxSowingSlope())) {
            return false;
        }

        // land or water plant mismatch.
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

        // can't so tiles inside houses.
        VolaTile volaTile = Zones.getOrCreateTile(tilePos, this.performer.isOnSurface());
        Structure structure = volaTile.getStructure();
        if (structure != null)
            return false;

        // can't so tiles inside bridge supports.
        BridgePart[] bridgeParts = volaTile.getBridgeParts();
        if (bridgeParts != null && bridgeParts.length > 0 && Arrays.stream(bridgeParts)
                    .anyMatch(bridgePart -> bridgePart.getType().isSupportType()))
            return false;

        // village permissions
        Village village = Zones.getVillage(this.targetTile.x, this.targetTile.y, this.performer.isOnSurface());
        if (village != null &&
                !village.isActionAllowed((short) this.action.getNumber(), this.performer,
                        false, TileUtilities.getSurfaceEncodedValue(this.getTargetTile()),
                        0) &&
                !village.isEnemy(this.performer) && this.performer.isLegal())
            return false;
        if (village != null && !village.isActionAllowed((short) this.action.getNumber(), this.performer, false,
                TileUtilities.getSurfaceEncodedValue(this.getTargetTile()), 0) &&
                !Zones.isOnPvPServer(this.targetTile.x,this.targetTile.y))
            return false;

        // god protected
        if (Zones.isTileProtected(this.targetTile.x, this.targetTile.y))
            return false;

        return true;
    }

    boolean unitTimeJustTicked(float counter){
        int unitTime = (int)(Math.floor((counter * 100) / (this.actionTimeTenthSecond * 10)));
        if (unitTime != this.lastWholeUnitTime){
            synchronized (this) {
                this.lastWholeUnitTime = unitTime;
            }
            return true;
        }
        return false;
    }

    boolean hasAFailureCondition() {
        boolean standardChecks =  failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        boolean insufficientSkillSorSowArea = this.farmBarrel.getSowRadius();
        failureFunctions.put(27, new ActionFailureFunction("FAILURE_FUNCTION_INSUFFICIENT_SKILL_FOR_SOW_AREA",
                actionMaster -> {
                    int sowBarrelRadius = actionMaster.getFarmBarrel().getSowRadius();
                    int sowDimension = (sowBarrelRadius * 2) + 1;
                    String sowArea = String.format("%d by %d area",sowDimension, sowDimension);
                    if (actionMaster.getMaxRadiusFromFarmSkill(actionMaster.getPerformer()) < sowBarrelRadius) {
                        actionMaster.getPerformer().getCommunicator().sendNormalServerMessage(
                                "You don't have enough farming skill to sow a "+sowArea+".");
                        return true;
                    }
                    return false;
                }));
    }

    double doSkillCheckAndGetPower() {
        if (this.usedSkill == null)
            return 1.0d;
        double difficulty = Crops.getDifficultyFromTemplateId(farmBarrel.getContainedItemTemplateId());
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, this.actionTimeTenthSecond/10));
    }

    void alterTileState() {
        byte newFarmTile = Crops.getTileTypeFromTemplateId(this.farmBarrel.getContainedItemTemplateId());
        TileUtilities.setSurfaceTypeId(this.targetTile, newFarmTile);
        Server.modifyFlagsByTileType(this.targetTile.x, this.targetTile.y, newFarmTile);
        Players.getInstance().sendChangedTile(this.targetTile.x, this.targetTile.y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(this.targetTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.targetTile.x, this.targetTile.y);
    }

    void updateMeshResourceData() {
        if (this.usedSkill == null)
            return;
        int resource = TileUtilities.encodeResourceFarmTileData(0, (int)Math.min(2047, 100.0 -
                        this.performer.getSkills().getSkillOrLearn(this.usedSkill).getKnowledge() +
                this.activeTool.getQualityLevel() +(this.activeTool.getRarity() * 20) + (action.getRarity() * 50)));
        Server.setWorldResource(this.targetTile.x, this.targetTile.y, resource);
    }

    @Override
    public TilePos getTargetTile() {
        return targetTile;
    }

    @Override
    public Item getTargetItem() {
        return null;
    }

    @Override
    public Item getActiveTool() {
        return activeTool;
    }

    FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

    @Override
    public void setInitialTime(ActionEntry actionEntry) {
        final double MAX_WOA_EFFECT = 0.20;
        final double TOOL_RARITY_EFFECT = 0.10;
        final double ACTION_RARITY_EFFECT = 0.33;

        if (this.action == null || this.activeTool == null)
            return;
        Skill toolSkill = null;
        double bonus = 0;
        if (this.activeTool.hasPrimarySkill()) {
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
        LinearScalingFunction linearScalingFunction = LinearScalingFunction.make(this.minSkill, this.maxSkill,
                this.longestTime, this.shortestTime);
        double time = linearScalingFunction.doFunctionOfX(modifiedKnowledge);

        if (this.activeTool.getSpellSpeedBonus() != 0.0f)
            time = Math.max(this.shortestTime, time * (1 - (MAX_WOA_EFFECT *
                    this.activeTool.getSpellSpeedBonus() / 100.0)));

        if (this.activeTool.getRarity() != MaterialUtilities.COMMON)
            time = Math.max(this.shortestTime, time * (1 - (this.activeTool.getRarity() *
                    TOOL_RARITY_EFFECT)));

        if (this.action.getRarity() != MaterialUtilities.COMMON)
            time = Math.max(this.shortestTime, time * (1 - (this.action.getRarity() * ACTION_RARITY_EFFECT)));

        if (this.activeTool.getSpellEffects() != null && this.activeTool.getSpellEffects().getRuneEffect() != -10L)
            time = Math.max(this.shortestTime, time * (1 -
                    RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(),
                            RuneUtilities.ModifierEffect.ENCH_USESPEED)));

        synchronized (this) {
            this.actionTimeTenthSecond = (int) time;
            this.action.setTimeLeft(this.actionTimeTenthSecond * this.totalTileCount);
        }
        this.performer.sendActionControl(actionEntry.getVerbString(), true,
                this.actionTimeTenthSecond * this.totalTileCount);
    }

    int getTotalTileCount() {
        return totalTileCount;
    }
}
