package com.joedobo27.fbm;


import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;

class ConfigureOptions {

    private ArrayList<Integer> sowRadius;
    private ArrayList<Integer> skillUnlockPoints;
    private ActionOptions emptyBarrelAction;
    private ActionOptions fillBarrelAction;
    private ActionOptions harvestCropAction;
    private ActionOptions sowAction;
    private ActionOptions harvestTreeAction;
    private int configureBarrelQuestionId;
    private CropYieldOptions cropYieldScaling;
    private TreeYieldOptions treeYieldScaling;
    private int maxSowingSlope;

    private static final ConfigureOptions instance;
    private static final String DEFAULT_ACTION_OPTION = "" +
            "{\"minSkill\":10 ,\"maxSkill\":95 , \"longestTime\":100 , \"shortestTime\":10 , \"minimumStamina\":6000}";

    static {
        instance = new ConfigureOptions();
    }

    class ActionOptions {
        private final int minSkill;
        private final int maxSkill;
        private final int longestTime;
        private final int shortestTime;
        private final int minimumStamina;

        ActionOptions(int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina) {
            this.minSkill = minSkill;
            this.maxSkill = maxSkill;
            this.longestTime = longestTime;
            this.shortestTime = shortestTime;
            this.minimumStamina = minimumStamina;
        }

        int getMinSkill() {
            return minSkill;
        }

        int getMaxSkill() {
            return maxSkill;
        }

        int getLongestTime() {
            return longestTime;
        }

        int getShortestTime() {
            return shortestTime;
        }

        int getMinimumStamina() {
            return minimumStamina;
        }
    }

    class CropYieldOptions {
        private final double minimumBaseYield;
        private final double maximumBaseYield;
        private final double minimumSkill;
        private final double maximumSkill;
        private final double minimumBonusYield;
        private final double maximumBonusYield;
        private final double minimumFarmChance;
        private final double maximumFarmChance;

        CropYieldOptions(double minimumBaseYield, double maximumBaseYield, double minimumSkill, double maximumSkill,
                         double minimumBonusYield, double maximumBonusYield, double minimumFarmChance,
                         double maximumFarmChance) {
            this.minimumBaseYield = minimumBaseYield;
            this.maximumBaseYield = maximumBaseYield;
            this.minimumSkill = minimumSkill;
            this.maximumSkill = maximumSkill;
            this.minimumBonusYield = minimumBonusYield;
            this.maximumBonusYield = maximumBonusYield;
            this.minimumFarmChance = minimumFarmChance;
            this.maximumFarmChance = maximumFarmChance;
        }

        double getMinimumBaseYield() {
            return minimumBaseYield;
        }

        double getMaximumBaseYield() {
            return maximumBaseYield;
        }

        double getMinimumSkill() {
            return minimumSkill;
        }

        double getMaximumSkill() {
            return maximumSkill;
        }

        double getMinimumBonusYield() {
            return minimumBonusYield;
        }

        double getMaximumBonusYield() {
            return maximumBonusYield;
        }

        double getMinimumFarmChance() {
            return minimumFarmChance;
        }

        double getMaximumFarmChance() {
            return maximumFarmChance;
        }
    }

    class TreeYieldOptions {
        private final double minimumYield;
        private final double maximumYield;
        private final double minimumSkill;
        private final double maximumSkill;

        TreeYieldOptions(double minimumYield, double maximumYield, double minimumSkill, double maximumSkill) {
            this.minimumYield = minimumYield;
            this.maximumYield = maximumYield;
            this.minimumSkill = minimumSkill;
            this.maximumSkill = maximumSkill;
        }

        public double getMinimumYield() {
            return minimumYield;
        }

        public double getMaximumYield() {
            return maximumYield;
        }

        public double getMinimumSkill() {
            return minimumSkill;
        }

        public double getMaximumSkill() {
            return maximumSkill;
        }
    }

