package com.joedobo27.farmbarrelmod;


import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;


public class BmlForm {
    private static Logger logger;
    private final StringBuffer buf = new StringBuffer();
    private static final String tabs = "\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t";

    private int openBorders = 0;
    private int openCenters = 0;
    private int openVarrays = 0;
    private int openScrolls = 0;
    private int openHarrays = 0;

    private int indentNum = 0;
    private boolean beautify = false;

    private boolean closeDefault = false;

    static {
        logger = Logger.getLogger(BmlForm.class.getName());
    }

    public BmlForm() {
    }

    public BmlForm(String formTitle) {
        beginBorder();
        beginCenter();
        addBoldText(formTitle);
        endCenter();

        beginScroll();
        beginVerticalFlow();

        closeDefault = true;        // in toString() we close the opened: varray, scroll, border
    }

    public BmlForm(String formTitle, int width, int height) {
        beginBorder(width, height);
        beginCenter();
        addBoldText(formTitle);
        endCenter();

        beginScroll();
        beginVerticalFlow();
        buf.append(";");

        closeDefault = true;        // in toString() we close the opened: varray, scroll, border
    }


    public void beginBorder() {
        buf.append(indent("border{"));
        indentNum++;
        openBorders++;
    }

    public void beginBorder(int width, int height) {
        buf.append(indent("border{"));
        setSizeAttribute(width, height);
        indentNum++;
        openBorders++;
    }

    public void endBorder() {
        indentNum--;
        buf.append(indent("}"));
        openBorders--;
    }

    public void beginCenter() {
        buf.append(indent("center{"));
        indentNum++;
        openCenters++;
    }

    public void endCenter() {
        indentNum--;
        buf.append(indent("};null;"));
        openCenters--;
    }

    public void beginVerticalFlow() {
        buf.append(indent("varray{rescale=\"true\";"));
        indentNum++;
        openVarrays++;
    }

    public void endVerticalFlow() {
        indentNum--;
        buf.append(indent("}"));
        openVarrays--;
    }

    public void beginScroll() {
        buf.append(indent("scroll{vertical=\"true\";horizontal=\"false\";"));
        indentNum++;
        openScrolls++;
    }

    public void endScroll() {
        indentNum--;
        buf.append(indent("};null;null;"));
        openScrolls--;
    }

    public void beginHorizontalFlow() {
        buf.append(indent("harray {"));
        indentNum++;
        openHarrays++;
    }

    public void endHorizontalFlow() {
        indentNum--;
        buf.append(indent("}"));
        openHarrays--;
    }

    public void addBoldText(String text, String... args) {
        addText(text, "bold", args);
    }

    public void addHidden(String name, String val) {
        buf.append(indent("passthrough{id=\"" + name + "\";text=\"" + val + "\"}"));
    }

    public void addText(String text, String... args) {
        addText(text, "", args);
    }

    private String indent(String s) {
        return (beautify ? getIndentation() + s + "\r\n" : s);
    }

    private String getIndentation() {
        if (indentNum > 0) {
            return tabs.substring(0, indentNum);
        }
        return "";
    }

    public void addRaw(String s) {
        buf.append(s);
    }

    private void addText(String text, String type, String... args) {
        String[] lines = text.split("\n");

        for (String l : lines) {
            if (beautify) {
                buf.append(getIndentation());
            }

            buf.append("text{");
            if (!type.equals("")) {
                buf.append("type='").append(type).append("';");
            }
            buf.append("text=\"");

            buf.append(String.format(l, (Object[]) args));
            buf.append("\"}");

            if (beautify) {
                buf.append("\r\n");
            }
        }
    }

    public void addButton(String name, String id) {
        buf.append(indent("button{text='  " + name + "  ';id='" + id + "'}"));
    }

    public void addInput(@NotNull String id, @Nullable String defaultText, @Nullable Integer maxChar) {

        buf.append("input{id=\"").append(id).append("\"");
        if (defaultText != null) {
            buf.append(";text=\"").append(defaultText).append("\"");
        }
        if (maxChar != null) {
            buf.append(";maxchars=\"").append(maxChar).append("\"");
        }
        buf.append("};");
    }

    public void addCheckBox(@NotNull String id, @NotNull Boolean selected ) {
        buf.append("checkbox{id=\"").append(id).append("\";");
        buf.append("selected=\"").append(Boolean.toString(selected)).append("\"};");
    }

    public void addLabel(@NotNull String text) {
        buf.append("label{text=\"").append(text).append("\"};");
    }

    public void setSizeAttribute(int width, int height) {
        buf.append(String.format("size=\"%1$s,%2$s\"", width, height));
    }

    public String toString() {
        if(closeDefault) {
            endVerticalFlow();
            endScroll();
            endBorder();
            closeDefault = false;
        }

        if(openCenters != 0 || openVarrays != 0 || openScrolls != 0 || openHarrays != 0 || openBorders != 0) {
            logger.log(Level.SEVERE, "While finalizing BML unclosed (or too many closed) blocks were found (this will likely mean the BML will not work!):"
                    + " center: " + openCenters
                    + " vert-flows: " + openVarrays
                    + " scroll: " + openScrolls
                    + " horiz-flows: " + openHarrays
                    + " border: " + openBorders
            );
        }

        return buf.toString();
    }

}
