package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.items.*;

import java.util.Arrays;
import java.util.Objects;

public enum Crops {
    BARLEY(0, 28, 28, 20, 300, 5, 5),
    WHEAT(1, 29, 29, 30, 300, 6, 6),
    RYE(2, 30, 30, 10, 300, 3, 3),
    OAT(3, 31, 31, 15, 300, 4, 4),
    CORN(4, 32, 32, 40, 100, 22, 22),
    PUMPKIN(5, 34, 33, 15, 100, 22, 22),
    POTATO(6, 35, 35, 4, 500, 22, 22),
    COTTON(7, 145, 144, 7, 100, 22, 17),
    WEMP(8, 317, 316, 10, 100, 22, 22),
    GARLIC(9, 356, 356, 70, 50, 22, 22),
    ONION(10, 355, 355, 60, 250, 22, 22),
    REED(11, 744, 743, 20, 100, 22, 22),
    RICE(12, 746, 746, 80, 100, 22, 22),
    STRAWBERRIES(13, 750, 362, 60, 100, 22, 22),
    CARROTS(14, 1145, 1133, 25, 50, 22, 22),
    CABBAGE(15, 1146, 1134, 35, 50, 22, 22),
    TOMATOS(16, 1147, 1135, 45, 50, 22, 22),
    SUGAR_BEET(17, 1148, 1136, 85, 50, 22, 22),
    LETTUCE(18, 1149, 1137, 55, 50, 22, 22),
    PEAS(19, 1150, 1138, 65, 100, 22, 22),
    CUCUMBER(20, 1248, 1247, 15, 50, 22, 22),
    EMPTY(21, -1, -1, 0, 0, -1, -1);
    /*
    BASIL(21, basil, basil, 10, 50, 22, 22),
    BELLADONNA(22, belladonna, belladonna, 10, 50, 22, 22),
    LOVAGE(23, lovage, lovage, 10, 50, 22, 22),
    NETTLES(24, nettles, nettles, 10, 50, 22, 22),
    OREGANO(25, oregano, oregano, 10, 50, 22, 22),
    PARSLEY(26, parsley, parsley, 10, 50, 22, 22),
    ROSEMARY(27, rosemary, rosemary, 10, 50, 22, 22),
    SAGE(28, sage, sage, 10, 50, 22, 22),
    SASSAFRAS(29, sassafras, sassafras, 10, 50, 22, 22),
    THYME(30, thyme, thyme, 10, 50, 22, 22),
    FENNEL(31, fennelSeeds, fennel, 10, 50, 22, 22),
    MINT(32, mint, mint, 10, 100, 22, 22),
    CUMIN(33, cumin, cumin, 10, 100, 22, 22),
    GINGER(34, ginger, ginger, 10, 100, 22, 22),
    NUTMEG(35, nutmeg, nutmeg, 10, 50, 22, 22),
    PAPRIKA(36, paprikaSeeds, paprika, 10, 50, 22, 22),
    TURMERIC(37, turmericSeeds, turmeric, 10, 50, 22, 22),
    BLUEBERRY(38, blueberry, blueberry, 60, 100, 22, 22),
    LINGONBERRY(39, lingonberry, lingonberry, 20, 100, 22, 22),
    RASPBERRY(40, raspberries, raspberries, 20, 100, 22, 22),
    COCOABEAN(41, cocoaBean, cocoaBean, 85, 200, 22, 22),
    WOAD(42, woad, woad, 90, 50, 22, 22),
    BLACK_MUSHROOM(43, mushroomBlack, mushroomBlack, 90, 400, 22, 22),
    BLUE_MUSHROOM(44, mushroomBlue, mushroomBlue, 70, 800, 22, 22),
    BROWN_MUSHROOM(45, mushroomBrown, mushroomBrown, 60, 600, 22, 22),
    GREEN_MUSHROOM(46, mushroomGreen, mushroomGreen, 80, 400, 22, 22),
    RED_MUSHROOM(47, mushroomRed, mushroomRed, 20, 100, 22, 22),
    YELLOW_MUSHROOM(48, mushroomYellow, mushroomYellow, 70, 200, 22, 22),
    */

    private final int id;
    private final int seedTemplateId;
    private final int productTemplateId;
    private final double difficulty;
    private final int seedGrams;
    private final byte SeedMaterial;
    private final byte productMaterial; // see MaterialUtilities.getMaterialString(). The same numbers are used in material arg of ItemTemplateCreator.
    private static int lastUsableEntry = 21;
    private static Crops instance;

    Crops(int id, int seedTemplateId, int productTemplateId, double difficulty, int seedGrams, int SeedMaterial, int productMaterial) {

        this.id = id;
        this.seedTemplateId = seedTemplateId;
        this.productTemplateId = productTemplateId;
        this.difficulty = difficulty;
        this.seedGrams = seedGrams;
        this.SeedMaterial = (byte) SeedMaterial;
        this.productMaterial = (byte) productMaterial;
    }

