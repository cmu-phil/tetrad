///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.StringTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serial;
import java.text.ParseException;
import java.util.List;
import java.util.*;

/**
 * Edits an expression for a node in the generalized SEM PM.
 *
 * @author josephramsey
 */
class GeneralizedTemplateEditor extends JComponent {

    @Serial
    private static final long serialVersionUID = 3455126100397759982L;
    /**
     * The text pane in which parsed text is rendered, typed, and colored.
     */
    private final JTextPane expressionTextPane;
    /**
     * The document for the expression text pane that holds the parsed text.
     */
    private final DefaultStyledDocument expressionTextDoc;
    /**
     * The generalized SEM PM that's being edited.
     */
    private final GeneralizedSemPm semPm;
    /**
     * Represents a combo box used for user selection.
     */
    private final JComboBox<String> combo = new JComboBox<>();
    /**
     * The field that indicates what names of variables or parameters should start with to be changed.
     */
    private final StringTextField startsWithField;
    /**
     * The box in which labels for each equation are rendered.
     */
    private final Box equationsBox;
    /**
     * The start index of selected text.
     */
    private int start;
    /**
     * The width of the selected text.
     */
    private int stringWidth;
    /**
     * The latest parser that was used to parse the expression in <code>expressionTextPane</code>. Needed to get the
     * most up-to-date list of parameters.
     */
    private ExpressionParser latestParser;

    //=============================================CONSTRUCTORS================================================//

    /**
     * Constructs the editor.
     *
     * @param semPm The GeneralizedSemPm that's being edited. The edits will be made to a copy of this PM.
     */
    public GeneralizedTemplateEditor(GeneralizedSemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must be provided.");
        }

        this.semPm = new GeneralizedSemPm(semPm);

        this.equationsBox = Box.createVerticalBox();
        JScrollPane scroll = new JScrollPane(this.equationsBox);
        scroll.setPreferredSize(new Dimension(400, 300));

        StyleContext sc = new StyleContext();
        this.expressionTextDoc = new DefaultStyledDocument(sc);
        this.expressionTextPane = new JTextPane(this.expressionTextDoc);

        setParseText(semPm.getVariablesTemplate());

        ExpressionParser parser = new ExpressionParser(); //new ArrayList<String>(), ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);
        this.latestParser = parser;

        try {
            String template = getSemPm().getVariablesTemplate();
            parser.parseExpression(template);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot parse the stored expression.", e);
        }

        Map<String, String> expressionsMap = getExpressionsMap(semPm);
        String[] expressionTokens = getExpressionTokens(expressionsMap);
        JComboBox<String> expressionsBox = new JComboBox<>(expressionTokens);
        expressionsBox.setMaximumSize(expressionsBox.getPreferredSize());

        JButton insertButton = getjButton(expressionsBox, expressionsMap);

        this.combo.addItem("Variables");
        this.combo.addItem("Errors");
        this.combo.addItem("Parameter Initializations");
        this.combo.addItem("Estimation Starting Values");

        this.combo.setSelectedItem("Variables");

        updateEquationsDisplay();

        this.combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox<?> source = (JComboBox<?>) e.getSource();
                String item = (String) source.getSelectedItem();

