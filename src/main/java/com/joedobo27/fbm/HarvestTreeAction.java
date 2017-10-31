package com.joedobo27.fbm;

import com.joedobo27.libs.LinearScalingFunction;
import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.BushData;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.server.Players;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

public class HarvestTreeAction extends ActionMaster {

    private final TilePos targetTile;
    private final FarmBarrel farmBarrel;
    private final ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions;
    private final TreeData.TreeType treeType;
    private final BushData.BushType bushType;

    private static WeakHashMap<Action, HarvestTreeAction> performers = new WeakHashMap<>();

    protected HarvestTreeAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                                int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                                TilePos targetTile, FarmBarrel farmBarrel,
                                ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TreeData.TreeType treeType,
                                BushData.BushType bushType) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.targetTile = targetTile;
        this.farmBarrel = farmBarrel;
        this.failureTestFunctions = failureTestFunctions;
        this.treeType = treeType;
        this.bushType = bushType;
        performers.put(action, this);
    }

    @Nullable static HarvestTreeAction getHarvestTreeAction(Action action) {
        if (!performers.containsKey(action))
            return null;
        return performers.get(action);
    }

    boolean hasAFailureCondition() {
        boolean standardChecks = failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
        if (standardChecks)
            return true;
        ItemTemplate harvestTemplate = null;
        int bushHarvestTemplate = TileUtilities.getBushHarvestItemTemplateId(this.targetTile);
        int treeHarvestTemplate = TileUtilities.getTreeHarvestItemTemplateId(this.targetTile);
        try {
            if (bushHarvestTemplate != -10)
                harvestTemplate = ItemTemplateFactory.getInstance().getTemplate(bushHarvestTemplate);
            else if (treeHarvestTemplate != -10)
                harvestTemplate = ItemTemplateFactory.getInstance().getTemplate(treeHarvestTemplate);
            if (harvestTemplate == null) {
                FarmBarrelMod.logger.warning("Didn't find a harvest item template for a tree/bush.");
                return true;
            }
        } catch (NoSuchTemplateException e) {
            FarmBarrelMod.logger.warning(String.format("Found no item template matching either Ids (bush,tree): (%d,%d).",
                    bushHarvestTemplate, treeHarvestTemplate));
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

    void removeTreesFruit() {
        TileUtilities.setTreeHasFruit(this.getTargetTile(), false);
        Players.getInstance().sendChangedTile(this.getTargetTile().x, this.getTargetTile().y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(this.getTargetTile(), this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.getTargetTile().x, this.getTargetTile().y);
    }

    double doSkillCheckAndGetPower (float counter) {
        if (this.usedSkill == null)
            return 1.0d;
        if (treeType == null && bushType == null)
            return 1.0d;
        double difficulty;
        if (treeType != null)
            difficulty = treeType.getDifficulty();
        else
            difficulty = bushType.getDifficulty();
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    int getYield() {
        ConfigureOptions.TreeYieldOptions yieldOptions = ConfigureOptions.getInstance().getTreeYieldScaling();
        LinearScalingFunction yieldFunction = LinearScalingFunction.make(yieldOptions.getMinimumSkill(),
                yieldOptions.getMaximumSkill(), yieldOptions.getMinimumYield(), yieldOptions.getMaximumYield());
        double modifiedSkill = this.performer.getSkills().getSkillOrLearn(SkillList.FARMING).
                getKnowledge(this.activeTool, 0);
        double baseYield = yieldFunction.doFunctionOfX(modifiedSkill);

        int toolRarity;
        if (this.activeTool == null)
            toolRarity = 0;
        else
            toolRarity = this.activeTool.getRarity();
        baseYield += toolRarity;
        baseYield += (this.action.getRarity() * 100);
        baseYield = Math.min(yieldOptions.getMaximumYield(), baseYield);

        return (int)Math.round(baseYield);
    }

    void depositInBarrel() {
        double forestryKnowledge = performer.getSkills().getSkillOrLearn(this.getUsedSkill()).getKnowledge();
        if (this.activeTool.getSpellEffects() != null && this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            forestryKnowledge *= (1 + RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(),
                    RuneUtilities.ModifierEffect.ENCH_RESGATHERED));
        }
        int harvestItemTemplateId = this.isTree() ?
                TileUtilities.getTreeHarvestItemTemplateId(this.getTargetTile()) :
                TileUtilities.getBushHarvestItemTemplateId(this.getTargetTile());
        this.getFarmBarrel().increaseContainedCount(this.getYield(), forestryKnowledge,
                harvestItemTemplateId);
    }

    boolean isTree() {
        return this.treeType != null;
    }

    public TreeData.TreeType getTreeType() {
        return treeType;
    }

    boolean isBush() {
        return this.bushType != null;
    }

    public BushData.BushType getBushType() {
        return bushType;
    }

    @Override
    public Item getActiveTool() {
        return activeTool;
    }

    @Override
    public Item getTargetItem() {
        return null;
    }

    @Override
    public TilePos getTargetTile() {
        return targetTile;
    }

    FarmBarrel getFarmBarrel() {
        return farmBarrel;
    }

    Integer getUsedSkill() {
        return this.usedSkill;
    }
}
