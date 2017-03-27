package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.items.Item;

/**
 *
 */
class BarrelDataHandler {

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0xF0000000
     *
     * @param seedBarrel WU Item object
     * @return int value, radius value for sow square.
     */
    static int decodeSowRadius(Item seedBarrel) {
        return (seedBarrel.getData1() & 0xF0000000) >>> 28;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0FFF0000
     *
     * @param seedBarrel WU Item object
     * @return int value, how many seed to move into the seedBarrel for a supply action.
     */
    static int decodeSupplyQuantity(Item seedBarrel) {
        return (seedBarrel.getData1() & 0xFFF0000) >>> 16 ;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0000FF80.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from
     * WU.
     *
     * @param seedBarrel WU Item object
     * @return int value, an identifier for Wrap.Crop for the seed type in barrel.
     */
    public static int decodeContainedSeed(Item seedBarrel) {
        int id = (seedBarrel.getData1() & 0xFF80) >>> 7;
        if (id > Crops.values().length - 1)
            return 0;
        else
            return id;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0000007F.
     * This gives up to 127 values even know we only need 100 for quality. I don't know how to isolate just 100.
     *
     * @param seedBarrel WU Item object
     * @return int value, The quality of contained crops.
     */
    static int decodeContainedQuality(Item seedBarrel){
        return seedBarrel.getData1() & 0x7F;
    }

    /**
     * Encode into Item.data1()0xF worth of data into bit positions 32-29(0xF0000000). This is the barrel's sow radius.
     * This gives 16 potential values, 0 for one tile all the way up to 15 for 961 tiles (31x31 square).
     *
     * @param seedBarrel WU Item object
     * @param sowRadius int primitive
     */
    static void encodeSowRadius(Item seedBarrel, int sowRadius){
        int preservedData = seedBarrel.getData1() & 0x0FFFFFFF;
        seedBarrel.setData1( (sowRadius << 28) + preservedData);
    }

    /**
     * Encode into Item.data1() 0xFFF worth of data into bit positions 28-17(0x0FFF0000). This is the barrel's supply quantity.
     * This gives up to 4095 potential filling values.
     *
     * @param seedBarrel WU Item object
     * @param supplyQuantity int primitive
     */
    static void encodeSupplyQuantity(Item seedBarrel, int supplyQuantity){
        int preservedData = seedBarrel.getData1() & 0xF000FFFF;
        seedBarrel.setData1((supplyQuantity << 16) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x1FF worth of data into bit positions 16-8(0xFFFF007F). This is the barrel's seed ID.
     * This gives up to 511 potential seed types. These values are from the Crops enum as opposed to ItemTemplates from
     * WU.
     *
     * @param seedBarrel WU Item object
     * @param cropId int primitive
     */
    static void encodeContainedSeed(Item seedBarrel, int cropId){
        int preservedData = seedBarrel.getData1() & 0xFFFF007F; // 1111 1111 1111 1111 0000 0000 0111 1111
        seedBarrel.setData1((cropId << 7) + preservedData);
    }

    /**
     * Encode into Item.data1() 0x7F worth of data into bit positions 7-1(0xFFFFF8). This is the barrel's seed quality.
     * This gives up to 127 values even know we only need 100 for quality. I don't know how to isolate just 100.
     *
     * @param seedBarrel WU Item object
     * @param quality int primitive
     */
    static void encodeContainedQuality(Item seedBarrel, int quality){
        int preservedData = seedBarrel.getData1() & 0xFFFFF800; // 1111 1111 1111 1111 1111 1000 0000
        seedBarrel.setData1(quality + preservedData);
    }
}