    public static int getSeedGramsFromCropId(int cropId) {
        Crops crops = Arrays.stream(values())
                .filter(crops1 -> crops1.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        if (crops.id >= lastUsableEntry)
            crops = Crops.EMPTY;
        return crops.seedGrams;
    }

    public int getId() {
        return this.id;
    }

    public int getSeedTemplateId() {
        return this.seedTemplateId;
    }

    public int getProductTemplateId() {return this.productTemplateId;}

    public static Crops getCrop(int cropId){
        return Arrays.stream(values())
                .filter(crops1 -> crops1.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
    }

    public static String getCropNameFromCropId(int cropId, boolean isSeed) throws NoSuchTemplateException {
        Crops crops = Arrays.stream(values())
                .filter(crops1 -> crops1.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        if (crops.id >= lastUsableEntry)
            crops = Crops.EMPTY;
        ItemTemplate cropTemplate = null;
        ItemTemplate seedTemplate = null;

        cropTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getProductTemplateIdFromCropId(crops.id));
        seedTemplate = ItemTemplateFactory.getInstance().getTemplate(Crops.getSeedTemplateIdFromCropId(crops.id));

        if (isSeed) {
            return seedTemplate.getName();
        }
        return cropTemplate.getName();
    }

    static ItemTemplate getSeedTemplateFromProductTemplate(ItemTemplate productTemplate) throws NoSuchTemplateException, CropsException {
        if (productTemplate.getTemplateId() == ItemList.bulkItem)
            throw new CropsException("arg requires getRealTemplate() preparation.");
        Crops crop = Arrays.stream(values())
                .filter(crops -> crops.productTemplateId == productTemplate.getTemplateId())
                .findFirst()
                .orElseThrow(() -> new CropsException("No matching productTemplateId found."));
        if (crop.id >= lastUsableEntry)
            throw new CropsException("Crops.id found isn't supported.");
        return ItemTemplateFactory.getInstance().getTemplate(crop.getSeedTemplateId());
    }

    static int getSeedTemplateIdFromCropId(int cropId) {
        Crops crop = Arrays.stream(values())
                .filter(crops -> crops.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        if (crop.id >= lastUsableEntry)
            crop = Crops.EMPTY;
        return crop.seedTemplateId;
    }

    static ItemTemplate getSeedTemplateFromCropId(int cropId) throws NoSuchTemplateException, CropsException {
        Crops crop = Arrays.stream(values())
                .filter(crops -> crops.id == cropId)
                .findFirst()
                .orElseThrow(() -> new CropsException("cropId isn't valid so no ItemTemplate can be found."));
        if (crop.id >= lastUsableEntry)
            throw new CropsException("Crops.id found isn't supported.");
        return ItemTemplateFactory.getInstance().getTemplate(crop.getSeedTemplateId());
    }

    static int getProductTemplateIdFromCropId(int cropId) {
        Crops crop = Arrays.stream(values())
                .filter(crops -> crops.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        if (crop.id >= lastUsableEntry)
            crop = Crops.EMPTY;
        return crop.productTemplateId;
    }

    static ItemTemplate getProductTemplateFromCropId(int cropId) throws NoSuchTemplateException, CropsException {
        Crops crop = Arrays.stream(values())
                .filter(crops -> crops.id == cropId)
                .findFirst()
                .orElseThrow(() -> new CropsException("cropId isn't valid so no ItemTemplate can be found."));
        if (crop.id >= lastUsableEntry)
            throw new CropsException("Crops.id found isn't supported.");
        return ItemTemplateFactory.getInstance().getTemplate(crop.getProductTemplateId());
    }

    static int getCropIdFromSeedTemplateId(int aSeedTemplateId) {
        int id = Arrays.stream(values())
                .filter(crops -> Objects.equals(crops.seedTemplateId, aSeedTemplateId))
                .mapToInt(crops -> crops.id)
                .findFirst()
                .orElse(lastUsableEntry);
        if (id > lastUsableEntry)
            return lastUsableEntry;
        return id;
    }

    static int getCropIdFromProductTemplateId(int aProductTemplateId) {
        int id = Arrays.stream(values())
                .filter(crops -> Objects.equals(crops.productTemplateId, aProductTemplateId))
                .mapToInt(crops -> crops.id)
                .findFirst()
                .orElse(lastUsableEntry);
        if (id > lastUsableEntry)
            return lastUsableEntry;
        return id;
    }

    static double getCropDifficultyFromCropId(int cropId) {
        Crops crops = Arrays.stream(values())
                .filter(crop -> Objects.equals(cropId, crop.id))
                .findFirst()
                .orElse(Crops.EMPTY);
        if (crops == EMPTY || crops.id >= lastUsableEntry)
            return -1;
        return crops.difficulty;
    }

    public static int getLastUsableEntry() {
        return lastUsableEntry;
    }

    public byte getSeedMaterial() {
        return SeedMaterial;
    }

    // new Crops(0, "barley", 28, 28, "handfuls", 20.0),
    // new Crops(1, "wheat", 29, 29, "handfuls", 30.0),
    // new Crops(2, "rye", 30, 30, "handfuls", 10.0),
    // new Crops(3, "oat", 31, 31, "handfuls", 15.0),
    // new Crops(4, "corn", 32, 32, "stalks", 40.0),
    // new Crops(5, "pumpkin", 34, 33, "", 15.0),
    // new Crops(6, "potato", 35, 35, "", 4.0),
    // new Crops(7, "cotton", 145, 144, "bales", 7.0),
    // new Crops(8, "wemp", 317, 316, "bales", 10.0),
    // new Crops(9, "garlic", 356, 356, "bunch", 70.0),
    // new Crops(10, "onion", 355, 355, "bunch", 60.0),
    // new Crops(11, "reed", 744, 743, "bales", 20.0),
    // new Crops(12, "rice", 746, 746, "handfuls", 80.0),
    // new Crops(13, "strawberries", 750, 362, "handfuls", 60.0),
    // new Crops(14, "carrots", 1145, 1133, "handfuls", 25.0),
    // new Crops(15, "cabbage", 1146, 1134, "", 35.0),
    // new Crops(16, "tomatos", 1147, 1135, "handfuls", 45.0),
    // new Crops(17, "sugar beet", 1148, 1136, "", 85.0),
    // new Crops(18, "lettuce", 1149, 1137, "", 55.0),
    // new Crops(19, "peas", 1150, 1138, "handfuls", 65.0),
    // new Crops(20, "cucumber", 1248, 1247, "", 15.0) };


}
