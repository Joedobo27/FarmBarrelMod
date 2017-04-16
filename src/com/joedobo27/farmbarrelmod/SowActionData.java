package com.joedobo27.farmbarrelmod;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.RuneUtilities;

import static com.wurmonline.server.skills.SkillList.*;

import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SowActionData {
    private Creature performer;
    private Item barrel;
    private int seedTemplateId;
    private int seedCount;
    private LinkedList<Point> points;  // H is an encodedTile int.
    private Action action;
    private int unitSowTimeInterval;
    private int totalTime;
    private double modifiedKnowledge;
    private int lastWholeUnitTime;
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    SowActionData(Point centerTile, Action action, boolean surfaced, Creature performer, Item barrel, int encodedTile) {
        this.action = action;
        this.performer = performer;
        this.barrel = barrel;
        this.lastWholeUnitTime = 0;

        seedTemplateId = Crops.getSeedTemplateIdFromCropId(FarmBarrelMod.decodeContainedSeed(barrel));
        points = new LinkedList<>();
        IntStream.range(centerTile.getX() - getSowBarrelRadius(), centerTile.getX() + getSowBarrelRadius() + 1)
                .forEach( posX ->
                        IntStream.range(centerTile.getY() - getSowBarrelRadius(), centerTile.getY() + getSowBarrelRadius() + 1)
                                .forEach(posY -> points.add(new Point(posX, posY, encodedTile)))
                );
        points = points.stream()
                .filter(value -> isTileCompatibleWithSeed(value.getX(), value.getY(), surfaced))
                .collect(Collectors.toCollection(LinkedList::new));
        seedCount = getSeedCount();
        totalTime = getInitialActionTime();
    }

    boolean unitTimeJustTicked(float counter){
        int unitTime = (int) Math.floor(counter / this.unitSowTimeInterval);
        if (unitTime == this.lastWholeUnitTime){
            this.lastWholeUnitTime = unitTime;
            return true;
        }
        return false;
    }

    private int getSeedCount(){
        if (seedTemplateId == -1)
            return 0;
        int seedGrams = Crops.getSeedGramsFromCropId(FarmBarrelMod.decodeContainedSeed(barrel));
        if (seedGrams == 0)
            return 0;
        if (barrel.getWeightGrams() - 1000 == 0)
            return 0;
        return (barrel.getWeightGrams() - 1000) / seedGrams;
    }

    int getSowDimension() {
        return (getSowBarrelRadius() * 2) + 1;
    }

    int getSowBarrelRadius() {
        return FarmBarrelMod.decodeSowRadius(barrel);
    }

    boolean enoughSeedForSowing() {
        return seedCount >= getSowTileCount();
    }

    boolean seedIsSeed() {
        return seedTemplateId != -1;
    }

    /**
     * It shouldn't be necessary to have a fantastic, 104woa, speed rune, 99ql, 99 skill in order to get the fastest time.
     * Aim for just skill as getting close to shortest time and the other boosts help at lower levels but aren't needed to have
     * the best at end game.
     *
     * @return int primitive, tens-of-a-second action time
     */
    private int getInitialActionTime(){
        double MINIMUM_TIME = FarmBarrelMod.minimumUnitActionTime;
        int MAX_BONUS = 10;
        double MAX_WOA_EFFECT = 0.20;
        double TOOL_RARITY_EFFECT = 0.1;
        double ACTION_RARITY_EFFECT = 0.33;
        double time;
        modifiedKnowledge = Math.max(100.0d, performer.getSkills().getSkillOrLearn(FARMING).getKnowledge(barrel,
                Math.max(MAX_BONUS, performer.getSkills().getSkillOrLearn(BODY_CONTROL).getKnowledge() / 5)));
        time = Math.max(MINIMUM_TIME, (100.0 - modifiedKnowledge) * 1.3f / Servers.localServer.getActionTimer());

        // woa
        if (barrel != null && barrel.getSpellSpeedBonus() > 0.0f)
            time = Math.max(MINIMUM_TIME, time * (1 - (MAX_WOA_EFFECT * barrel.getSpellSpeedBonus() / 100.0)));
        //rare barrel item, 10% speed reduction per rarity level.
        if (barrel.getRarity() > 0)
            time = Math.max(MINIMUM_TIME, time * (1 - (barrel.getRarity() * TOOL_RARITY_EFFECT)));
        //rare sowing action, 33% speed reduction per rarity level.
        if (action.getRarity() > 0)
            time = Math.max(MINIMUM_TIME, time * (1 - (action.getRarity() * ACTION_RARITY_EFFECT)));
        // rune effects
        if (barrel.getSpellEffects() != null && barrel.getSpellEffects().getRuneEffect() != -10L)
            time = Math.max(MINIMUM_TIME, time * (1 - RuneUtilities.getModifier(barrel.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_USESPEED)));
        return (int) time;
    }

    int getSowTileCount() {
        return points.size();
    }

    /**
     * LIFO pop.
     *
     * @return WU Point object.
     */
    Point popSowTile() {
        return points.removeFirst();
    }

    private boolean isTileCompatibleWithSeed(int posX, int posY, boolean surfaced) {
        if (!seedIsSeed())
            return false;
        MeshIO sowMesh;
        if (surfaced) {
            sowMesh = Server.surfaceMesh;
        } else {
            sowMesh = Server.caveMesh;
        }

        boolean isTileUnderWater = Terraforming.isCornerUnderWater(posX, posY, surfaced);
        boolean isSeedAquatic;
        switch (seedTemplateId){
            case ItemList.reedSeed:
                isSeedAquatic = true;
                break;
            case ItemList.rice:
                isSeedAquatic = true;
                break;
            default:
                isSeedAquatic = false;
        }

        boolean isSlopeMinimal = Terraforming.isFlat(posX, posY, surfaced, 4);

        byte seedNeededTile;
        switch (seedTemplateId) {
            case ItemList.mushroomBlack:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            case ItemList.mushroomBlue:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            case ItemList.mushroomBrown:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            case ItemList.mushroomGreen:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            case ItemList.mushroomRed:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            case ItemList.mushroomYellow:
                seedNeededTile = Tiles.Tile.TILE_CAVE.id;
                break;
            default:
                seedNeededTile = Tiles.Tile.TILE_DIRT.id;
        }
        boolean seedsCompatibleWithTile = seedNeededTile == Tiles.decodeType(sowMesh.getTile(posX, posY));
        return isTileUnderWater == isSeedAquatic && isSlopeMinimal && seedsCompatibleWithTile;
    }

    @SuppressWarnings("ConstantConditions")
    void consumeSeed() {
        int weight = barrel.getWeightGrams() - Crops.getSeedGramsFromCropId(FarmBarrelMod.decodeContainedSeed(barrel));
        if (weight == 1000) {
            FarmBarrelMod.encodeContainedSeed(barrel, -1);
            barrel.updateName();
        }
        barrel.setWeight(weight, false);
    }

    int getTotalTime() {
        return totalTime;
    }

    int getUnitSowTimeInterval() {
        return unitSowTimeInterval;
    }

    int getSeedTemplateId() {
        return seedTemplateId;
    }

    Creature getPerformer() {
        return performer;
    }

    public Action getAction() {
        return action;
    }

    public Item getBarrel() {
        return barrel;
    }
}
