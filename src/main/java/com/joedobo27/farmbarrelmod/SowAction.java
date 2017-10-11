package com.joedobo27.farmbarrelmod;

import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Point;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;

import static com.wurmonline.server.skills.SkillList.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SowAction {
    private Creature performer;
    private Item barrel;
    private int seedTemplateId;
    private int seedCount;
    private LinkedList<Point> points;  // H is an encodedTile int.
    private Action action;
    private double unitSowTimeInterval;
    private int totalTime;
    private int lastWholeUnitTime;

    SowAction(Point centerTile, Action action, boolean surfaced, Creature performer, Item barrel, int encodedTile) {
        this.action = action;
        this.performer = performer;
        this.barrel = barrel;
        this.lastWholeUnitTime = 0;

        seedTemplateId = Crops.getSeedTemplateIdFromCropId(FarmBarrelMod.decodeContainedCropId(barrel));
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
        unitSowTimeInterval = FarmBarrelMod.getBaseUnitActionTime(barrel, performer, action, FARMING, BODY_CONTROL);
        totalTime = (int) Math.ceil(unitSowTimeInterval * points.size());
    }

    boolean unitTimeJustTicked(float counter){
        int unitTime = (int)(Math.floor((counter * 100) / (this.unitSowTimeInterval * 10)));
        if (unitTime != this.lastWholeUnitTime){
            this.lastWholeUnitTime = unitTime;
            return true;
        }
        return false;
    }

    private int getSeedCount(){
        if (seedTemplateId == -1)
            return 0;
        int seedGrams = Crops.getSeedGramsFromCropId(FarmBarrelMod.decodeContainedCropId(barrel));
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

    private boolean seedIsSeed() {
        return seedTemplateId != -1;
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

        int minimumHeight;
        int maxHeight;
        switch (seedTemplateId){
            case ItemList.reedSeed:
                minimumHeight = -4;
                maxHeight = -1;
                break;
            case ItemList.rice:
                minimumHeight = -4;
                maxHeight = -1;
                break;
            default:
                minimumHeight = 1;
                maxHeight = Short.MAX_VALUE;
        }
        TilePos targetTilePos = TilePos.fromXY(posX, posY);
        boolean allCornersInHeightRange = Arrays.stream(new TilePos[]{targetTilePos, targetTilePos.East(), targetTilePos.SouthEast(), targetTilePos.South()})
                .filter(tilePos -> TileUtilities.getSurfaceHeight(tilePos) >= minimumHeight)
                .filter(tilePos -> TileUtilities.getSurfaceHeight(tilePos) <= maxHeight)
                .count() == 4;

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
        return  allCornersInHeightRange && isSlopeMinimal && seedsCompatibleWithTile;
    }

    @SuppressWarnings("ConstantConditions")
    void consumeSeed() {
        int weight = barrel.getWeightGrams() - Crops.getSeedGramsFromCropId(FarmBarrelMod.decodeContainedCropId(barrel));
        if (weight == 1000) {
            FarmBarrelMod.encodeIsSeed(barrel, false);
            FarmBarrelMod.encodeContainedCropId(barrel, Crops.EMPTY.getId());
            FarmBarrelMod.encodeContainedQuality(barrel, 0);
            barrel.updateName();
        }
        barrel.setWeight(weight, false);
    }

    int getTotalTime() {
        return totalTime;
    }

    double getUnitSowTimeInterval() {
        return unitSowTimeInterval;
    }

    int getSeedTemplateId() {
        return seedTemplateId;
    }

    Creature getPerformer() {
        return performer;
    }

    Item getBarrel() {
        return barrel;
    }

    LinkedList<Point> getPoints() {
        return points;
    }
}
