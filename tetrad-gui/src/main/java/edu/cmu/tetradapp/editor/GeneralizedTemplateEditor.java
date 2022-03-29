///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetradapp.util.StringTextField;

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
import java.text.ParseException;
import java.util.List;
import java.util.*;

/**
 * Edits an expression for a node in the generalized SEM PM.
 *
 * @author Joseph Ramsey
 */
class GeneralizedTemplateEditor extends JComponent {

    private static final long serialVersionUID = 3455126100397759982L;

    /**
     * The color that selected text is being rendered. Either black or red.
     */
    private Color color = Color.BLACK;

    /**
     * The start index of selected text.
     */
    private int start;

    /**
     * The width of the selected text.
     */
    private int stringWidth;

    /**
     * The time that the selectded text should be colored. (Must do this all indirectly using a thread because we cannot
     * saveTemplate to the text pane.
     */
    private long recolorTime = System.currentTimeMillis();

    /**
     * The text pane in which parsed text is rendered, typed, and colored.
     */
    private final JTextPane expressionTextPane;

    /**
     * The document for the expression text pane that holds the parsed text.
     */
    private final DefaultStyledDocument expressionTextDoc;

    /**
     * g The generalized SEM PM that's being edited.
     */
    private final GeneralizedSemPm semPm;

    /**
     * The latest parser that was used to parse the expression in <code>expressionTextPane</code>. Needed to get the
     * most up to date list of parameters.
     */
    private ExpressionParser latestParser;

    /**
     * The box in which labels for each equation are rendered.
     */
    private Box equationsBox = Box.createVerticalBox();

    /**
     *
     */
    private final JComboBox<String> combo = new JComboBox<>();

//    /**
//     * If this is selected, then variables are listed in the box. Exclusive with <code>errorsCheckBox</code> and
//     * <code>parametersCheckBox</code>.
//     */
//    private JRadioButton variablesButton;
//
//    /**
//     * If this is selected, then error terms are listed in the box. Exclusive with <code>variablesCheckBox</code> and
//     * <code>parametersCheckBox</code>.
//     */
//    private JRadioButton errorsButton;
//
//    /**
//     * If this is selected, then parameters are listed in the box. Exclusive with <code>variablesCheckBox</code> and
//     * <code>errorsCheckBox</code>.
//     */
//    private JRadioButton parametersButton;

    /**
     * The field that indicates what names of variables or parameters should start with in order to be changed.
     */
    private final StringTextField startsWithField;

    //=============================================CONSTRUCTORS================================================//

    /**
     * Constructs the editor.
     *
     * @param semPm The GeneralizedSemPm that's being edited. The edits will be made to a copy of this PM.
     */
    public GeneralizedTemplateEditor(final GeneralizedSemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must be provided.");
        }

        this.semPm = new GeneralizedSemPm(semPm);

        this.equationsBox = Box.createVerticalBox();
        final JScrollPane scroll = new JScrollPane(this.equationsBox);
        scroll.setPreferredSize(new Dimension(400, 300));

        final StyleContext sc = new StyleContext();
        this.expressionTextDoc = new DefaultStyledDocument(sc);
        this.expressionTextPane = new JTextPane(this.expressionTextDoc);

        setParseText(semPm.getVariablesTemplate());

        final ExpressionParser parser = new ExpressionParser(); //new ArrayList<String>(), ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);
        this.latestParser = parser;

        try {
            final String template = getSemPm().getVariablesTemplate();
            parser.parseExpression(template);
        } catch (final ParseException e) {
            throw new RuntimeException("Cannot parse the stored expression.", e);
        }

        final Map<String, String> expressionsMap = getExpressionsMap(semPm);
        final String[] expressionTokens = getExpressionTokens(expressionsMap);
        final JComboBox<String> expressionsBox = new JComboBox<>(expressionTokens);
        expressionsBox.setMaximumSize(expressionsBox.getPreferredSize());

        final JButton insertButton = new JButton("Insert");

        insertButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                final String token = (String) expressionsBox.getSelectedItem();
                String signature;

                if ("-New Parameter-".equals(token)) {
                    signature = nextParameterName("b");
                } else {
                    signature = expressionsMap.get(token);
                }

                while (signature.contains("%")) {
                    signature = signature.replaceFirst("%", nextParameterName("b"));
                }

