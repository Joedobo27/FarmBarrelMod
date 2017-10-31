package com.joedobo27.fbm;

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
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.function.Function;

class HarvestCropAction extends ActionMaster {

    private final TilePos targetTile;
    private final FarmBarrel farmBarrel;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;

    private static WeakHashMap<Action, HarvestCropAction> performers = new WeakHashMap<>();

    HarvestCropAction(Action action, Creature performer, @NotNull Item activeTool, @NotNull Integer usedSkill,
                      int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                      ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TilePos targetTile,
                      FarmBarrel farmBarrel) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
        this.targetTile = targetTile;
        this.farmBarrel = farmBarrel;
        performers.put(action, this);
    }

    @Nullable static HarvestCropAction getHarvestCropAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    boolean hasAFailureCondition() {
        boolean standardChecks =  failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        ItemTemplate harvestTemplate;
        try {
            harvestTemplate = Crops.getHarvestTemplateFromCropId(TileUtilities.getFarmTileCropId(this.targetTile));
        } catch (CropsException e) {
            FarmBarrelMod.logger.warning(e.getMessage());
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "Something went wrong, sorry.");
            return true;
        }
        if (harvestTemplate == null) {
            FarmBarrelMod.logger.warning("harvestTemplate is null");
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "Something went wrong, sorry.");
            return true;
        }
        boolean barrelContentMismatch = this.farmBarrel.getContainedItemTemplateId() != -1 &&
                harvestTemplate.getTemplateId() != this.farmBarrel.getContainedItemTemplateId();
        if (barrelContentMismatch) {
            getPerformer().getCommunicator().sendNormalServerMessage("" +
                    "The seed barrel won't hold both "+farmBarrel.getCropName()+" and "+harvestTemplate.getName()+".");
            return true;
        }
        return false;
    }

    double doSkillCheckAndGetPower (float counter) throws CropsException {
        if (this.usedSkill == null)
            return 1.0d;
        int cropId = TileUtilities.getFarmTileCropId(this.targetTile);
        double difficulty = Crops.getDifficultyFromCropId(cropId);
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    int getYield() {
        ConfigureOptions.CropYieldOptions yieldOptions = ConfigureOptions.getInstance().getCropYieldScaling();
        LinearScalingFunction baseYieldFunction = LinearScalingFunction.make(yieldOptions.getMinimumSkill(),
                yieldOptions.getMaximumSkill(), yieldOptions.getMinimumBaseYield(), yieldOptions.getMaximumBaseYield());
        double modifiedSkill = this.performer.getSkills().getSkillOrLearn(SkillList.FARMING).
                getKnowledge(this.activeTool, 0);
        double baseYield = baseYieldFunction.doFunctionOfX(modifiedSkill);


        LinearScalingFunction bonusYieldFunction = LinearScalingFunction.make(yieldOptions.getMinimumFarmChance(),
                yieldOptions.getMaximumFarmChance(), yieldOptions.getMinimumBonusYield(),
                yieldOptions.getMaximumBonusYield());
        // In resourceMesh the farmChance value is cumulatively stored using mask 0B0000 0111 1111 1111 or
        //      0x7FF which is 2047.
        double farmedChance = TileUtilities.getFarmTileCumulativeChance(this.targetTile);
        int farmedCount = TileUtilities.getFarmTileTendCount(this.targetTile);
        int toolRarity;
        if (this.activeTool == null)
            toolRarity = 0;
        else
            toolRarity = this.activeTool.getRarity();
        double harvestChance = (this.action.getRarity() * 110) + (toolRarity * 50) +
                (Math.min(5, farmedCount) * 50);
        farmedChance += harvestChance;
        double bonusYield = bonusYieldFunction.doFunctionOfX(farmedChance);

        return (int)Math.round(baseYield + bonusYield);
    }

    void alterTileState(byte tileTypeId, byte tileData) {
        Server.surfaceMesh.setTile(this.targetTile.x, this.targetTile.y,
                Tiles.encode(TileUtilities.getSurfaceHeight(this.targetTile), tileTypeId, tileData));
        Server.modifyFlagsByTileType(this.targetTile.x, this.targetTile.y, tileTypeId);
        this.performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(this.targetTile.x, this.targetTile.y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(this.targetTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.targetTile.x, this.targetTile.y);
    }

    void updateMeshResourceData() {
        if (this.usedSkill == null || this.activeTool == null)
            return;
        int sowResult = (int)Math.min(2047,
                100.0 - this.performer.getSkills().getSkillOrLearn(this.usedSkill).getKnowledge() +
                        this.activeTool.getQualityLevel() + (this.activeTool.getRarity() * 20) +
                        (action.getRarity() * 50));
        int resource = TileUtilities.encodeResourceFarmTileData(0, sowResult);
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

    Integer getUsedSkill() {
        return this.usedSkill;
    }

    void doActionEndMessages(int harvestYield) {
        final int itemTemplateId = this.farmBarrel.getContainedItemTemplateId();
        ItemTemplate template = Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                .filter(itemTemplate -> itemTemplate.getTemplateId() == itemTemplateId)
                .findFirst()
                .orElse(null);
        if (template != null) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You finish harvesting and get %d of %s.",
                    harvestYield, template.getName()));
        }
    }
}
