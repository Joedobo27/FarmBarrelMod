package com.joedobo27.fbm;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;

import java.util.Arrays;

import static com.joedobo27.fbm.CropsList.*;

class Crops {

    private final int number;
    private final String cropName;
    private final int seedTemplateId;
    private final String measure;
    private final int productTemplateId;
    private final double difficulty;
    private static final Crops[] cropTypes;
    
    private Crops(final int aNumber, final String aCropName, final int aTemplateId, final int aProductId, final String aMeasure, final double aDifficulty) {
        this.number = aNumber;
        this.cropName = aCropName;
        this.seedTemplateId = aTemplateId;
        this.productTemplateId = aProductId;
        this.measure = aMeasure;
        this.difficulty = aDifficulty;
    }

    static int getCropIdFromTemplateId(int templateId) throws CropsException{
        return Arrays.stream(cropTypes)
                .filter(crops -> crops.productTemplateId == templateId || crops.seedTemplateId == templateId)
                .mapToInt(crops -> crops.number)
                .boxed()
                .findFirst()
                .orElseThrow(new CropsException("No matching templateId for "+templateId+" in Crops."));
    }

    static byte getTileTypeFromTemplateId(int templateId) throws CropsException {
        int cropId = Arrays.stream(cropTypes)
                .filter(crops -> crops.productTemplateId == templateId || crops.seedTemplateId == templateId)
                .mapToInt(crops -> crops.number)
                .findFirst()
                .orElseThrow(new CropsException("No matching templateId for "+templateId+" in Crops."));
        if (cropId < 16) {
            return Tiles.Tile.TILE_FIELD.id;
        }
        return Tiles.Tile.TILE_FIELD2.id;
    }

    static double getDifficultyFromTemplateId(int templateId) throws CropsException{
        return Arrays.stream(cropTypes)
                .filter(crops -> crops.productTemplateId == templateId || crops.seedTemplateId == templateId)
                .mapToDouble(crops -> crops.difficulty)
                .findFirst()
                .orElseThrow(new CropsException("No matching templateId for "+templateId+" in Crops."));
    }

    static byte getTileTypeForCropId(int cropId) {
        if (cropId < 16) {
            return Tiles.Tile.TILE_FIELD.id;
        }
        return Tiles.Tile.TILE_FIELD2.id;
    }

    static boolean isScytheHarvested(int tileX, int tileY) {
        int cropsId = TileUtilities.getFarmTileCropId(TilePos.fromXY(tileX, tileY));
        switch (cropsId) {
            case BARLEY:
            case WHEAT:
            case RYE:
            case OAT:
                return true;
            default:
                return false;
        }
    }

    static ItemTemplate getHarvestTemplateFromCropId(int cropId) throws CropsException {
        if (cropId > cropTypes.length - 1)
            throw new CropsException("The cropId: "+cropId+", does not exist in Crops.");
        return Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                .filter(itemTemplate -> itemTemplate.getTemplateId() == cropTypes[cropId].productTemplateId)
                .findFirst()
                .orElseThrow(new CropsException("No harvest template found matching cropId: "+cropId));
    }

    static ItemTemplate getSeedTemplateFromCropId(int cropId) throws CropsException {
        if (cropId > cropTypes.length - 1)
            throw new CropsException("The cropId: "+cropId+", does not exist in Crops.");
        return Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                .filter(itemTemplate -> itemTemplate.getTemplateId() == cropTypes[cropId].seedTemplateId)
                .findFirst()
                .orElseThrow(new CropsException("No seed template found matching cropId: "+cropId));
    }

    static double getDifficultyFromCropId(int cropId) throws CropsException {
        if (cropId > cropTypes.length - 1)
            throw new CropsException("The cropId: "+cropId+", does not exist in Crops.");
        return cropTypes[cropId].difficulty;
    }

    static String getCropNameFromTemplateId(int templateId) {
        return Arrays.stream(cropTypes)
                .filter(crops -> crops.productTemplateId == templateId || crops.seedTemplateId == templateId)
                .map(crops -> crops.cropName)
                .findFirst()
                .orElseThrow(new CropsException("No matching templateId for "+templateId+" in Crops."));
    }

    public static Crops[] getCropTypes() {
        return cropTypes;
    }

    static {
        cropTypes = new Crops[] {
                new Crops(0, "barley", 28, 28, "handfuls", 20.0),
                new Crops(1, "wheat", 29, 29, "handfuls", 30.0),
                new Crops(2, "rye", 30, 30, "handfuls", 10.0),
                new Crops(3, "oat", 31, 31, "handfuls", 15.0),
                new Crops(4, "corn", 32, 32, "stalks", 40.0),
                new Crops(5, "pumpkin", 34, 33, "", 15.0),
                new Crops(6, "potato", 35, 35, "", 4.0),
                new Crops(7, "cotton", 145, 144, "bales", 7.0),
                new Crops(8, "wemp", 317, 316, "bales", 10.0),
                new Crops(9, "garlic", 356, 356, "bunch", 70.0),
                new Crops(10, "onion", 355, 355, "bunch", 60.0),
                new Crops(11, "reed", 744, 743, "bales", 20.0),
                new Crops(12, "rice", 746, 746, "handfuls", 80.0),
                new Crops(13, "strawberries", 750, 362, "handfuls", 60.0),
                new Crops(14, "carrots", 1145, 1133, "handfuls", 25.0),
                new Crops(15, "cabbage", 1146, 1134, "", 35.0),
                new Crops(16, "tomatoes", 1147, 1135, "handfuls", 45.0),
                new Crops(17, "sugar beet", 1148, 1136, "", 85.0),
                new Crops(18, "lettuce", 1149, 1137, "", 55.0),
                new Crops(19, "peas", 1150, 1138, "handfuls", 65.0),
                new Crops(20, "cucumber", 1248, 1247, "", 15.0) };
    }
}