                GeneralizedTemplateEditor.this.expressionTextPane.replaceSelection(signature);
            }
        });

        this.combo.addItem("Variables");
        this.combo.addItem("Errors");
        this.combo.addItem("Parameter Initializations");
        this.combo.addItem("Estimation Starting Values");

        this.combo.setSelectedItem("Variables");

        updateEquationsDisplay();

        this.combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final JComboBox source = (JComboBox) e.getSource();
                final String item = (String) source.getSelectedItem();

                if ("Variables".equals(item)) {
                    final String variablesTemplate = getSemPm().getVariablesTemplate();
                    setParseText(variablesTemplate);
                    updateEquationsDisplay();
                } else if ("Errors".equals(item)) {
                    final String errorsTemplate = getSemPm().getErrorsTemplate();
                    setParseText(errorsTemplate);
                    updateEquationsDisplay();
                } else if ("Parameter Initializations".equals(item)) {
                    final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterTemplate(startsWith);
                    if (template == null) {
                        final String parametersTemplate = getSemPm().getParametersTemplate();
                        template = parametersTemplate;
                    }

                    setParseText(template);
                    updateEquationsDisplay();
                } else if ("Estimation Starting Values".equals(item)) {
                    final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterEstimationInitializatonTemplate(startsWith);
                    if (template == null) {
                        final String parametersTemplate = getSemPm().getParametersEstimationInitializationTemplate();
                        template = parametersTemplate;
                    }

                    setParseText(template);
                    updateEquationsDisplay();
                } else {
                    throw new IllegalStateException("Unrecognized Combo Box Item: " + item);
                }
            }
        });

        final JButton applyButton = new JButton("APPLY");

        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                applyChanges();
                updateEquationsDisplay();

                if ("Variables".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                    final String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                    try {
                        getSemPm().setVariablesTemplate(template);
                    } catch (final ParseException e) {
                        //
                    }
                } else if ("Errors".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                    final String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                    try {
                        getSemPm().setErrorsTemplate(template);
                    } catch (final ParseException e) {
                        //
                    }
                } else if ("Parameter Initializations".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                    final String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                    try {
                        getSemPm().setParametersTemplate(template);

                        final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();

                        getSemPm().setStartsWithParametersTemplate(startsWith, template);
                    } catch (final ParseException e) {
                        //
                    }
                } else if ("Estimation Starting Values".equals(GeneralizedTemplateEditor.this.combo.getSelectedItem())) {
                    final String template = GeneralizedTemplateEditor.this.expressionTextPane.getText();

                    try {
                        getSemPm().setParametersEstimationInitializationTemplate(template);

                        final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();

                        getSemPm().setStartsWithParametersEstimationInitializaationTemplate(startsWith, template);
                    } catch (final ParseException e) {
                        //
                    }
                }

            }
        });

        final JButton saveTemplate = new JButton("Save Template");

        saveTemplate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                listen();
            }
        });

        this.startsWithField = new StringTextField("", 6);

        this.startsWithField.setFilter(new StringTextField.Filter() {
            public String filter(final String value, final String oldValue) {
                final String item = (String) GeneralizedTemplateEditor.this.combo.getSelectedItem();

                if ("Variables".equals(item)) {
                    final String variablesTemplate = getSemPm().getVariablesTemplate();
                    setParseText(variablesTemplate);
//                    updateEquationsDisplay();
                } else if ("Errors".equals(item)) {
                    final String errorsTemplate = getSemPm().getErrorsTemplate();
                    setParseText(errorsTemplate);
//                    updateEquationsDisplay();
                } else if ("Parameter Initializations".equals(item)) {
                    final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterTemplate(startsWith);
                    if (template == null) {
                        final String parametersTemplate = getSemPm().getParametersTemplate();
                        template = parametersTemplate;
                    }

                    setParseText(template);
//                    updateEquationsDisplay();
                } else if ("Estimation Starting Values".equals(item)) {
                    final String startsWith = GeneralizedTemplateEditor.this.startsWithField.getText();
                    String template = getSemPm().getStartsWithParameterEstimationInitializatonTemplate(startsWith);
                    if (template == null) {
                        final String parametersTemplate = getSemPm().getParametersEstimationInitializationTemplate();
                        template = parametersTemplate;
                    }

                    setParseText(template);
//                    updateEquationsDisplay();
                } else {
                    throw new IllegalStateException("Unrecognized Combo Box Item: " + item);
                }

                return value;
            }
        });

        final Box b = Box.createVerticalBox();

        final Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Type Template:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(expressionsBox);
        b4.add(Box.createHorizontalStrut(5));
        b4.add(insertButton);
        b.add(b4);
        b.add(Box.createVerticalStrut(5));

        final JScrollPane expressionScroll = new JScrollPane(this.expressionTextPane);
        expressionScroll.setPreferredSize(new Dimension(500, 50));
        final Box b5 = Box.createHorizontalBox();
        b5.add(expressionScroll);
        b.add(b5);
        b.add(Box.createVerticalStrut(5));

        final JPanel applyToPanel = new JPanel();
        applyToPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        final Box b7 = Box.createHorizontalBox();
        b7.add(new JLabel("Apply to: "));
        b7.add(this.combo);
