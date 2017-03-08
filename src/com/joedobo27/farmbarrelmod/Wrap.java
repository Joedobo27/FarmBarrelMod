package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.items.Item;

import java.util.Arrays;
import java.util.Objects;

class Wrap {

    @SuppressWarnings("unused")
    enum Actions {
        ACTION_QUICK(0,""),
        ACTION_NEED_FOOD(1,"Action blocked if food is too low."),
        ACTION_SPELL(2,""),
        ACTION_ATTACK(3,""),
        ACTION_FATIGUE(4,""),
        ACTION_POLICED(5,""),
        ACTION_NOMOVE(6,"Actions is cancelled if toon moves."),
        ACTION_NON_LIBILAPRIEST(7,""),
        ACTION_NON_WHITEPRIEST(8,""),
        ACTION_NON_RELIGION(9,"Doing Actions are considered unfaithful."),
        ACTION_ATTACK_HIGH(12,""),
        ACTION_ATTACK_LOW(13,""),
        ACTION_ATTACK_LEFT(14,""),
        ACTION_ATTACK_RIGHT(15,""),
        ACTION_DEFEND(16,""),
        ACTION_STANCE_CHANGE(17,""),
        ACTION_ALLOW_MAGRANON(18,""),
        ACTION_ALLOW_FO(19,""),
        ACTION_ALLOW_VYNORA(20,""),
        ACTION_ALLOW_LIBILA(21,""),
        ACTION_NO_OPPORTUNITY(22,""),
        ACTION_IGNORERANGE(23,""),
        ACTION_VULNERABLE(24,""),
        ACTION_MISSION(25,""),
        ACTION_NOTVULNERABLE(26,""),
        ACTION_NONSTACKABLE(27,""),
        ACTION_NONSTACKABLE_FIGHT(28,""),
        ACTION_BLOCKED_NONE(29,""),
        ACTION_BLOCKED_FENCE(30,""),
        ACTION_BLOCKED_WALL(31,""),
        ACTION_BLOCKED_FLOOR(32,""),
        ACTION_BLOCKED_ALL_BUT_OPEN(33,""),
        ACTION_BLOCKED_TARGET_TILE(34,""),
        ACTION_MAYBE_USE_ACTIVE_ITEM(35,""),
        ACTION_ALWAYS_USE_ACTIVE_ITEM(36,""),
        ACTION_NEVER_USE_ACTIVE_ITEM(37,""),
        ACTION_ALLOW_MAGRANON_IN_CAVE(38,""),
        ACTION_ALLOW_FO_ON_SURFACE(39,""),
        ACTION_ALLOW_LIBILA_IN_CAVE(40,""),
        ACTION_USES_NEW_SKILL_SYSTEM(41,""),
        ACTION_VERIFIED_NEW_SKILL_SYSTEM(42,""),
        ACTION_SHOW_ON_SELECT_BAR(43,""),
        ACTION_SAME_BRIDGE(44,""),
        ACTION_PERIMETER(45,""),
        ACTION_CORNER(46,""),
        ACTION_ENEMY_NEVER(47,""),
        ACTION_ENEMY_ALWAYS(48,""),
        ACTION_ENEMY_NO_GUARDS(49,""),
        ACTION_BLOCKED_NOT_DOOR(50,"");

        private final int id;
        private final String description;

        Actions(int actionID, String description){
            this.id = actionID;
            this.description = description;
        }

        public int getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    enum Rarity {
        NO_RARITY(0),
        RARE(1),
        SUPREME(2),
        FANTASTIC(3);

        /*
        Player getRarity()
        supreme 1 in 33.334 chance ... 3/100=33.334 for Paying. 1 in 100 chance for F2P
        fantastic 1 in 9708.737 chance ... 1.03f/10,000; 103 in 1,000,000 for Paying. 1 in 10,000 chance for F2P.

        improvement has a 1 in 5 chance to go rare if: the power/success of action is > 0, and the action's rarity
        is greater then the rarity of the item.

        */
        private final int id;

        Rarity(int id){
            this.id = id;
        }

        public byte getId() {
            return (byte)id;
        }
    }

    @SuppressWarnings("unused")
    enum Crops {
        BARLEY(0, 28, 28, 20),
        WHEAT(1, 29, 29, 30),
        RYE(2, 30, 30, 10),
        OAT(3, 31, 31, 15),
        CORN(4, 32, 32, 40),
        PUMPKIN(5, 34, 33, 15),
        POTATO(6, 35, 35, 4),
        COTTON(7, 145, 144, 7),
        WEMP(8, 317, 316, 10),
        GARLIC(9, 356, 356, 70),
        ONION(10, 355, 355, 60),
        REED(11, 744, 743, 20),
        RICE(12, 746, 746, 80),
        STRAWBERRIES(13, 750, 362, 60),
        CARROTS(14, 1145, 1133, 25),
        CABBAGE(15, 1146, 1134, 35),
        TOMATOS(16, 1147, 1135, 45),
        SUGAR_BEET(17, 1148, 1136, 85),
        LETTUCE(18, 1149, 1137, 55),
        PEAS(19, 1150, 1138, 65),
        CUCUMBER(20, 1248, 1247, 15);

        private final int id;
        private final int seedTemplateId;
        private final int productTemplateId;
        private final double difficulty;

        Crops(int id, int seedTemplateId, int productTemplateId, double difficulty){
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

        static int getCropIdFromSeedTemplateId(int seedTemplateId) {
           return Arrays.stream(values())
                    .filter(crops -> Objects.equals(seedTemplateId, crops.getSeedTemplateId()))
                    .mapToInt(Crops::getId)
                    .findFirst()
                    .orElseThrow(() -> new NullPointerException("No matching seedTemplateId in Crops.enum"));
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
                    .orElseThrow(() -> new NullPointerException("No matching cropId in Crops.enum"));
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

    @SuppressWarnings("unused")
    static Item getItemFromID(long id){
        try {
            return Items.getItem(id);
        }catch (NoSuchItemException e){
            return null;
        }
    }

    @SuppressWarnings("unused")
    static int getTemplateWeightFromItem(Item item){
        return item.getTemplate().getWeightGrams();
    }

}
