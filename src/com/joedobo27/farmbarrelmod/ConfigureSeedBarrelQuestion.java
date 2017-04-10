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
    private static final Logger logger = Logger.getLogger(FarmBarrelMod.class.getName());

    ConfigureSeedBarrelQuestion(Creature responder, String title, String question, int type, long aTarget) {
        this.questionType = type;
        try {this.seedBarrel = Items.getItem(aTarget);}catch (Exception ignored){}
        this.sowRadius = FarmBarrelMod.decodeSowRadius(this.seedBarrel);
        this.supplyQuantity = FarmBarrelMod.decodeSupplyQuantity(this.seedBarrel);
        sendQuestion(ModQuestions.createQuestion(responder, title, question, aTarget, this));
    }

    @Override
    public int getType() {
        return this.questionType;
    }

    @Override
    public void answer(Question question, Properties answer) {
        if (question.getType() == 0) {
            logger.log(Level.INFO, "Received answer for a question with NOQUESTION.");
            return;
        }
        if (this.getType() == question.getType()) {
            boolean radiusBox = Boolean.parseBoolean(answer.getProperty("radiusBox"));
            if (radiusBox) {
                this.sowRadius = Integer.parseInt(answer.getProperty("radiusValue"));
            }
            boolean supplyBox = Boolean.parseBoolean(answer.getProperty("supplyBox"));
            if (supplyBox) {
                this.supplyQuantity = Integer.parseInt(answer.getProperty("supplyValue"));
            }
            if (radiusBox || supplyBox){
                FarmBarrelMod.encodeSowRadius(this.seedBarrel, this.sowRadius);
                FarmBarrelMod.encodeSupplyQuantity(this.seedBarrel, this.supplyQuantity);
            }
        }
    }

    @Override
    public void sendQuestion(Question question) {
        BmlForm bmlForm = new BmlForm(question.getTitle(), 300, 150);
        bmlForm.addHidden("id", Integer.toString(question.getId()));
        bmlForm.beginTable(3, 3,
                "label{text=\"\"};text{type=\"bold\";text=\"Input\"};text{type=\"bold\";text=\"Current setting.\"};");
        bmlForm.addCheckBox("radiusBox", false);
        bmlForm.addInput("radiusValue", Integer.toString(this.sowRadius), 50);
        bmlForm.addLabel(String.format("radiusValue of %1$s.", Integer.toString(this.sowRadius)));
        bmlForm.addCheckBox("supplyBox", false);
        bmlForm.addInput("supplyValue", Integer.toString(this.supplyQuantity), 50);
        bmlForm.addLabel(String.format("supply value of %1$s.", Integer.toString(this.supplyQuantity)));
        bmlForm.endTable();
        bmlForm.addButton("Send", "submit");
        String bml = bmlForm.toString();
        //logger.log(Level.INFO, bml);
        question.getResponder().getCommunicator().sendBml(300, 150, true, true,
                bml, 200, 200, 200, question.getTitle());
    }


}
