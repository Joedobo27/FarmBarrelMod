package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.LinearScaldingFunction;
import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

class HarvestFarmer extends FarmBarrelAction {

    private static WeakHashMap<Action, HarvestFarmer> actionDataWeakHashMap = new WeakHashMap<>();
    private final TilePos targetTile;
    private final Item activeTool;
    private final FarmBarrel farmBarrel;

    protected HarvestFarmer(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                            int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                            ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions, TilePos targetTile,
                            FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetTile = targetTile;
        this.activeTool = activeTool;
        this.farmBarrel = farmBarrel;
        actionDataWeakHashMap.put(action, this);
    }

    @Override
    boolean hasAFailureCondition() {
        return this.getFailureTestFunctions().stream()
                .anyMatch(function -> function.apply(this));
    }

    static boolean hashMapContainsKey(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return 1.0d;
        int templateId = TileUtilities.getFarmTileCropId(this.targetTile);
        double difficulty = getCropDifficulty(templateId);
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    private double getCropDifficulty(int templateId){
        double difficulty = 1.0d;
        switch (templateId) {
            case ItemList.barley:
                difficulty = 20.0d;
                break;
            case ItemList.wheat:
                difficulty = 30.0d;
                break;
            case ItemList.rye:
                difficulty = 10.0d;
                break;
            case ItemList.oat:
                difficulty = 15.0d;
                break;
            case ItemList.corn:
                difficulty = 40.0d;
                break;
            case ItemList.pumpkin:
                difficulty = 15.0d;
                break;
            case ItemList.pumpkinSeed:
                difficulty = 15.0d;
                break;
            case ItemList.potato:
                difficulty = 4.0d;
                break;
            case ItemList.cotton:
                difficulty = 7.0d;
                break;
            case ItemList.cottonSeed:
                difficulty = 7.0d;
                break;
            case ItemList.wemp:
                difficulty = 10.0d;
                break;
            case ItemList.wempSeed:
                difficulty = 10.0d;
                break;
            case ItemList.garlic:
                difficulty = 70.0d;
                break;
            case ItemList.onion:
                difficulty = 60.0d;
                break;
            case ItemList.reed:
                difficulty = 20.0d;
                break;
            case ItemList.reedSeed:
                difficulty = 20.0d;
                break;
            case ItemList.rice:
                difficulty = 80.0d;
                break;
            case ItemList.strawberries:
                difficulty = 60.0d;
                break;
            case ItemList.strawberrySeed:
                difficulty = 60.0d;
                break;
            case ItemList.carrot:
                difficulty = 25.0d;
                break;
            case ItemList.carrotSeeds:
                difficulty = 25.0d;
                break;
            case ItemList.cabbage:
                difficulty = 35.0d;
                break;
            case ItemList.cabbageSeeds:
                difficulty = 35.0d;
                break;
            case ItemList.tomato:
                difficulty = 45.0d;
                break;
            case ItemList.tomatoSeeds:
                difficulty = 45.0d;
                break;
            case ItemList.sugarBeet:
                difficulty = 85.0d;
                break;
            case ItemList.sugarBeetSeeds:
                difficulty = 85.0d;
                break;
            case ItemList.lettuce:
                difficulty = 55.0d;
                break;
            case ItemList.lettuceSeeds:
                difficulty = 55.0d;
                break;
            case ItemList.pea:
                difficulty = 65.0d;
                break;
            case ItemList.peaPod:
                difficulty = 65.0d;
                break;
            case ItemList.cucumber:
                difficulty = 15.0d;
                break;
            case ItemList.cucumberSeeds:
                difficulty = 15.0d;
                break;
        }
        return difficulty;
    }

    int getYield() {
        LinearScaldingFunction baseYieldFunction = LinearScaldingFunction.make(2.0d, 9.0d,
                1.0d, 99.999999d);
        double modifiedSkill = this.performer.getSkills().getSkillOrLearn(SkillList.FARMING).
                getKnowledge(this.activeTool, 0);
        double baseYield = baseYieldFunction.doFunctionOfX(modifiedSkill);

        LinearScaldingFunction bonusYieldFunction = LinearScaldingFunction.make(0.0d, 11.0d,
                0.0d, 2047.0d);
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

    static WeakHashMap<Action, HarvestFarmer> getActionDataWeakHashMap() {
        return actionDataWeakHashMap;
    }
}
