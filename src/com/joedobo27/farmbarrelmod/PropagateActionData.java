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
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class PropagateActionData {
    private Creature performer;
    private Item barrel;
    private int seedTemplateId;
    private int seedCount;
    private LinkedList<Point> points;  // H is an encodedTile int.
    private Action action;
    private int unitSowTimeInterval;
    private int totalTime;
    private double modifiedKnowledge;
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    PropagateActionData(Point centerTile, Action action, boolean surfaced, Creature performer, Item barrel, int encodedTile) {
        this.action = action;
        this.performer = performer;
        this.barrel = barrel;

        seedTemplateId = Crops.getSeedTemplateIdFromCropId(FarmBarrelMod.decodeContainedSeed(barrel));
        points = new LinkedList<>();
        IntStream.range(centerTile.getX() - getSowBarrelRadius(), centerTile.getX() + getSowBarrelRadius() + 1)
                .forEach( posX ->
                        IntStream.range(centerTile.getY() - getSowBarrelRadius(), centerTile.getY() + getSowBarrelRadius() + 1)
                                .forEach(posY -> points.add(new Point(posX, posY, encodedTile)))
                );
        logger.info(points.toString());
        points = points.stream()
                .filter(value -> isTileCompatibleWithSeed(value.getX(), value.getY(), surfaced))
                .collect(Collectors.toCollection(LinkedList::new));
        logger.info(points.toString());
        seedCount = getSeedCount();
        totalTime = getInitialActionTime();
    }

    private int getSeedCount(){
        if (seedTemplateId == 0)
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
        return seedTemplateId != 0;
    }

    private int getInitialActionTime(){
        Skill farmingSkill = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        double bonus = Math.max(10, performer.getSkills().getSkillOrLearn(SkillList.BODY_CONTROL).getKnowledge() / 5);
        double knowledge = farmingSkill.getKnowledge(barrel, bonus);
        modifiedKnowledge = knowledge;
        final float multiplier = 1.3f / Servers.localServer.getActionTimer();
        double time = (100.0 - knowledge) * multiplier;

        // woa
        if (barrel != null && barrel.getSpellSpeedBonus() > 0.0f) {
            time = 30.0 + time * (1.0 - 0.2 * barrel.getSpellSpeedBonus() / 100.0);
        } else {
            time += 30.0;
        }
        //rare barrel item, 10% speed reduction per rarity level.
        int barrelRarity = barrel.getRarity();
        double rarityBarrelBonus = 1 - (barrelRarity == 0 ? 1 : barrelRarity * 0.1);
        time *= rarityBarrelBonus == 0 ? 1 : rarityBarrelBonus;
        //rare sowing action, 33% speed reduction per rarity level.
        int actionRarity = action.getRarity();
        double rarityActionBonus = 1 - (actionRarity == 0 ? 1 : actionRarity * 0.3);
        time *= rarityActionBonus == 0 ? 1 : rarityActionBonus;

        // In order to ease use of modulo and trigger on action.justTickedSecond() round away the one's digit in time.
        MathContext mathContext = null;
        if (time < 100) {
            mathContext = new MathContext(1);
        }
        else if (time >= 100 && time < 1000) {
            mathContext = new MathContext(2);
        }
        else if (time >= 1000 && time < 10000) {
            mathContext = new MathContext(3);
        }
        BigDecimal bigDecimal = new BigDecimal(time);
        bigDecimal = bigDecimal.round(mathContext);
        int roundedTime = bigDecimal.intValue();
        unitSowTimeInterval = roundedTime;

        roundedTime *= getSowTileCount();
        return Math.max(10, roundedTime);
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
        logger.info(Boolean.toString(isSeedAquatic) + ", " + Boolean.toString(isSlopeMinimal) + ", "
                + Boolean.toString(seedsCompatibleWithTile));
        return isTileUnderWater == isSeedAquatic && isSlopeMinimal && seedsCompatibleWithTile;
    }

    @SuppressWarnings("ConstantConditions")
    void consumeSeed() {
        barrel.setWeight(barrel.getWeightGrams() - Crops.getSeedGramsFromCropId(seedTemplateId), false);
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
