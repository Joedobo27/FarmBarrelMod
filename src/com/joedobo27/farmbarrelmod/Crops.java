package com.joedobo27.farmbarrelmod;

import java.util.Arrays;
import java.util.Objects;

/**
 *
 */
public enum Crops {
    EMPTY(0, 0, 0, 0),
    BARLEY(1, 28, 28, 20),
    WHEAT(2, 29, 29, 30),
    RYE(3, 30, 30, 10),
    OAT(4, 31, 31, 15),
    CORN(5, 32, 32, 40),
    PUMPKIN(6, 34, 33, 15),
    POTATO(7, 35, 35, 4),
    COTTON(8, 145, 144, 7),
    WEMP(9, 317, 316, 10),
    GARLIC(10, 356, 356, 70),
    ONION(11, 355, 355, 60),
    REED(12, 744, 743, 20),
    RICE(13, 746, 746, 80),
    STRAWBERRIES(14, 750, 362, 60),
    CARROTS(15, 1145, 1133, 25),
    CABBAGE(16, 1146, 1134, 35),
    TOMATOS(17, 1147, 1135, 45),
    SUGAR_BEET(18, 1148, 1136, 85),
    LETTUCE(19, 1149, 1137, 55),
    PEAS(20, 1150, 1138, 65),
    CUCUMBER(21, 1248, 1247, 15);

    private final int id;
    private final int seedTemplateId;
    private final int productTemplateId;
    private final double difficulty;

    Crops(int id, int seedTemplateId, int productTemplateId, double difficulty) {
        this.id = id;
        this.seedTemplateId = seedTemplateId;
        this.productTemplateId = productTemplateId;
        this.difficulty = difficulty;
    }

    public int getId() {
        return this.id;
    }

    public int getSeedTemplateId() {
        return seedTemplateId;
    }

    public static String getCropNameFromCropId(int cropId) {
        Crops crops = Arrays.stream(values())
                .filter(crops1 -> crops1.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        return crops.name();
    }

    static int getSeedTemplateIdFromCropId(int cropId) {
        Crops crops = Arrays.stream(values())
                .filter(crop -> crop.id == cropId)
                .findFirst()
                .orElse(Crops.EMPTY);
        return crops.seedTemplateId;
    }

    static int getCropIdFromSeedTemplateId(int seedTemplateId) {
        return Arrays.stream(values())
                .filter(crops -> Objects.equals(seedTemplateId, crops.getSeedTemplateId()))
                .mapToInt(Crops::getId)
                .findFirst()
                .orElse(0);
    }

    static boolean templateIdIsSeed(int templateId) {
        return Arrays.stream(values())
                .filter(crops -> crops.seedTemplateId == templateId)
                .count() == 1;
    }

    static int getCropDifficultyFromCropId(int cropId) {
        return Arrays.stream(values())
                .filter(crops -> Objects.equals(cropId, crops.getId()))
                .mapToInt(Crops::getId)
                .findFirst()
                .orElse(0);
    }

    static boolean isCropId(int id) {
        return id < Crops.values().length;
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
