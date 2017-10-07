package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

@SuppressWarnings("WeakerAccess")
abstract class FarmBarrelAction extends ActionMaster {

    private final ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions;

    protected FarmBarrelAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                               int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                               ArrayList<Function<FarmBarrelAction, Boolean>> failureTestFunctions) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
    }

    abstract boolean hasAFailureCondition();

    abstract TilePos getTargetTile();

    abstract Item getTargetItem();

    abstract Item getActiveTool();

    abstract FarmBarrel getFarmBarrel();

    protected ArrayList<Function<FarmBarrelAction, Boolean>> getFailureTestFunctions() {
        return failureTestFunctions;
    }

    protected int getMaxRadiusFromFarmSkill(Creature performer) {
        double farmingLevel = performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge();
        ArrayList<Integer> sowRadius = ConfigureOptions.getInstance().getSowRadius();
        ArrayList<Double> skillUnlockPoints = ConfigureOptions.getInstance().getSkillUnlockPoints();

        int maxIndex = skillUnlockPoints.size() - 1;
        for (int i = 0; i <= maxIndex; i++) {
            if (i == maxIndex && farmingLevel >= skillUnlockPoints.get(i)) {
                return sowRadius.get(i);
            } else if (farmingLevel >= skillUnlockPoints.get(i) && farmingLevel < skillUnlockPoints.get(i + 1)) {
                return sowRadius.get(i);
            }
        }
        return sowRadius.get(0);
    }
}
