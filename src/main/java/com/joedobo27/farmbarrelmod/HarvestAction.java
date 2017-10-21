package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.LinearScalingFunction;
import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

class HarvestAction extends ActionMaster {

    private final TilePos targetTile;
    private final FarmBarrel farmBarrel;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;

    private static WeakHashMap<Action, HarvestAction> performers = new WeakHashMap<>();

    HarvestAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                            int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TilePos targetTile,
                            FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
        this.targetTile = targetTile;
        this.farmBarrel = farmBarrel;
        performers.put(action, this);
    }

    @Nullable static HarvestAction getHarvestAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    boolean hasAFailureCondition() {
        boolean standardChecks =  failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        boolean barrelContentMismatch = this.getTargetItem().getRealTemplateId() != this.farmBarrel.getContainedItemTemplateId();
        if (barrelContentMismatch) {
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "The seed barrel won't hold both "+farmBarrel.getCropName()+" and "+
                    this.getTargetItem().getRealTemplate().getName()+".");
            return true;
        }
        return false;
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return 1.0d;
        int templateId = TileUtilities.getFarmTileCropId(this.targetTile);
        double difficulty = Crops.getDifficultyFromTemplateId(templateId);
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    int getYield() {
        ConfigureOptions.HarvestYieldOptions yieldOptions = ConfigureOptions.getInstance().getSowYieldScaling();
        LinearScalingFunction baseYieldFunction = LinearScalingFunction.make(yieldOptions.getMinimumBaseYield(),
                yieldOptions.getMaximumBaseYield(), yieldOptions.getMinimumSkill(), yieldOptions.getMaximumSkill());
        double modifiedSkill = this.performer.getSkills().getSkillOrLearn(SkillList.FARMING).
                getKnowledge(this.activeTool, 0);
        double baseYield = baseYieldFunction.doFunctionOfX(modifiedSkill);


        LinearScalingFunction bonusYieldFunction = LinearScalingFunction.make(yieldOptions.getMinimumBonusYield(),
                yieldOptions.getMaximumBonusYield(), yieldOptions.getMinimumFarmChance(), yieldOptions.getMaximumFarmChance());
        // In resourceMesh the farmChance value is cumulatively stored using mask 0B0000 0111 1111 1111 or
        //      0x7FF which is 2047.
        double farmedChance = TileUtilities.getFarmTileCumulativeChance(this.targetTile);
        int farmedCount = TileUtilities.getFarmTileTendCount(this.targetTile);
        int toolRarity;
        if (this.activeTool == null)
            toolRarity = 0;
        else
            toolRarity = this.activeTool.getRarity();
        double harvestChance= (this.action.getRarity() * 110) + (toolRarity * 50) +
                (Math.min(5, farmedCount) * 50);
        farmedChance += harvestChance;
        double bonusYield = bonusYieldFunction.doFunctionOfX(farmedChance);

        return (int)Math.round(baseYield + bonusYield);
    }

    void alterTileState() {
        TileUtilities.setSurfaceTypeId(this.targetTile, Tiles.TILE_TYPE_DIRT);
        Server.modifyFlagsByTileType(this.targetTile.x, this.targetTile.y, Tiles.Tile.TILE_DIRT.id);
        this.performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(this.targetTile.x, this.targetTile.y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(this.targetTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.targetTile.x, this.targetTile.y);
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
}