//        b7.add(variablesButton);
//        b7.add(errorsButton);
//        b7.add(parametersButton);
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("Starts with: "));
        b7.add(this.startsWithField);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        b.add(new JScrollPane(scroll));

        final Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(saveTemplate);
        b8.add(applyButton);
        b.add(b8);
        b.add(Box.createVerticalStrut(5));

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);

        class ColorThread extends Thread {
            private boolean stop;

            @Override
            public void run() {
                final StyledDocument document = (StyledDocument) GeneralizedTemplateEditor.this.expressionTextPane.getDocument();

                final Style red = GeneralizedTemplateEditor.this.expressionTextPane.addStyle("Red", null);
                StyleConstants.setForeground(red, Color.RED);

                final Style black = GeneralizedTemplateEditor.this.expressionTextPane.addStyle("Black", null);
                StyleConstants.setForeground(black, Color.BLACK);

                while (!this.stop) {
                    if (System.currentTimeMillis() < GeneralizedTemplateEditor.this.recolorTime) {
                        continue;
                    }

                    if (GeneralizedTemplateEditor.this.color.equals(Color.RED)) {
                        document.setCharacterAttributes(GeneralizedTemplateEditor.this.start, GeneralizedTemplateEditor.this.stringWidth, GeneralizedTemplateEditor.this.expressionTextPane.getStyle("Red"), true);
                    } else if (GeneralizedTemplateEditor.this.color == Color.BLACK) {
                        document.setCharacterAttributes(GeneralizedTemplateEditor.this.start, GeneralizedTemplateEditor.this.stringWidth, GeneralizedTemplateEditor.this.expressionTextPane.getStyle("Black"), true);
                    }

                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            public void scheduleStop() {
                this.stop = true;
            }
        }

        this.expressionTextDoc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(final DocumentEvent documentEvent) {
                listen();
            }

            public void removeUpdate(final DocumentEvent documentEvent) {
                listen();
            }

            public void changedUpdate(final DocumentEvent documentEvent) {
                listen();
            }
        });

        final ColorThread thread = new ColorThread();
        thread.start();

        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(final AncestorEvent event) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void ancestorRemoved(final AncestorEvent event) {
                thread.scheduleStop();
            }

            public void ancestorMoved(final AncestorEvent event) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });

        this.expressionTextPane.setCaretPosition(this.expressionTextPane.getText().length());

        // When the dialog closes, we want to make sure the expression gets parsed and set.
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(final AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(final AncestorEvent ancestorEvent) {
                listen();
            }

            public void ancestorMoved(final AncestorEvent ancestorEvent) {
            }
        });

        setFocusCycleRoot(true);
    }

    //==================================================PRIVATE METHODS=========================================//

    private String[] getExpressionTokens(final Map<String, String> expressionsMap) {
        final String[] expressionTokens = new String[expressionsMap.keySet().size()];
        int i = -1;

        for (final String token : expressionsMap.keySet()) {
            expressionTokens[++i] = token;
        }
        return expressionTokens;
    }

    private Map<String, String> getExpressionsMap(final GeneralizedSemPm semPm) {
        final String[][] templateExpressions = {
                {"NEW", "NEW(b)"},
                {"new", "new(b)"},
                {"TSUM", "TSUM($)"},
                {"tsum", "tsum($)"},
                {"TPROD", "TPROD($)"},
                {"tprod", "tprod($)"},
        };

        // These are the expressions the user can choose from. The display form is on the left, and the template
        // form is on the. Obviously you use a % for a new parameter. In case you want to change it.
        final String[][] expressions = {
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
                {"ExponentialDist(mean)", "ExponentialDist(%)"},
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

        final List<Node> nodes = semPm.getNodes();

        final Map<String, String> expressionsMap = new LinkedHashMap<>();

        for (int i = 0; i < templateExpressions.length; i++) {
            expressionsMap.put(templateExpressions[i][0], templateExpressions[i][1]);
        }

        for (int i = 0; i < nodes.size(); i++) {
            expressionsMap.put(nodes.get(i).getName(), nodes.get(i).getName());
        }

        for (int i = 0; i < expressions.length; i++) {
            expressionsMap.put(expressions[i][0], expressions[i][1]);
        }
        return expressionsMap;
    }

    //===============================================PUBLIC METHODS===========================================//

    private String nextParameterName(final String base) {
        final Set<String> parameters = getSemPm().getParameters();
        parameters.addAll(this.latestParser.getParameters());

        // Names should start with "1."
        int i = 0;

        loop:
        while (true) {
            final String name = base + (++i);

            for (final String parameter : parameters) {
                if (parameter.equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return base + i;
    }

    public GeneralizedSemPm getSemPm() {
        return this.semPm;
    }

    //==============================================PRIVATE METHODS============================================//

    private void setParseText(final String text) {
        try {

            // Add the text to the document
            this.expressionTextDoc.remove(0, this.expressionTextPane.getText().length());
            this.expressionTextDoc.insertString(0, text, null);
        } catch (final BadLocationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void applyChanges() {
        final List<Node> nodes = new ArrayList<>();
        final List<String> parameters = new ArrayList<>();
        final String startWith = this.startsWithField.getText();

        if ("Variables".equals(this.combo.getSelectedItem())) {
            for (final Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (final ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (final ParseException e) {
                    continue;
                }

                nodes.add(node);
            }
        } else if ("Errors".equals(this.combo.getSelectedItem())) {
            for (final Node node : getSemPm().getErrorNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (final ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (final ParseException e) {
                    continue;
                }

                nodes.add(node);
            }
        } else if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
            final List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (final String parameter : _parameters) {
                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), null);
                } catch (final ParseException e) {
                    continue;
                }

                if (!parameter.startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setParameterExpression(parameter, formula);
                    getSemPm().setParameterExpression(startWith, parameter, formula);
                } catch (final ParseException e) {
                    continue;
                }

                parameters.add(parameter);
            }
        } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
            final List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (final String parameter : _parameters) {
                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(this.expressionTextPane.getText(),
                            getSemPm(), null);
                } catch (final ParseException e) {
                    continue;
                }

                if (!parameter.startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setParameterEstimationInitializationExpression(parameter, formula);
                    getSemPm().setParameterEstimationInitializationExpression(startWith, parameter, formula);
                } catch (final ParseException e) {
                    continue;
                }

                parameters.add(parameter);
            }
        }

    }

    private void updateEquationsDisplay() {
        this.equationsBox.removeAll();

        if ("Variables".equals(this.combo.getSelectedItem())) {
            for (final Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                final Box c = Box.createHorizontalBox();
                final String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                final JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Errors".equals(this.combo.getSelectedItem())) {
            for (final Node node : getSemPm().getErrorNodes()) {
                if (node == null) continue;

                final Box c = Box.createHorizontalBox();
                final String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                final JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
            final List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (final String parameter : _parameters) {
                final Box c = Box.createHorizontalBox();
                final JLabel label = new JLabel(parameter + " ~ " + getSemPm().getParameterExpressionString(parameter));

                c.add(label);
                c.add(Box.createHorizontalGlue());

                this.equationsBox.add(c);
                this.equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
            final List<String> _parameters = new ArrayList<>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (final String parameter : _parameters) {
                final Box c = Box.createHorizontalBox();
                final JLabel label = new JLabel(parameter + " ~ " +
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
        final String expressionString = this.expressionTextPane.getText();

        final ExpressionParser parser = new ExpressionParser();

        try {
            if (!"".equals(expressionString.trim())) {
                parser.parseExpression(expressionString);
            }

            this.color = Color.BLACK;
            this.start = 0;
            this.stringWidth = expressionString.length();
            this.recolorTime = System.currentTimeMillis();

            final String startsWithText = this.startsWithField.getValue().trim();

            if ("Variables".equals(this.combo.getSelectedItem())) {
                if (!"".equals(expressionString.trim())) {
//                    getSemPm().setVariablesTemplate(expressionString);
                }
            } else if ("Errors".equals(this.combo.getSelectedItem())) {
                if (!"".equals(expressionString.trim())) {
//                    getSemPm().setErrorsTemplate(expressionString);
                }
            } else if ("Parameter Initializations".equals(this.combo.getSelectedItem())) {
                if (!"".equals(startsWithText.trim())) {
                    getSemPm().setStartsWithParametersTemplate(startsWithText, expressionString);
                }
//                getSemPm().setParametersTemplate(expressionString);
            } else if ("Estimation Starting Values".equals(this.combo.getSelectedItem())) {
                if (!"".equals(startsWithText.trim())) {
                    getSemPm().setStartsWithParametersEstimationInitializaationTemplate(startsWithText, expressionString);
                }
//                getSemPm().setParametersEstimationInitializationTemplate(expressionString);
            }
        } catch (final ParseException e) {
            this.color = Color.RED;
            this.start = e.getErrorOffset();
            this.stringWidth = parser.getNextOffset() - e.getErrorOffset();
            this.recolorTime = System.currentTimeMillis();
        }

        this.latestParser = parser;
    }
}


