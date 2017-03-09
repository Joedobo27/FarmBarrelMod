package com.joedobo27.farmbarrelmod;

import com.wurmonline.server.Items;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.questions.Question;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestion;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestions;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConfigureSeedBarrelQuestion implements ModQuestion {
    private Item seedBarrel;
    private int sowRadius;
    private int supplyQuantity;
    private int questionType;
    private boolean isAnswered;
    private Question myQuestion;
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    ConfigureSeedBarrelQuestion(Creature responder, String title, String question, int type, long aTarget) {
        myQuestion = ModQuestions.createQuestion(responder, title, question, aTarget, this);
        sendQuestion(myQuestion);
        this.questionType = type;
        this.isAnswered = false;
        try {this.seedBarrel = Items.getItem(aTarget);}catch (Exception ignored){}
        this.sowRadius = decodeRadius();
        this.supplyQuantity = decodeSupplyQuantity();
    }

    @Override
    public int getType() {
        return this.questionType;
    }

    @Override
    public void answer(Question question, Properties answer) {
        logger.log(Level.INFO, "question: " + question.toString());
        logger.log(Level.INFO, "answer: " + answer.toString());
        logger.log(Level.INFO, "quesType: " + question.getType());
        logger.log(Level.INFO, "thisType: " + this.getType());
        if (question.getType() == 0) {
            logger.log(Level.INFO, "Received answer for a question with NOQUESTION.");
            return;
        }
        if (this.getType() == question.getType()) {
            logger.log(Level.INFO, "answer: " + this.seedBarrel.toString());
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
            this.isAnswered = true;
        }
    }

    @Override
    public void sendQuestion(Question question) {
        BmlForm bmlForm = new BmlForm(question.getTitle(), 200, 200);
        bmlForm.addHidden("id", Integer.toString(this.myQuestion.getId()));
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
        question.getResponder().getCommunicator().sendBml(200, 200, true, true,
                bml, 200, 200, 200, question.getTitle());
    }

    public boolean isAnswered() {
        return this.isAnswered;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0xF0000000
     *
     * @return int value, (Side - 1) / 2 = return; where side is always odd.
     */
    private int decodeRadius() {
        return (this.seedBarrel.getData1() >> 7) & 0xF;
    }

    /**
     * Retrieve custom serialized data from the data1 column of the ITEMDATA table. Custom defined by the mask: 0x0FFF0000
     *
     * @return int value, how many seed to move into the seedBarrel for a supply action.
     */
    private int decodeSupplyQuantity() {
        return (this.seedBarrel.getData1() >> 4) & 0xFFF;
    }

    /**
     * Write a serialized int value into the data1 column for the ITEMDATA table. Writing values for 0xFFFF0000 part of the
     * field's int value.
     */
    private void encodeData1(){
        this.seedBarrel.setData1( (this.sowRadius << 7) + (this.supplyQuantity << 4) );
    }
}
