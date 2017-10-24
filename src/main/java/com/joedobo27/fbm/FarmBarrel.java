package com.joedobo27.fbm;


import com.wurmonline.server.items.*;
import com.wurmonline.shared.util.MaterialUtilities;
import org.jetbrains.annotations.NotNull;

import javax.json.*;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class FarmBarrel {

    private boolean autoResow;
    private Item wuItem;
    private ScheduledExecutorService jsonWritingExecutor;
    private int containedCount;
    private int containedItemTemplateId;
    private double containedQuality;
    private int sowRadius;
    private int supplyQuantity;

    private static final int NONE_CROP_ID = -1;
    private static HashMap<Long, FarmBarrel> farmBarrels = new HashMap<>();

    private static final String DEFAULT_BARREL_SETTING =
    "{\"autoResow\":false, \"containedCount\":0, \"containedItemTemplateId\":-1, \"containedQuality\":0, \"sowRadius\":0, \"supplyQuantity\":0}";

    private FarmBarrel(boolean autoResow, Item wuItem, int containedCount, int containedItemTemplateId, double containedQuality, int sowRadius,
                       int supplyQuantity) {
        this.autoResow = autoResow;
        this.wuItem = wuItem;
        this.containedCount = containedCount;
        this.containedItemTemplateId = containedItemTemplateId;
        this.containedQuality = containedQuality;
        this.sowRadius = sowRadius;
        this.supplyQuantity = supplyQuantity;
    }

    class RunnableJsonUpdater implements Runnable {
        private final Item farmBarrel;
        private final ScheduledExecutorService jsonWritingExecutor;

        RunnableJsonUpdater(Item farmBarrel, ScheduledExecutorService jsonWritingExecutor) {
            this.farmBarrel = farmBarrel;
            this.jsonWritingExecutor = jsonWritingExecutor;
        }

        @Override
        public void run() {
            doFarmBarrelToInscriptionJson(this.farmBarrel);
            jsonWritingExecutor.shutdown();
            try {
                jsonWritingExecutor.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                FarmBarrelMod.logger.warning(e.getMessage());
            }
        }
    }

    synchronized void configureUpdate(boolean autoResow, int sowRadius, int supplyQuantity) {
        this.autoResow = autoResow;
        this.sowRadius = sowRadius;
        this.supplyQuantity = supplyQuantity;
        this.checkOrQueueScheduledJsonBackup();
    }

    synchronized private void checkOrQueueScheduledJsonBackup() {
        if (jsonWritingExecutor.isShutdown()) {
            jsonWritingExecutor = Executors.newSingleThreadScheduledExecutor();
            RunnableJsonUpdater runnableJsonUpdater = new RunnableJsonUpdater(this.wuItem, this.jsonWritingExecutor);
            jsonWritingExecutor.schedule(runnableJsonUpdater, 10, TimeUnit.MINUTES);
        }
    }

    synchronized void reduceContainedCount(int count) {
        int originalId = this.containedItemTemplateId;
        this.containedCount = Math.max(0, this.containedCount - count);
        if (this.containedCount == 0) {
            this.containedQuality = 0.0d;
            this.containedItemTemplateId = NONE_CROP_ID;
        }

        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(this.containedItemTemplateId);
        int barrelGrams = this.wuItem.getTemplate().getWeightGrams();
        if (this.containedItemTemplateId != NONE_CROP_ID && itemTemplate != null) {
            barrelGrams += (itemTemplate.getWeightGrams() * this.containedCount);
        }
        this.wuItem.setWeight(barrelGrams, false);
        if (this.containedItemTemplateId != originalId)
            updateBarrelName();
    }

    synchronized void increaseContainedCount(int count, double quality, int itemTemplateId) {
        int originalId = this.containedItemTemplateId;
        double qlWeightedAverage = ((this.containedQuality * this.containedCount) + (count * quality)) /
                (this.containedCount + count);
        this.containedItemTemplateId = itemTemplateId;

        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(this.containedItemTemplateId);
        int barrelGramms = this.wuItem.getTemplate().getWeightGrams();
        if (this.containedItemTemplateId != NONE_CROP_ID && itemTemplate != null) {
            barrelGramms += (itemTemplate.getWeightGrams() * this.getContainedCount());
            this.containedQuality = qlWeightedAverage;
        }
        this.wuItem.setWeight(barrelGramms, false);
        if (this.containedItemTemplateId != originalId)
            updateBarrelName();
    }


    private void updateBarrelName() {
        String s = MaterialUtilities.getRarityString(this.wuItem.getRarity()) + this.wuItem.getTemplate().getName() +
                "[" + getCropName() + "]";
        this.wuItem.setName(s);
    }

    String getCropName() {
        ItemTemplate itemTemplate = ItemTemplateFactory.getInstance().getTemplateOrNull(this.containedItemTemplateId);
        if (itemTemplate == null)
            return "";
        return itemTemplate.getName();
    }


    private static void doInscriptionJsonToPOJO(@NotNull Item item) {
        InscriptionData inscriptionData = item.getInscription();
        String jsonString;
        if (inscriptionData == null) {
            jsonString = DEFAULT_BARREL_SETTING;
        }
        else
            jsonString = inscriptionData.getInscription();
        Reader reader = new StringReader(jsonString);
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject jsonValues = jsonReader.readObject();
        boolean autoResow = jsonValues.getBoolean("autoResow", false);
        int containedCount = jsonValues.getInt("containedCount", 0);
        int containedCropId = jsonValues.getInt("containedItemTemplateId", -1);
        double containedQuality = jsonValues.getJsonNumber("containedQuality").doubleValue();
        int sowRadius = jsonValues.getInt("sowRadius", 0);
        int supplyQuantity = jsonValues.getInt("supplyQuantity", 0);

        synchronized (FarmBarrel.class) {
            FarmBarrel farmBarrel = new FarmBarrel(autoResow, item, containedCount, containedCropId, containedQuality, sowRadius,
                    supplyQuantity);
            farmBarrels.put(item.getWurmId(), farmBarrel);
        }
        jsonReader.close();
    }

    private static void doFarmBarrelToInscriptionJson(Item item) {
        FarmBarrel farmBarrel = getOrMakeFarmBarrel(item);
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (farmBarrel == null || builder == null)
            return;
        builder.add("autoResow", farmBarrel.isAutoResow());
        builder.add("containedCount", farmBarrel.getContainedCount());
        builder.add("containedItemTemplateId", farmBarrel.getContainedItemTemplateId());
        builder.add("containedQuality", farmBarrel.getContainedQuality());
        builder.add("sowRadius", farmBarrel.getSowRadius());
        builder.add("supplyQuantity", farmBarrel.getSupplyQuantity());

        Writer writer = new StringWriter();
        JsonWriter jsonWriter = Json.createWriter(writer);
        jsonWriter.writeObject(builder.build());
        String s = jsonWriter.toString();
        jsonWriter.close();
        synchronized (Objects.requireNonNull(item)) {
            item.setInscription(s, "");
        }
    }

    static FarmBarrel getOrMakeFarmBarrel(@NotNull Item item) {
        if (farmBarrels.containsKey(item.getWurmId()))
            return farmBarrels.getOrDefault(item.getWurmId(), null);
        else {
            doInscriptionJsonToPOJO(item);
            return farmBarrels.getOrDefault(item.getWurmId(), null);
        }
    }

    boolean isAutoResow() {
        return autoResow;
    }

    int getContainedCount() {
        return containedCount;
    }

    int getContainedItemTemplateId() {
        return containedItemTemplateId;
    }

    double getContainedQuality() {
        return containedQuality;
    }

    int getSowRadius() {
        return sowRadius;
    }

    int getSupplyQuantity() {
        return supplyQuantity;
    }

    public Item getWuItem() {
        return wuItem;
    }
}
