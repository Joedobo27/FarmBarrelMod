package com.joedobo27.fbm;

import com.joedobo27.libs.LinearScalingFunction;
import com.joedobo27.libs.TileUtilities;
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

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

class SowAction extends ActionMaster {

    private final TilePos targetTile;
    private final FarmBarrel farmBarrel;
    private int lastWholeUnitTime;
    private LinkedList<TilePos> sowTiles;
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

    void selectSowingTiles() {
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
                            if (isValidSowTile(tilePos) && sowTiles.size() < this.farmBarrel.getContainedCount())
                                 sowTiles.add(tilePos);
                        }));
        synchronized (this) {
            this.sowTiles = sowTiles;
        }
    }

    TilePos getNextSowTile() {
        return sowTiles.remove();
    }

    private boolean isValidSowTile(TilePos tilePos) {
        // can only sow on dirt tiles.
        if (TileUtilities.getSurfaceTypeId(tilePos) != Tiles.TILE_TYPE_DIRT)
            return false;

        // is the tile flat enough?
        if (!Terraforming.isFlat(tilePos.x, tilePos.y, this.performer.isOnSurface(),
                ConfigureOptions.getInstance().getMaxSowingSlope())) {
            return false;
        }

        // successful fetch of contained seed item template;
        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(
                this.farmBarrel.getContainedItemTemplateId());
        if (itemTemplate == null) {
            return false;
        }

        // land or water plant mismatch.
        boolean isUnderWater = Terraforming.isCornerUnderWater(tilePos.x, tilePos.y,
                this.performer.isOnSurface());

        boolean isWaterPlant = itemTemplate.getTemplateId() == ItemList.rice ||
                itemTemplate.getTemplateId() == ItemList.reedSeed || itemTemplate.getTemplateId() == ItemList.reed;
        if (isUnderWater && !isWaterPlant) {
            return false;
        }
        if (isWaterPlant && !Terraforming.isAllCornersInsideHeightRange(tilePos.x, tilePos.y,
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
        Village village = Zones.getVillage(tilePos.x, tilePos.y, this.performer.isOnSurface());
        if (village != null &&
                !village.isActionAllowed(this.action.getNumber(), this.performer,
                        false, TileUtilities.getSurfaceEncodedValue(this.getTargetTile()),
                        0) &&
                !village.isEnemy(this.performer) && this.performer.isLegal())
            return false;
        if (village != null && !village.isActionAllowed(this.action.getNumber(), this.performer, false,
                TileUtilities.getSurfaceEncodedValue(this.getTargetTile()), 0) &&
                !Zones.isOnPvPServer(tilePos.x,tilePos.y))
            return false;

        // god protected
        if (Zones.isTileProtected(tilePos.x, tilePos.y))
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

        // Check player's farm skill against against reqired for barrel's radius setting.
        ArrayList<Integer> skillBrackets = ConfigureOptions.getInstance().getSkillUnlockPoints();
        ArrayList<Integer> radii = ConfigureOptions.getInstance().getSowRadius();
        if (skillBrackets.size() != radii.size()){
            this.performer.getCommunicator().sendNormalServerMessage(
                    "Something went wrong, sorry.");
            return true;
        }
        int ordinal = IntStream.range(0, radii.size())
                .filter(value -> this.farmBarrel.getSowRadius() == radii.get(value))
                .findAny()
                .orElse(-1);
        if (ordinal == -1){
            this.performer.getCommunicator().sendNormalServerMessage(
                    String.format("%d is not a valid sow radius setting.", this.farmBarrel.getSowRadius()));
            return true;
        }
        int minimumSkill = skillBrackets.get(ordinal);
        if (this.usedSkill == null){
            this.performer.getCommunicator().sendNormalServerMessage(
                    "Something went wrong, sorry.");
            return true;
        }
        if (this.performer.getSkills().getSkillOrLearn(this.usedSkill).getKnowledge() < minimumSkill) {
            this.performer.getCommunicator().sendNormalServerMessage(
                    String.format("You need at least %d farming skill to sow a %d x %d area.", minimumSkill,
                            (this.farmBarrel.getSowRadius() * 2) + 1, (this.farmBarrel.getSowRadius() * 2) + 1));
            return true;
        }
        return false;
    }

    double doSkillCheckAndGetPower() throws CropsException {
        if (this.usedSkill == null)
            return 1.0d;
        double difficulty = Crops.getDifficultyFromTemplateId(farmBarrel.getContainedItemTemplateId());
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, this.actionTimeTenthSecond/10));
    }

    void alterTileState(TilePos sowTile) throws CropsException {
        byte newTileTypeId = Crops.getTileTypeFromTemplateId(this.farmBarrel.getContainedItemTemplateId());
        byte newTileData = TileUtilities.encodeSurfaceFarmTileData(false, 0,
                    Crops.getCropIdFromTemplateId(this.farmBarrel.getContainedItemTemplateId()));
        Server.surfaceMesh.setTile(sowTile.x, sowTile.y, Tiles.encode(TileUtilities.getSurfaceHeight(sowTile),
                newTileTypeId, newTileData));
        Server.modifyFlagsByTileType(sowTile.x, sowTile.y, newTileTypeId);
        Players.getInstance().sendChangedTile(sowTile.x, sowTile.y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(sowTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(sowTile.x, sowTile.y);
    }

    void updateMeshResourceData(TilePos sowTile) {
        if (this.usedSkill == null || this.activeTool == null)
            return;
        int sowResult = (int)Math.min(2047,
                100.0 - this.performer.getSkills().getSkillOrLearn(this.usedSkill).getKnowledge() +
                this.activeTool.getQualityLevel() + (this.activeTool.getRarity() * 20) +
                (action.getRarity() * 50));
        int resource = TileUtilities.encodeResourceFarmTileData(0, sowResult);
        Server.setWorldResource(sowTile.x, sowTile.y, resource);
    }

    public LinkedList<TilePos> getSowTiles() {
        return sowTiles;
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
            this.action.setTimeLeft(this.actionTimeTenthSecond * this.sowTiles.size());
        }
        this.performer.sendActionControl(actionEntry.getVerbString(), true,
                this.actionTimeTenthSecond * this.sowTiles.size());
    }
}
