package com.joedobo27.fbm;

import com.wurmonline.server.questions.Question;
import org.gotti.wurmunlimited.modsupport.bml.BmlBuilder;
import org.gotti.wurmunlimited.modsupport.bml.TextStyle;
import org.gotti.wurmunlimited.modsupport.questions.ModQuestion;

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
            boolean autoResow = Boolean.parseBoolean(answer.getProperty("autoResow"));
            int sowRadius = Integer.parseInt(answer.getProperty("sowRadius"));
            int supplyQuantity = Integer.parseInt(answer.getProperty("supplyQuantity"));

            if (autoResow != this.farmBarrel.isAutoResow() || sowRadius != this.farmBarrel.getSowRadius() ||
                    supplyQuantity != this.farmBarrel.getSupplyQuantity()) {
                farmBarrel.configureUpdate(autoResow, sowRadius, supplyQuantity);
            }
        }
    }

    @Override
    public void sendQuestion(Question question) {
        BmlBuilder bmlBuilder =
            BmlBuilder.builder()
            .withNode(table(2).withAttribute("rows", 4)
                .withNode(label("Input", TextStyle.BOLD))
                .withNode(label("Current setting", TextStyle.BOLD))
                .withNode(checkbox("autoResow", "").withAttribute("selected",farmBarrel.isAutoResow()))
                .withNode(label(String.format("Current auto-resow: %1$s", Boolean.toString(farmBarrel.isAutoResow()))))
                .withNode(input("sowRadius").withAttribute("maxchars", 20))
                .withNode(label(String.format("Current radius: %1$s", Integer.toString(farmBarrel.getSowRadius()))))
                .withNode(input("supplyQuantity").withAttribute("maxchars", 20))
                .withNode(label(String.format("Current supply amount: %1$s", Integer.toString(farmBarrel.getSupplyQuantity())))))
            .withNode(button("submit", "Send"));
        String bml = bmlBuilder.buildBml();
        question.getResponder().getCommunicator().sendBml(300, 150, true, true,
                                                          bml, 200, 200, 200, question.getTitle());
    }
}