    synchronized static void setOptions(Properties properties) {
        instance.sowRadius = doPropertiesToArray(properties.getProperty("sowRadius"));
        instance.skillUnlockPoints = doPropertiesToArray(properties.getProperty("skillUnlockPoints"));
        instance.emptyBarrelAction = doPropertiesToActionOptions(properties.getProperty("emptyBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.fillBarrelAction = doPropertiesToActionOptions(properties.getProperty("fillBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.harvestCropAction = doPropertiesToActionOptions(properties.getProperty("harvestCropAction",
                DEFAULT_ACTION_OPTION));
        instance.sowAction = doPropertiesToActionOptions(properties.getProperty("sowAction",
                DEFAULT_ACTION_OPTION));
        instance.harvestTreeAction = doPropertiesToActionOptions(properties.getProperty("harvestTreeAction",
                DEFAULT_ACTION_OPTION));
        instance.configureBarrelQuestionId = Integer.parseInt(properties.getProperty("configureBarrelQuestionId"));
        instance.cropYieldScaling = doPropertiesToCropYieldOptions(properties.getProperty("cropYieldScaling"));
        instance.treeYieldScaling = doPropertiesToTreeYieldOptions(properties.getProperty("treeYieldScaling"));
        instance.maxSowingSlope = Integer.parseInt(properties.getProperty("maxSowingSlope"));
    }

    synchronized static void resetOptions() {
        Properties properties = getProperties();
        if (properties == null)
            throw new RuntimeException("properties can't be null here.");
        instance.emptyBarrelAction = doPropertiesToActionOptions(properties.getProperty("emptyBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.fillBarrelAction = doPropertiesToActionOptions(properties.getProperty("fillBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.harvestCropAction = doPropertiesToActionOptions(properties.getProperty("harvestCropAction",
                DEFAULT_ACTION_OPTION));
        instance.sowAction = doPropertiesToActionOptions(properties.getProperty("sowAction",
                DEFAULT_ACTION_OPTION));
        instance.cropYieldScaling = doPropertiesToCropYieldOptions(properties.getProperty("cropYieldScaling"));
        instance.configureBarrelQuestionId = Integer.parseInt(properties.getProperty("configureBarrelQuestionId"));
        instance.maxSowingSlope = Integer.parseInt(properties.getProperty("maxSowingSlope"));
    }

    private static ArrayList<Integer> doPropertiesToArray(String values) {
        String[] strings = values.split(",");
        ArrayList<Integer> integers = new ArrayList<>();
        IntStream.range(0, strings.length)
                .forEach(value -> integers.add(Integer.parseInt(strings[value])));
        return integers;
    }

    private static ActionOptions doPropertiesToActionOptions(String values) {
        Reader reader = new StringReader(values);
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject jsonObject = jsonReader.readObject();
        int minSkill = jsonObject.getInt("minSkill", 10);
        int maxSkill = jsonObject.getInt("maxSkill", 95);
        int longestTime = jsonObject.getInt("longestTime", 100);
        int shortestTime = jsonObject.getInt("shortestTime", 10);
        int minimumStamina = jsonObject.getInt("minimumStamina", 6000);
        return instance.new ActionOptions(minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
    }

    private static CropYieldOptions doPropertiesToCropYieldOptions(String values) {
        Reader reader = new StringReader(values);
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject jsonObject = jsonReader.readObject();
        double minimumBaseYield = jsonObject.getJsonNumber("minimumBaseYield").doubleValue();
        double maximumBaseYield = jsonObject.getJsonNumber("maximumBaseYield").doubleValue();
        double minimumSkill = jsonObject.getJsonNumber("minimumSkill").doubleValue();
        double maximumSkill = jsonObject.getJsonNumber("maximumSkill").doubleValue();
        double minimumBonusYield = jsonObject.getJsonNumber("minimumBonusYield").doubleValue();
        double maximumBonusYield = jsonObject.getJsonNumber("maximumBonusYield").doubleValue();
        double minimumFarmChance = jsonObject.getJsonNumber("minimumFarmChance").doubleValue();
        double maximumFarmChance = jsonObject.getJsonNumber("maximumFarmChance").doubleValue();
        return instance.new CropYieldOptions(minimumBaseYield, maximumBaseYield, minimumSkill, maximumSkill, minimumBonusYield,
                maximumBonusYield, minimumFarmChance, maximumFarmChance);
    }

    private static TreeYieldOptions doPropertiesToTreeYieldOptions(String values) {
        Reader reader = new StringReader(values);
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject jsonObject = jsonReader.readObject();
        double minimumBaseYield = jsonObject.getJsonNumber("minimumYield").doubleValue();
        double maximumBaseYield = jsonObject.getJsonNumber("maximumYield").doubleValue();
        double minimumSkill = jsonObject.getJsonNumber("minimumSkill").doubleValue();
        double maximumSkill = jsonObject.getJsonNumber("maximumSkill").doubleValue();
        return instance.new TreeYieldOptions(minimumBaseYield, maximumBaseYield, minimumSkill, maximumSkill);
    }

    private static Properties getProperties() {
        try {
            File configureFile = new File("mods/FarmBarrelMod.properties");
            FileInputStream configureStream = new FileInputStream(configureFile);
            Properties configureProperties = new Properties();
            configureProperties.load(configureStream);
            return configureProperties;
        }catch (IOException e) {
            FarmBarrelMod.logger.warning(e.getMessage());
            return null;
        }
    }

    synchronized void setSowRadiusWithDefaults() {
        sowRadius = new ArrayList<>(Arrays.asList(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15));
    }

    synchronized void setSkillUnlockPointsWithDefauts() {
        skillUnlockPoints = new ArrayList<>(Arrays.asList(0,10,20,30,40,50,60,70,80,90,92,94,96,98,100));
    }

    static ConfigureOptions getInstance() {
        return instance;
    }

    ArrayList<Integer> getSowRadius() {
        return sowRadius;
    }

    ArrayList<Integer> getSkillUnlockPoints() {
        return skillUnlockPoints;
    }

    ActionOptions getEmptyBarrelAction() {
        return emptyBarrelAction;
    }

    ActionOptions getFillBarrelAction() {
        return fillBarrelAction;
    }

    ActionOptions getHarvestCropAction() {
        return harvestCropAction;
    }

    ActionOptions getSowAction() {
        return sowAction;
    }

    public ActionOptions getHarvestTreeAction() {
        return harvestTreeAction;
    }

    int getConfigureBarrelQuestionId() {
        return configureBarrelQuestionId;
    }

    CropYieldOptions getCropYieldScaling() {
        return cropYieldScaling;
    }

    public TreeYieldOptions getTreeYieldScaling() {
        return treeYieldScaling;
    }

    int getMaxSowingSlope() {
        return maxSowingSlope;
    }
}
