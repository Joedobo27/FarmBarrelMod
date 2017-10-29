package com.joedobo27.fbm;

import com.wurmonline.server.questions.Question;
import org.gotti.wurmunlimited.modsupport.bml.BmlBuilder;
import org.gotti.wurmunlimited.modsupport.bml.BmlNodeBuilder;
import org.gotti.wurmunlimited.modsupport.bml.TextStyle;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestion;

import java.util.Objects;
import java.util.Properties;

import static org.gotti.wurmunlimited.modsupport.bml.BmlBuilder.*;


public class ConfigureSeedBarrelQuestion implements ModQuestion {
    private final FarmBarrel farmBarrel;
    private final int questionType;


    ConfigureSeedBarrelQuestion(FarmBarrel farmBarrel, int questionType) {
        this.farmBarrel = farmBarrel;
        this.questionType = questionType;
    }

    @Override
    public int getType() {
        return this.questionType;
    }

    @Override
    public void answer(Question question, Properties answer) {
        if (question.getType() == 0) {
            FarmBarrelMod.logger.warning( "Received answer for a question with NOQUESTION.");
            return;
        }
        if (this.getType() == question.getType()) {
            boolean autoResow = Boolean.parseBoolean(answer.getProperty("autoResow",
                    Boolean.toString(this.farmBarrel.isAutoResow())));
            int sowRadius;
            try {
                sowRadius = Integer.parseInt(answer.getProperty("sowRadius",
                        Integer.toString(this.farmBarrel.getSowRadius())));
            } catch (NumberFormatException e) {
                sowRadius = this.farmBarrel.getSowRadius();
            }
            int supplyQuantity;
            try {
                supplyQuantity = Integer.parseInt(answer.getProperty("supplyQuantity",
                        Integer.toString(this.farmBarrel.getSupplyQuantity())));
            } catch (NumberFormatException e) {
                supplyQuantity = this.farmBarrel.getSupplyQuantity();
            }

            if (autoResow != this.farmBarrel.isAutoResow() || sowRadius != this.farmBarrel.getSowRadius() ||
                    supplyQuantity != this.farmBarrel.getSupplyQuantity()) {
                this.farmBarrel.configureUpdate(autoResow, sowRadius, supplyQuantity);
                this.farmBarrel.doFarmBarrelToInscriptionJson();
            }
        }
    }

    @Override
    public void sendQuestion(Question question) {

        BmlNodeBuilder tableNode = table(2).withAttribute("rows", 4);
        tableNode.withNode(label("Input", TextStyle.BOLD).withAttribute("size", "30,20"));
        tableNode.withNode(label("Current setting", TextStyle.BOLD).withAttribute("size", "50,20"));
        tableNode.withNode(checkbox("autoResow", "").withAttribute("selected", this.farmBarrel.isAutoResow()));
        tableNode.withNode(label(String.format("Current auto-resow: %1$s", Boolean.toString(this.farmBarrel.isAutoResow()))));
        tableNode.withNode(input("sowRadius").withAttribute("maxchars", 20).withAttribute("size", "30,20"));
        tableNode.withNode(label(String.format("Current radius: %1$s", Integer.toString(this.farmBarrel.getSowRadius()))));
        tableNode.withNode(input("supplyQuantity").withAttribute("maxchars", 20).withAttribute("size", "30,20"));
        tableNode.withNode(label(String.format("Current supply amount: %1$s", Integer.toString(this.farmBarrel.getSupplyQuantity()))));

        BmlBuilder bmlBuilder = BmlBuilder.builder();
        bmlBuilder.withNode(passthough("id", Integer.toString(question.getId())));
        bmlBuilder.withNode(tableNode);
        bmlBuilder.withNode(button("submit", "Send"));
        bmlBuilder = bmlBuilder.wrapAsDialog(question.getTitle(), false, false, false);
        String bml = bmlBuilder.buildBml();
        question.getResponder().getCommunicator().sendBml(300, 200, true, true,
                                                          bml, 200, 200, 200, question.getTitle());
    }
}
