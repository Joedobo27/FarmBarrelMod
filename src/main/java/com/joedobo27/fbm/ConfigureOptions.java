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
    private ActionOptions harvestAction;
    private ActionOptions sowAction;
    private int configureBarrelQuestionId;
    private HarvestYieldOptions sowYieldScaling;
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

    class HarvestYieldOptions {
        private final double minimumBaseYield;
        private final double maximumBaseYield;
        private final double minimumSkill;
        private final double maximumSkill;
        private final double minimumBonusYield;
        private final double maximumBonusYield;
        private final double minimumFarmChance;
        private final double maximumFarmChance;

        HarvestYieldOptions(double minimumBaseYield, double maximumBaseYield, double minimumSkill, double maximumSkill,
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

        public double getMinimumBaseYield() {
            return minimumBaseYield;
        }

        public double getMaximumBaseYield() {
            return maximumBaseYield;
        }

        public double getMinimumSkill() {
            return minimumSkill;
        }

        public double getMaximumSkill() {
            return maximumSkill;
        }

        public double getMinimumBonusYield() {
            return minimumBonusYield;
        }

        public double getMaximumBonusYield() {
            return maximumBonusYield;
        }

        public double getMinimumFarmChance() {
            return minimumFarmChance;
        }

        public double getMaximumFarmChance() {
            return maximumFarmChance;
        }
    }

    synchronized static void setOptions(Properties properties) {
        instance.sowRadius = doPropertiesToArray(properties.getProperty("sowRadius"));
        instance.skillUnlockPoints = doPropertiesToArray(properties.getProperty("skillUnlockPoints"));
        instance.emptyBarrelAction = doPropertiesToActionOptions(properties.getProperty("emptyBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.fillBarrelAction = doPropertiesToActionOptions(properties.getProperty("fillBarrelAction",
                DEFAULT_ACTION_OPTION));
        instance.harvestAction = doPropertiesToActionOptions(properties.getProperty("harvestAction",
                DEFAULT_ACTION_OPTION));
        instance.sowAction = doPropertiesToActionOptions(properties.getProperty("sowAction",
                DEFAULT_ACTION_OPTION));
        instance.configureBarrelQuestionId = Integer.parseInt(properties.getProperty("configureBarrelQuestionId"));
        instance.sowYieldScaling = doPropertiesToYieldOptions(properties.getProperty("sowYieldScaling"));
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
        instance.harvestAction = doPropertiesToActionOptions(properties.getProperty("harvestAction",
                DEFAULT_ACTION_OPTION));
        instance.sowAction = doPropertiesToActionOptions(properties.getProperty("sowAction",
                DEFAULT_ACTION_OPTION));
        instance.sowYieldScaling = doPropertiesToYieldOptions(properties.getProperty("sowYieldScaling"));
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

    private static HarvestYieldOptions doPropertiesToYieldOptions(String values) {
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
        return instance.new HarvestYieldOptions(minimumBaseYield, maximumBaseYield, minimumSkill, maximumSkill, minimumBonusYield,
                maximumBonusYield, minimumFarmChance, maximumFarmChance);
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

    ActionOptions getHarvestAction() {
        return harvestAction;
    }

    ActionOptions getSowAction() {
        return sowAction;
    }

    int getConfigureBarrelQuestionId() {
        return configureBarrelQuestionId;
    }

    HarvestYieldOptions getSowYieldScaling() {
        return sowYieldScaling;
    }

    int getMaxSowingSlope() {
        return maxSowingSlope;
    }
}
