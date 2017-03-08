package com.wurmonline.server.questions;

import com.joedobo27.farmbarrelmod.BmlForm;
import com.wurmonline.server.Items;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

import java.util.Properties;
import java.util.logging.Level;


public class ConfigureSeedBarrelQuestion extends Question {

    private Item seedBarrel;
    private int sowRadius;
    private int supplyQuantity;

    public ConfigureSeedBarrelQuestion(Creature aResponder, String aTitle, String aQuestion, int aType, long aTarget) {
        super(aResponder, aTitle, aQuestion, aType, aTarget);
        try {this.seedBarrel = Items.getItem(aTarget);}catch (Exception ignored){}
        this.sowRadius = decodeRadius();
        this.supplyQuantity = decodeSupplyQuantity();
        logger.log(Level.INFO, "construct " + this.seedBarrel.toString());
        logger.log(Level.INFO, "id " + aType);
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0xF0000000
     *
     * @return int value, (Side - 1) / 2 = return; where side is always odd.
     */
    private int decodeRadius() {
        return (this.seedBarrel.getData1() & 0xF0000000) & 0xF;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0FFF0000
     *
     * @return int value, how many seed to move into the seedBarrel for a supply action.
     */
    private int decodeSupplyQuantity() {
        return (this.seedBarrel.getData1() & 0x0FFF0000) & 0xFFF;
    }

    /**
     * Write a serialized int value into the data1 column for the ITEMDATA table. Writing values for 0xFFFF0000 part of the
     * field's int value.
     */
    private void encodeData1(){
        this.seedBarrel.setData1( (this.sowRadius << 7) + (this.supplyQuantity << 4) );
    }

    @Override
    public void answer(Properties answer) {
        logger.log(Level.INFO, "abc " + answer.toString());
        this.setAnswer(answer);
        if (this.type == 0) {
            logger.log(Level.INFO, "Received answer for a question with NOQUESTION.");
            return;
        }
        if (this.type == 501) {
            logger.log(Level.INFO, this.seedBarrel.toString());
            boolean radiusBox = Boolean.parseBoolean(answer.getProperty("radiusBox"));
            if (radiusBox) {
                sowRadius = Integer.parseInt(answer.getProperty("radiusValue"));
            }
            final boolean supplyBox = Boolean.parseBoolean(answer.getProperty("supplyBox"));
            if (supplyBox) {
                supplyQuantity = Integer.parseInt(answer.getProperty("supplyValue"));
            }
            if (radiusBox || supplyBox){
                encodeData1();
            }
        }
    }

    @Override
    public void sendQuestion() {
        BmlForm bmlForm = new BmlForm(getQuestion(), 200, 200);
        bmlForm.addHidden("id ", ""+this.id);
        bmlForm.beginHorizontalFlow();
        bmlForm.addLabel("Select");
        bmlForm.addLabel("Change");
        bmlForm.addText("---");
        bmlForm.endHorizontalFlow();
        bmlForm.beginHorizontalFlow();
        bmlForm.addCheckBox("radiusBox", false);
        bmlForm.addInput("radiusValue", Integer.toString(sowRadius), 10);
        bmlForm.addText("radiusValue of %1$s.", Integer.toString(sowRadius));
        bmlForm.endHorizontalFlow();
        bmlForm.beginHorizontalFlow();
        bmlForm.addCheckBox("supplyBox", false);
        bmlForm.addInput("supplyValue", Integer.toString(supplyQuantity), 10);
        bmlForm.addText("supply value of %1$s.", Integer.toString(supplyQuantity));
        bmlForm.endHorizontalFlow();
        bmlForm.addButton("Send", "submit");
        String bml = bmlForm.toString();
        //logger.log(Level.INFO, bml);
        this.getResponder().getCommunicator().sendBml(200, 200, true, true,
                bml, 200, 200, 200, this.title);
    }
}
