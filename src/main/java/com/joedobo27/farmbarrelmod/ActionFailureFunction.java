package com.joedobo27.farmbarrelmod;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings("unused")
class ActionFailureFunction {

    private final String name;
    private final Function<FarmBarrelAction, Boolean> function;

    static final int FAILURE_FUNCTION_EMPTY = 0;
    static final int FAILURE_FUNCTION_INSUFFICIENT_STAMINA = 1;
    static final int FAILURE_FUNCTION_GOD_PROTECTED = 2;
    static final int FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION = 3;
    static final int FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION = 4;
    static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE = 5;
    static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT = 6;
    static final int FAILURE_FUNCTION_FOOD_IN_NON_FOOD_CONTAINER = 7;
    static final int FAILURE_FUNCTION_NON_FOOD_IN_FOOD_CONTAINER = 8;
    static final int FAILURE_FUNCTION_TARGET_NOT_FARM_ITEM = 9;
    static final int FAILURE_FUNCTION_BARREL_CONTENT_MISMATCH = 10;
    static final int FAILURE_FUNCTION_CROPS_NOT_RIPE = 11;
    static final int FAILURE_FUNCTION_TOON_HOLDING_MAX_WEIGHT = 12;
    static final int FAILURE_FUNCTION_INSUFFICIENT_SKILL_FOR_SOW_AREA = 13;

    private static HashMap<Integer, ActionFailureFunction> failureFunctions = new HashMap<>();
    static {
        initializeFailureFunctions();
    }

    @SuppressWarnings("WeakerAccess")
    public ActionFailureFunction(String name, Function<FarmBarrelAction, Boolean> function) {
        this.name = name;
        this.function = function;
    }