                if ("Variables".equals(item)) {
                    String variablesTemplate = getSemPm().getVariablesTemplate();
                    setParseText(variablesTemplate);
                    updateEquationsDisplay();
                } else if ("Errors".equals(item)) {
                    String errorsTemplate = getSemPm().getErrorsTemplate();
                    setParseText(errorsTemplate);
                    updateEquationsDisplay();
                } else if ("Parameter Initializations".equals(item)) {
                    String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterTemplate(startsWith);
                    if (template == null) {
                        template = getSemPm().getParametersTemplate();
                    }

                    setParseText(template);
                    updateEquationsDisplay();
                } else if ("Estimation Starting Values".equals(item)) {
                    String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterEstimationInitializationTemplate(startsWith);
                    if (template == null) {
                        template = getSemPm().getParametersEstimationInitializationTemplate();
                    }

                    setParseText(template);
                    updateEquationsDisplay();
                } else {
                    throw new IllegalStateException("Unrecognized Combo Box Item: " + item);
                }
            }
        });

        JButton applyButton = getjButton();

        JButton saveTemplate = new JButton("Save Template");

        saveTemplate.addActionListener(e -> listen());

        this.startsWithField = new StringTextField("", 6);

        this.startsWithField.setFilter((value, oldValue) -> {
            String item = (String) GeneralizedTemplateEditor.this.combo.getSelectedItem();

            if ("Variables".equals(item)) {
                String variablesTemplate = getSemPm().getVariablesTemplate();
                setParseText(variablesTemplate);
            } else if ("Errors".equals(item)) {
                String errorsTemplate = getSemPm().getErrorsTemplate();
                setParseText(errorsTemplate);
            } else if ("Parameter Initializations".equals(item)) {
                String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                String template = getSemPm().getStartsWithParameterTemplate(startsWith);
                if (template == null) {
                    template = getSemPm().getParametersTemplate();
                }

                setParseText(template);
            } else if ("Estimation Starting Values".equals(item)) {
                String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                String template = getSemPm().getStartsWithParameterEstimationInitializationTemplate(startsWith);
                if (template == null) {
                    template = getSemPm().getParametersEstimationInitializationTemplate();
                }

                setParseText(template);
            } else {
                throw new IllegalStateException("Unrecognized Combo Box Item: " + item);
            }

            return value;
        });

        Box b = Box.createVerticalBox();

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Type Template:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(expressionsBox);
        b4.add(Box.createHorizontalStrut(5));
        b4.add(insertButton);
        b.add(b4);
        b.add(Box.createVerticalStrut(5));

        JScrollPane expressionScroll = new JScrollPane(this.expressionTextPane);
        expressionScroll.setPreferredSize(new Dimension(500, 50));
        Box b5 = Box.createHorizontalBox();
        b5.add(expressionScroll);
        b.add(b5);
        b.add(Box.createVerticalStrut(5));

        JPanel applyToPanel = new JPanel();
        applyToPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        Box b7 = Box.createHorizontalBox();
        b7.add(new JLabel("Apply to: "));
        b7.add(this.combo);
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("Starts with: "));
        b7.add(this.startsWithField);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        b.add(new JScrollPane(scroll));

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(saveTemplate);
        b8.add(applyButton);
        b.add(b8);
        b.add(Box.createVerticalStrut(5));

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);


        this.expressionTextDoc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent documentEvent) {
                listen();
            }

            public void removeUpdate(DocumentEvent documentEvent) {
                listen();
            }

            public void changedUpdate(DocumentEvent documentEvent) {
                listen();
            }
        });

        Style red = expressionTextPane.addStyle("Red", null);
        StyleConstants.setForeground(red, Color.RED);

        Style black = expressionTextPane.addStyle("Black", null);
        StyleConstants.setForeground(black, Color.BLACK);

        this.expressionTextPane.setCaretPosition(this.expressionTextPane.getText().length());

        // When the dialog closes, we want to make sure the expression gets parsed and set.
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                listen();
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });

        setFocusCycleRoot(true);
    }

    @NotNull
    private JButton getjButton() {
        JButton applyButton = new JButton("APPLY");

        applyButton.addActionListener(actionEvent -> {
            applyChanges();
            updateEquationsDisplay();

            if ("Variables".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                try {
                    getSemPm().setVariablesTemplate(template);
                } catch (ParseException e) {
                    //
                }
            } else if ("Errors".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                try {
                    getSemPm().setErrorsTemplate(template);
                } catch (ParseException e) {
                    //
                }
            } else if ("Parameter Initializations".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                try {
                    getSemPm().setParametersTemplate(template);

                    String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();

                    getSemPm().setStartsWithParametersTemplate(startsWith, template);
                } catch (ParseException e) {
                    //
                }
            } else if ("Estimation Starting Values".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                try {
                    getSemPm().setParametersEstimationInitializationTemplate(template);

                    String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();

                    getSemPm().setStartsWithParametersEstimationInitializationTemplate(startsWith, template);
                } catch (ParseException e) {
                    //
                }
            }

        });
        return applyButton;
    }

    @NotNull
    private JButton getjButton(JComboBox<String> expressionsBox, Map<String, String> expressionsMap) {
        JButton insertButton = new JButton("Insert");

        insertButton.addActionListener(actionEvent -> {
            String token = (String) expressionsBox.getSelectedItem();
            String signature;

            if ("-New Parameter-".equals(token)) {
                signature = nextParameterName();
            } else {
                signature = expressionsMap.get(token);
            }

            while (signature.contains("%")) {
                signature = signature.replaceFirst("%", nextParameterName());
            }

            GeneralizedTemplateEditor.this.expressionTextPane.replaceSelection(signature);
        });
        return insertButton;
    }

    //==================================================PRIVATE METHODS=========================================//

    private String[] getExpressionTokens(Map<String, String> expressionsMap) {
        String[] expressionTokens = new String[expressionsMap.keySet().size()];
        int i = -1;

        for (String token : expressionsMap.keySet()) {
            expressionTokens[++i] = token;
        }
        return expressionTokens;
    }

    private Map<String, String> getExpressionsMap(GeneralizedSemPm semPm) {
        String[][] templateExpressions = {
                {"NEW", "NEW(b)"},
                {"new", "new(b)"},
                {"TSUM", "TSUM($)"},
                {"tsum", "tsum($)"},
                {"TPROD", "TPROD($)"},
                {"tprod", "tprod($)"},
        };

        // These are the expressions the user can choose from. The display form is on the left, and the template
        // form is on the right. You use a % for a new parameter. In case you want to change it.
        String[][] expressions = {
                {"+", " + "},
                {"-", " - "},
                {"*", " * "},
                {"/", " / "},
                {"^", "^"},
//                {"+(a, b, ...)", "+(%, %)"},
//                {"*(a, b, ...)", "*(%, %)"},
                {"pow(a, b)", "pow(%, %)"},
                {"sqrt(a)", "sqrt(%)"},
                {"sin(a)", "sin(%)"},
                {"cos(a)", "cos(%)"},
                {"tan(a)", "tan(%)"},
                {"asin(a)", "asin(%)"},
                {"acos(a)", "acos(%)"},
                {"atan(a)", "atan(%)"},
                {"sinh(a)", "sinh(%)"},
                {"tanh(a)", "tanh(%)"},
                {"ln(a)", "ln(%)"},
                {"log10(a)", "log10(%)"},
                {"round(a)", "round(%)"},
                {"ceil(a)", "ceil(%)"},
                {"floor(a)", "floor(%)"},
                {"signum(a)", "signum(%)"},
                {"abs(a)", "abs(%)"},
                {"max(a, b, ...)", "max(%, %)"},
                {"min(a, b, ...)", "min(%, %)"},
                {"AND(a, b)", "AND(%, %)"},
                {"OR(a, b)", "OR(%, %)"},
                {"XOR(a, b)", "XOR(%, %)"},
                {"IF(a, b, c)", "IF(%, %, %)"},
                {"<", " < "},
                {"<=", " <= "},
                {"=", " = "},
                {">=", " >= "},
                {">", " > "},
//                {"Normal(mean, sd)", "Normal(%, %)"},
//                {"Uniform(mean, sd)", "Uniform(%, %)"},
//                {"StudentT(df)", "StudentT(%)"},
//                {"Beta(alpha, beta)", "Beta(%, %)"},
//                {"Gamma(alpha, lambda)", "Gamma(%, %)"},
//                {"ChiSquare(df)", "ChiSquare(%)"},
//                {"Poisson(lambda)", "Poisson(%)"},
//                {"ExponentialPower(tau)", "ExponentialPower(%)"},
//                {"Exponential(lambda)", "Exponential(%)"},
//                {"VonMises(freedom)", "VonMises(%)"},
//                {"Split(a1, b1, a2, b2, ...)", "Split(%, %, %, %)"},
//                {"Discrete(a1, a2, a3, a4, ...)", "Discrete(%, %, %, %)"},
//                {"Indicator(p)", "Indicator(.5)"},
//                {"Mixture(a1, dist1, b1, dist2, ...)", "Mixture(%, Normal(%, %), %, Normal(%, %))"},

                {"Beta(alpha, beta)", "Beta(%, %)"},
                {"Cauchy(median, scale)", "Cauchy(%, %)"},
                {"ChiSquare(df)", "ChiSquare(%)"},
                {"Exponential(mean)", "Exponential(%)"},
                {"FDist(e1, e2)", "FDist(%, %)"},
                {"ExponentialPower(alpha, beta)", "ExponentialPower(%, %)"},
                {"Gamma(shape, scale)", "Gamma(%, %)"},
                {"Gumbel(mu, beta)", "Gumbel(%, %)"},
                {"Indicator(p)", "Indicator(%)"},
                {"Laplace(mu, beta)", "Laplace(%, %)"},
                {"Levy(mu, c)", "Levy(%, %)"},
                {"LogNormal(e1, e2)", "LogNormal(%, %)"},
                {"Nakagami(mu, omega)", "Nakagami(%, %)"},
                {"Normal(mu, sd)", "Normal(%, %)"},
                {"Poisson(lambda)", "Poisson(%)"},
                {"StudentT(dof)", "StudentT(%)"},
                {"Triangular(a, b, c)", "Triangular(%, %, %)"},
                {"Uniform(lower, upper)", "Uniform(%, %)"},
                {"Weibull(alpha, beta)", "Weibull(%, %)"},
                {"Split(a1, b1, a2, b2, ...)", "Split(%, %, %, %)"},
                {"TruncNormal(mean, sd, low, high)", "TruncNormal(%, %, %, %)"},
                {"Discrete(a1, a2, a3, a4, ...)", "Discrete(%, %, %, %)"},
                {"Mixture(a1, dist1, b1, dist2, ...)", "Mixture(%, Normal(%, %), %, Normal(%, %))"},
        };

        List<Node> nodes = semPm.getNodes();

        Map<String, String> expressionsMap = new LinkedHashMap<>();

        for (String[] templateExpression : templateExpressions) {
            expressionsMap.put(templateExpression[0], templateExpression[1]);
        }

        for (Node node : nodes) {
            expressionsMap.put(node.getName(), node.getName());
        }

        for (String[] expression : expressions) {
            expressionsMap.put(expression[0], expression[1]);
        }
        return expressionsMap;
    }

    //===============================================PUBLIC METHODS===========================================//

    private String nextParameterName() {
        Set<String> parameters = getSemPm().getParameters();
        parameters.addAll(this.latestParser.getParameters());

        // Names should start with "1."
        int i = 0;

        loop:
        while (true) {
            String name = "b" + (++i);

            for (String parameter : parameters) {
                if (parameter.equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return "b" + i;
    }

    /**
     * <p>Getter for the field <code>semPm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public GeneralizedSemPm getSemPm() {
        return this.semPm;
    }

    //==============================================PRIVATE METHODS============================================//

    private void setParseText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {

                // Add the text to the document
                expressionTextDoc.remove(0, expressionTextPane.getText().length());
                expressionTextDoc.insertString(0, text, null);
            } catch (BadLocationException e) {
                TetradLogger.getInstance().forceLogMessage(e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void applyChanges() {
        String startWith = this.startsWithField.getText();

        if ("Variables".equals(this.combo.getSelectedItem())) {
            for (Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula;

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                }
            }
        } else if ("Errors".equals(this.combo.getSelectedItem())) {
            for (Node node : getSemPm().getErrorNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula;

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                }
            }
        } else if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
            List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                String formula;

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), null);
                } catch (ParseException e) {
                    continue;
                }

                if (!parameter.startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setParameterExpression(parameter, formula);
                    getSemPm().setParameterExpression(startWith, parameter, formula);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                }
            }
        } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
            List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                String formula;

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), null);
                } catch (ParseException e) {
                    continue;
                }

                if (!parameter.startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setParameterEstimationInitializationExpression(parameter, formula);
                    getSemPm().setParameterEstimationInitializationExpression(startWith, parameter, formula);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

    }

    private void updateEquationsDisplay() {
        this.equationsBox.removeAll();

        if ("Variables".equals(this.combo.getSelectedItem())) {
            for (Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                Box c = Box.createHorizontalBox();
                String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Errors".equals(this.combo.getSelectedItem())) {
            for (Node node : getSemPm().getErrorNodes()) {
                if (node == null) continue;

                Box c = Box.createHorizontalBox();
                String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
            List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                Box c = Box.createHorizontalBox();
                JLabel label = new JLabel(parameter + " ~ " + getSemPm().getParameterExpressionString(parameter));

                c.add(label);
                c.add(Box.createHorizontalGlue());

                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
            List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                Box c = Box.createHorizontalBox();
                JLabel label = new JLabel(parameter + " ~ " +
                                          getSemPm().getParameterEstimationInitializationExpressionString(parameter));

                c.add(label);
                c.add(Box.createHorizontalGlue());

                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        }


        this.equationsBox.setBorder(new EmptyBorder(5, 5, 5, 5));

        this.equationsBox.revalidate();
        this.equationsBox.repaint();
    }

    private void listen() {
        String expressionString = this.expressionTextPane.getText();
        ExpressionParser parser = new ExpressionParser();

        try {
            if (!expressionString.trim().isEmpty()) {
                parser.parseExpression(expressionString);
            }

            StyledDocument document = expressionTextPane.getStyledDocument();
            setDocumentColor(document, "Black");

            this.start = 0;
            this.stringWidth = expressionString.length();

            String startsWithText = this.startsWithField.getValue().trim();

            if (!"Variables".equals(this.combo.getSelectedItem())) {
                if (!"Errors".equals(this.combo.getSelectedItem())) {
                    if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
                        if (!startsWithText.trim().isEmpty()) {
                            getSemPm().setStartsWithParametersTemplate(startsWithText, expressionString);
                        }
                    } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
                        if (!startsWithText.trim().isEmpty()) {
                            getSemPm().setStartsWithParametersEstimationInitializationTemplate(startsWithText, expressionString);
                        }
                    }
                }
            }
        } catch (ParseException e) {
            this.start = e.getErrorOffset();
            StyledDocument document = expressionTextPane.getStyledDocument();
            setDocumentColor(document, "Red");
            this.stringWidth = parser.getNextOffset() - e.getErrorOffset();
        }

        this.latestParser = parser;
    }

    private void setDocumentColor(StyledDocument document, String color) {
        SwingUtilities.invokeLater(() -> {
            if (document != null) {
                document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle(color), true);
            }
        });
    }
}


