package com.joedobo27.farmbarrelmod;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

class ConfigureOptions {

    private final int configureBarrelQuestionId;
    private final ConfigureActionOptions emptyBarrelActionOptions;
    private final ConfigureActionOptions fillBarrelActionOptions;
    private final ConfigureActionOptions harvestActionOptions;
    private final ArrayList<Double> skillUnlockPoints;
    private final ConfigureActionOptions sowActionOptions;
    private final ArrayList<Integer> sowRadius;
    private final ArrayList<Double>  sowYieldScaling;
    private final int maxSowingSlope;

    private static ConfigureOptions instance = null;
    private static final String DEFAULT_ACTION_OPTION = "" +
            "{\"minSkill\":10 ,\"maxSkill\":95 , \"longestTime\":100 , \"shortestTime\":10 , \"minimumStamina\":6000}";

    private ConfigureOptions(int configureBarrelQuestionId, ConfigureActionOptions emptyBarrelActionOptions,
                             ConfigureActionOptions fillBarrelActionOptions, ConfigureActionOptions harvestActionOptions,
                             ArrayList<Double> skillUnlockPoints, ConfigureActionOptions sowActionOptions,
                             ArrayList<Integer> sowRadius, ArrayList<Double> sowYieldScaling, int maxSowingSlope) {
        this.configureBarrelQuestionId = configureBarrelQuestionId;
        this.emptyBarrelActionOptions = emptyBarrelActionOptions;
        this.fillBarrelActionOptions = fillBarrelActionOptions;
        this.harvestActionOptions = harvestActionOptions;
        this.skillUnlockPoints = skillUnlockPoints;
        this.sowActionOptions = sowActionOptions;
        this.sowRadius = sowRadius;
        this.sowYieldScaling = sowYieldScaling;
        this.maxSowingSlope = maxSowingSlope;
        instance = this;
    }

    synchronized static void setOptions(@Nullable Properties properties) {
        if (instance == null) {
            if (properties == null) {
                properties = getProperties();
            }
            if (properties == null)
                throw new RuntimeException("properties can't be null here.");

            instance = new ConfigureOptions(
                    Integer.valueOf(properties.getProperty("configureBarrelQuestionId")),
                    doPropertiesToConfigureAction(properties.getProperty("emptyBarrelAction", DEFAULT_ACTION_OPTION)),
                    doPropertiesToConfigureAction(properties.getProperty("fillBarrelAction", DEFAULT_ACTION_OPTION)),
                    doPropertiesToConfigureAction(properties.getProperty("harvestAction", DEFAULT_ACTION_OPTION)),
                    formatSkillUnlock(properties.getProperty("skillUnlockPoints")),
                    doPropertiesToConfigureAction(properties.getProperty("sowAction", DEFAULT_ACTION_OPTION)),
                    formatSowRadius(properties.getProperty("sowRadius")),
                    formatSowYieldScaling(properties.getProperty("sowYieldScaling")),
                    Integer.valueOf(properties.getProperty("maxSowingSlope")));
        }
    }

    synchronized static void resetOptions() {
        instance = null;
        Properties properties = getProperties();
        if (properties == null)
            throw new RuntimeException("properties can't be null here.");
        instance = new ConfigureOptions(
                Integer.valueOf(properties.getProperty("configureBarrelQuestionId")),
                doPropertiesToConfigureAction(properties.getProperty("emptyBarrelAction", DEFAULT_ACTION_OPTION)),
                doPropertiesToConfigureAction(properties.getProperty("fillBarrelAction", DEFAULT_ACTION_OPTION)),
                doPropertiesToConfigureAction(properties.getProperty("harvestAction", DEFAULT_ACTION_OPTION)),
                formatSkillUnlock(properties.getProperty("skillUnlockPoints")),
                doPropertiesToConfigureAction(properties.getProperty("sowAction", DEFAULT_ACTION_OPTION)),
                formatSowRadius(properties.getProperty("sowRadius")),
                formatSowYieldScaling(properties.getProperty("sowYieldScaling")),
                Integer.valueOf(properties.getProperty("maxSowingSlope")));
    }

    private static ArrayList<Double> formatSowYieldScaling(String values) {
        return Arrays.stream(values.replaceAll("\\s", "").split(","))
                .mapToDouble(Double::parseDouble)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ArrayList<Integer> formatSowRadius(String values) {
        FarmBarrelMod.logger.info("sowRadius: " + values);
        return Arrays.stream(values.replaceAll("\\s", "").split(","))
                .mapToInt(Integer::parseInt)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ArrayList<Double> formatSkillUnlock(String values) {
        final double EQUIVALENT_100_SKILL = 99.99999615;
        FarmBarrelMod.logger.info("sowRadius: " + values);

        values = values.replaceAll("100", Double.toString(EQUIVALENT_100_SKILL));
        return Arrays.stream(values.replaceAll("\\s", "").split(","))
                .mapToDouble(Integer::parseInt)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ConfigureActionOptions doPropertiesToConfigureAction(String values) {

        ArrayList<Integer> integers = Arrays.stream(values.replaceAll("\\s", "").split(","))
                .mapToInt(Integer::parseInt)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));

        int minSkill = integers.get(0);
        int maxSkill = integers.get(1);
        int longestTime = integers.get(2);
        int shortestTime = integers.get(3);
        int minimumStamina = integers.get(4);
        return new ConfigureActionOptions(minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
    }

    private static Properties getProperties() {
        try {
            File configureFile = new File("mods/MightyMattockMod.properties");
            FileInputStream configureStream = new FileInputStream(configureFile);
            Properties configureProperties = new Properties();
            configureProperties.load(configureStream);
            return configureProperties;
        }catch (IOException e) {
            FarmBarrelMod.logger.warning(e.getMessage());
            return null;
        }
    }

    public int getConfigureBarrelQuestionId() {
        return configureBarrelQuestionId;
    }

    static ConfigureOptions getInstance() {
        return instance;
    }

    public ConfigureActionOptions getEmptyBarrelActionOptions() {
        return emptyBarrelActionOptions;
    }

    public ConfigureActionOptions getFillBarrelActionOptions() {
        return fillBarrelActionOptions;
    }

    public ConfigureActionOptions getHarvestActionOptions() {
        return harvestActionOptions;
    }

    public ArrayList<Double> getSkillUnlockPoints() {
        return skillUnlockPoints;
    }

    public ConfigureActionOptions getSowActionOptions() {
        return sowActionOptions;
    }

    public ArrayList<Integer> getSowRadius() {
        return sowRadius;
    }

    public ArrayList<Double> getSowYieldScaling() {
        return sowYieldScaling;
    }

    public int getMaxSowingSlope() {
        return maxSowingSlope;
    }
}