    private static void initializeFailureFunctions() {
        failureFunctions.put(0, new ActionFailureFunction("FAILURE_FUNCTION_EMPTY", null));
        failureFunctions.put(1, new ActionFailureFunction("FAILURE_FUNCTION_INSUFFICIENT_STAMINA",
                (FarmBarrelAction farmBarrelAction) -> {
                    if (farmBarrelAction.getPerformer().getStatus().getStamina() < farmBarrelAction.getMinimumStamina()) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage(
                                "You don't have enough stamina to " + farmBarrelAction.getAction().getActionString() + ".");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(2, new ActionFailureFunction("FAILURE_FUNCTION_GOD_PROTECTED",
                (FarmBarrelAction farmBarrelAction) -> {
                    if (Zones.isTileProtected(farmBarrelAction.getTargetTile().x, farmBarrelAction.getTargetTile().y)) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "This tile is protected by the gods. You can not " +
                                farmBarrelAction.getAction().getActionString() + " here.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(3, new ActionFailureFunction("FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION",
                (FarmBarrelAction farmBarrelAction) -> {
                    Village village = Zones.getVillage(farmBarrelAction.getTargetTile().x, farmBarrelAction.getTargetTile().y,
                            farmBarrelAction.getPerformer().isOnSurface());
                    if (village != null && !village.isActionAllowed((short) farmBarrelAction.getActionId(),
                            farmBarrelAction.getPerformer(), false,
                            TileUtilities.getSurfaceEncodedValue(farmBarrelAction.getTargetTile()), 0) &&
                            !Zones.isOnPvPServer(farmBarrelAction.getTargetTile().x, farmBarrelAction.getTargetTile().y)) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "This tile is controlled by a deed which hasn't given you permission to change it.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(4, new ActionFailureFunction("FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION",
                (FarmBarrelAction farmBarrelAction) -> {
                    Village village = Zones.getVillage(farmBarrelAction.getTargetTile().x, farmBarrelAction.getTargetTile().y,
                            farmBarrelAction.getPerformer().isOnSurface());
                    if (village != null &&
                            !village.isActionAllowed((short) farmBarrelAction.getActionId(), farmBarrelAction.getPerformer(),
                                    false, TileUtilities.getSurfaceEncodedValue(farmBarrelAction.getTargetTile()),
                                    0) &&
                            !village.isEnemy(farmBarrelAction.getPerformer()) && farmBarrelAction.getPerformer().isLegal()) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "That would be illegal here. You can check the settlement token for the local laws.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(5, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE",
                (FarmBarrelAction farmBarrelAction) -> {
                    VolaTile volaTile = Zones.getTileOrNull(farmBarrelAction.getTargetTile(),
                            farmBarrelAction.getPerformer().isOnSurface());
                    if (volaTile == null)
                        return false;
                    Structure structure = volaTile.getStructure();
                    if (structure != null && volaTile.getStructure().isTypeHouse()) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+farmBarrelAction.getAction().getActionString()+
                                " action can't modify a tile occupied by a house.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(6, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT",
                (FarmBarrelAction farmBarrelAction) -> {
                    VolaTile volaTile = Zones.getTileOrNull(farmBarrelAction.getTargetTile(),
                            farmBarrelAction.getPerformer().isOnSurface());
                    if (volaTile == null)
                        return false;
                    BridgePart[] bridgeParts = volaTile.getBridgeParts();
                    if (bridgeParts == null || bridgeParts.length == 0)
                        return false;
                    if (Arrays.stream(bridgeParts)
                            .anyMatch(bridgePart -> bridgePart.getType().isSupportType())) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+farmBarrelAction.getAction().getActionString()+
                                " action can't modify or tile occupied by a bridge support.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(7, new ActionFailureFunction("FAILURE_FUNCTION_FOOD_IN_NON_FOOD_CONTAINER",
                (FarmBarrelAction farmBarrelAction) -> {
                    if (FarmBarrel.containsFoodItem(farmBarrelAction.getActiveTool()) &&
                            farmBarrelAction.getTargetItem().getTemplateId() == ItemList.bulkContainer) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "Food items go in the food storage bin, not a bulk storage bin.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(8, new ActionFailureFunction("FAILURE_FUNCTION_NON_FOOD_IN_FOOD_CONTAINER",
                (FarmBarrelAction farmBarrelAction) -> {
                    if (FarmBarrel.containsNonFoodItem(farmBarrelAction.getActiveTool()) &&
                            farmBarrelAction.getTargetItem().getTemplateId() == ItemList.hopper) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "Non-food items go in the bulk storage bin, not a food storage bin.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(9, new ActionFailureFunction("FAILURE_FUNCTION_TARGET_NOT_FARM_ITEM",
                (FarmBarrelAction farmBarrelAction) -> {
                    ItemTemplate targetTemplate = farmBarrelAction.getTargetItem().getRealTemplate();
                    switch (targetTemplate.getTemplateId()) {
                        case ItemList.barley:
                        case ItemList.rye:
                        case ItemList.oat:
                        case ItemList.corn:
                        case ItemList.pumpkin:
                        case ItemList.pumpkinSeed:
                        case ItemList.potato:
                        case ItemList.cotton:
                        case ItemList.cottonSeed:
                        case ItemList.wemp:
                        case ItemList.wempSeed:
                        case ItemList.garlic:
                        case ItemList.onion:
                        case ItemList.reed:
                        case ItemList.reedSeed:
                        case ItemList.rice:
                        case ItemList.strawberries:
                        case ItemList.strawberrySeed:
                        case ItemList.carrot:
                        case ItemList.carrotSeeds:
                        case ItemList.cabbage:
                        case ItemList.cabbageSeeds:
                        case ItemList.tomato:
                        case ItemList.tomatoSeeds:
                        case ItemList.sugarBeet:
                        case ItemList.sugarBeetSeeds:
                        case ItemList.lettuce:
                        case ItemList.lettuceSeeds:
                        case ItemList.pea:
                        case ItemList.peaPod:
                        case ItemList.cucumber:
                        case ItemList.cucumberSeeds:
                            return true;
                        default:
                            return false;
                    }
                }));
        failureFunctions.put(10, new ActionFailureFunction("FAILURE_FUNCTION_BARREL_CONTENT_MISMATCH",
                (FarmBarrelAction farmBarrelAction) -> {
                    ItemTemplate targetTemplate;
                    if (farmBarrelAction.getTargetTile() != null) {
                        targetTemplate = TileUtilities.getItemTemplateFromHarvestTile(farmBarrelAction.getTargetTile());
                    }
                    else if (farmBarrelAction.getTargetItem().getTemplateId() == ItemList.bulkItem)
                        targetTemplate = farmBarrelAction.getTargetItem().getRealTemplate();
                    else
                        targetTemplate = farmBarrelAction.getTargetItem().getTemplate();
                    FarmBarrel farmBarrel = farmBarrelAction.getFarmBarrel();
                    String containedName = farmBarrel.getCropName();
                    if (targetTemplate.getTemplateId() != farmBarrel.getContainedItemTemplateId()) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "The seed barrel won't hold both "+containedName+" and "+targetTemplate.getName()+".");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(11, new ActionFailureFunction("FAILURE_FUNCTION_CROPS_NOT_RIPE",
                (FarmBarrelAction farmBarrelAction) -> {
                    if (TileUtilities.getFarmTileAge(farmBarrelAction.getTargetTile()) < 5 ||
                            TileUtilities.getFarmTileAge(farmBarrelAction.getTargetTile()) > 6) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "The crops aren't ripe.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(12, new ActionFailureFunction("FAILURE_FUNCTION_TOON_HOLDING_MAX_WEIGHT",
                (farmBarrelAction -> {
                    if (farmBarrelAction.getPerformer().getCarryingCapacityLeft() < 1) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "You can't carry anything else.");
                        return true;
                    }
                    return false;
                })));
        failureFunctions.put(13, new ActionFailureFunction("FAILURE_FUNCTION_INSUFFICIENT_SKILL_FOR_SOW_AREA",
                farmBarrelAction -> {
                    int sowBarrelRadius = farmBarrelAction.getFarmBarrel().getSowRadius();
                    int sowDimension = (sowBarrelRadius * 2) + 1;
                    String sowArea = String.format("%d by %d area",sowDimension, sowDimension);
                    if (farmBarrelAction.getMaxRadiusFromFarmSkill(farmBarrelAction.getPerformer()) < sowBarrelRadius) {
                        farmBarrelAction.getPerformer().getCommunicator().sendNormalServerMessage(
                                "You don't have enough farming skill to sow a "+sowArea+".");
                        return true;
                    }
                    return false;
                }));
    }


    static Function<FarmBarrelAction, Boolean> getFunction(int functionId) {
        if (failureFunctions.containsKey(functionId))
            return failureFunctions.get(functionId).function;
        else
            return failureFunctions.get(0).function;
    }

    static Function<FarmBarrelAction, Boolean> getFunction(String functionName) {
        Function<FarmBarrelAction, Boolean> toReturn = failureFunctions.values().stream()
                .filter(integerActionFailureFunctionEntry -> Objects.equals(
                        integerActionFailureFunctionEntry.name, functionName))
                .map(integerActionFailureFunctionEntry -> integerActionFailureFunctionEntry.function)
                .findFirst()
                .orElseGet(null);
        if (toReturn == null)
            toReturn = failureFunctions.get(0).function;
        return toReturn;
    }
}
