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

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Edits an expression for a node in the generalized SEM PM.
 *
 * @author Joseph Ramsey
 */
class GeneralizedExpressionEditor extends JComponent {
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
     * The time that the selectded text should be colored. (Must do this all indirectly using a thread because
     * we cannot listen to the text pane.
     */
    private long recolorTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

    /**
     * The text pane in which parsed text is rendered, typed, and colored.
     */
    private final JTextPane expressionTextPane;

    /**
     * The generalized SEM PM that's being edited.
     */
    private final GeneralizedSemPm semPm;

    /**
     * The latest parser that was used to parse the expression in <code>expressionTextPane</code>. Needed to
     * get the most up to date list of parameters.
     */
    private ExpressionParser latestParser;

    /**
     * The node that's being edited, if a node is being edited; otherwise null.
     */
    private Node node;

    /**
     * The error node for <code>node</code>.
     */
    private final Node errorNode;

    /**
     * The parameter that's being edited, if a parameter is being edited; otherwise null.
     */
    private String parameter;

    /**
     * A display showing the equation or distribution that would result from taking the most recent parsable text
     * from <code>expressionTextPane</code>, writing the variable in front of it with = or ~, and appending the
     * error term if it's not already in the expression.
     */
    private final JTextArea resultTextPane;

    /**
     * The string described for <code>resultTextPane</code>, without the "variable =" or "parameter ~".
     */
    private String expressionString;

    /**
     * If a node is being edited, this is the list of variables other than the node and its parents.
     */
    private final Set<String> otherVariables;

    /**
     * A label listing the parameters referenced by <code>expressionText</code>, according to the latest parser.
     * This list changes as the expression is edited.
     */
    private final JLabel referencedParametersLabel;
    private final JCheckBox errorTermCheckBox;

    //============================================CONSTRUCTORS==================================================//

    /**
     * Constructs an editor for a node in a generalized SEM PM.
     *
     * @param semPm The GeneralizedSemPm that's being edited, containing <code>node</code>.
     * @param node  The node in <code>semPm</code> that's being edited.
     */
    public GeneralizedExpressionEditor(GeneralizedSemPm semPm, Node node) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must be provided.");
        }

        if (node == null) {
            throw new NullPointerException("Node must be provided.");
        }

        if (!semPm.getNodes().contains(node)) {
            throw new IllegalArgumentException("The node provided must be in the graph of the SEM PM.");
        }

        this.semPm = semPm;
        this.node = node;
        this.errorNode = semPm.getErrorNode(node);
        this.expressionString = semPm.getNodeExpressionString(node);

        StyleContext sc = new StyleContext();
        DefaultStyledDocument doc = new DefaultStyledDocument(sc);
        this.expressionTextPane = new JTextPane(doc);
        this.resultTextPane = new JTextArea(semPm.getNodeExpressionString(node));

        try {
            // Add the text to the document
            doc.insertString(0, semPm.getNodeExpressionString(node), null);
        } catch (BadLocationException e) {
            throw new RuntimeException("Couldn't construct editor", e);
        }

        this.otherVariables = new LinkedHashSet<>();

        for (Node _node : semPm.getNodes()) {
            if (semPm.getParents(node).contains(_node)) {
                continue;
            }

            this.otherVariables.add(_node.getName());
        }

        ExpressionParser parser = new ExpressionParser(this.otherVariables, ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);
        latestParser = parser;

        try {
            parser.parseExpression(expressionString);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot parser the stored expression.", e);
        }

        resultTextPane.setEditable(false);
        resultTextPane.setBackground(Color.LIGHT_GRAY);

        Map<String, String> expressionsMap = this.getExpressionMap(semPm, node);
        String[] expressionTokens = this.getExpressionTokens(semPm, node, expressionsMap);
        JComboBox expressionsBox = new JComboBox(expressionTokens);
        expressionsBox.setMaximumSize(expressionsBox.getPreferredSize());

        JButton insertButton = new JButton("Insert");

        insertButton.addActionListener(actionEvent -> {
            String token = (String) expressionsBox.getSelectedItem();
            String signature;

            if ("-New Parameter-".equals(token)) {
                signature = GeneralizedExpressionEditor.this.nextParameterName();
            } else {
                signature = expressionsMap.get(token);
            }

            while (signature.contains("%")) {
                signature = signature.replaceFirst("%", GeneralizedExpressionEditor.this.nextParameterName());
            }

            expressionTextPane.replaceSelection(signature);
        });

        errorTermCheckBox = new JCheckBox("Automatically add error term");
        errorTermCheckBox.setSelected(Preferences.userRoot().getBoolean("automaticallyAddErrorTerms", true));
        errorTermCheckBox.addActionListener(event -> {
            JCheckBox box = (JCheckBox) event.getSource();
            Preferences.userRoot().putBoolean("automaticallyAddErrorTerms", box.isSelected());
        });

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Parents of " + node + ":  " + this.niceParentsList(semPm.getParents(node))));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);
        b.add(Box.createVerticalStrut(5));

        Box b2 = Box.createHorizontalBox();
        referencedParametersLabel = new JLabel("Parameters:  " + this.parameterString(parser));
        b2.add(referencedParametersLabel);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);
        b.add(Box.createVerticalStrut(5));

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Type Expression:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(expressionsBox);
        b4.add(Box.createHorizontalStrut(5));
        b4.add(insertButton);
        b.add(b4);
        b.add(Box.createVerticalStrut(5));

        JScrollPane expressionScroll = new JScrollPane(expressionTextPane);
        expressionScroll.setPreferredSize(new Dimension(500, 50));
        Box b5 = Box.createHorizontalBox();
        b5.add(expressionScroll);
        b.add(b5);
        b.add(Box.createVerticalStrut(5));

        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Result:"));
        b6.add(Box.createHorizontalGlue());
        b.add(b6);
        b.add(Box.createVerticalStrut(5));

        JScrollPane resultScroll = new JScrollPane(resultTextPane);
        resultScroll.setPreferredSize(new Dimension(500, 50));
        Box b7 = Box.createHorizontalBox();
        b7.add(resultScroll);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        Box b8 = Box.createHorizontalBox();
        b8.add(errorTermCheckBox);
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("* Parameter appears in other expressions."));
        b.add(b8);

        this.setLayout(new BorderLayout());
        this.add(b, BorderLayout.CENTER);

        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }

            public void removeUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }

            public void changedUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }
        });

        class ColorThread extends Thread {
            private boolean stop;

            @Override
            public void run() {
                StyledDocument document = (StyledDocument) expressionTextPane.getDocument();

                Style red = expressionTextPane.addStyle("Red", null);
                StyleConstants.setForeground(red, Color.RED);

                Style black = expressionTextPane.addStyle("Black", null);
                StyleConstants.setForeground(black, Color.BLACK);

                while (!stop) {
                    if (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() < recolorTime) {
                        continue;
                    }

                    if (color == Color.RED) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Red"), true);
                    } else if (color == Color.BLACK) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Black"), true);
                    }

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            public void scheduleStop() {
                stop = true;
            }
        }

        ColorThread thread = new ColorThread();
        thread.start();

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent componentEvent) {
                thread.scheduleStop();
            }
        });

        expressionTextPane.setCaretPosition(expressionTextPane.getText().length());

        // When the dialog closes, we want to make sure the expression gets parsed and set.
        this.addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                GeneralizedExpressionEditor.this.listen();
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });

        this.setFocusCycleRoot(true);
        expressionTextPane.grabFocus();
    }

    /**
     * Constructs an editor for a parameter in a generalized SEM PM.
     *
     * @param semPm     The GeneralizedSemPm that's being edited, containing <code>parameter</code>.
     * @param parameter The parameter in <code>semPm</code> that's being edited.
     */
    public GeneralizedExpressionEditor(GeneralizedSemPm semPm, String parameter) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must be provided.");
        }

        if (parameter == null) {
            throw new NullPointerException("Parameter must be provided.");
        }

        if (!semPm.getParameters().contains(parameter)) {
            throw new IllegalArgumentException("The parameter provided must be in the graph of the SEM PM.");
        }

        this.semPm = semPm;
        this.parameter = parameter;
        errorNode = null;
        expressionString = semPm.getParameterExpressionString(parameter);

        StyleContext sc = new StyleContext();
        DefaultStyledDocument doc = new DefaultStyledDocument(sc);
        expressionTextPane = new JTextPane(doc);
        resultTextPane = new JTextArea(semPm.getParameterExpressionString(parameter));

        try {
            try {
                // Add the text to the document
                doc.insertString(0, semPm.getParameterExpressionString(parameter), null);
            } catch (BadLocationException ignored) {
            }
        } catch (Exception e) {
            System.exit(1);
        }

        otherVariables = new LinkedHashSet<>();

        for (Node _node : semPm.getNodes()) {
            otherVariables.add(_node.getName());
        }

        ExpressionParser parser = new ExpressionParser(otherVariables, ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);

        try {
            parser.parseExpression(expressionString);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot parser the stored expression.", e);
        }

        resultTextPane.setEditable(false);
        resultTextPane.setBackground(Color.LIGHT_GRAY);

        Map<String, String> expressionsMap = this.getExpressionMap(semPm, node);
        String[] expressionTokens = this.getExpressionTokens(semPm, node, expressionsMap);
        JComboBox expressionsBox = new JComboBox(expressionTokens);
        expressionsBox.setMaximumSize(expressionsBox.getPreferredSize());

        JButton insertButton = new JButton("Insert");

        insertButton.addActionListener(actionEvent -> {
            String token = (String) expressionsBox.getSelectedItem();
            String signature;

            if ("-New Parameter-".equals(token)) {
                signature = GeneralizedExpressionEditor.this.nextParameterName();
            } else {
                signature = expressionsMap.get(token);
            }

            while (signature.contains("%")) {
                signature = signature.replaceFirst("%", GeneralizedExpressionEditor.this.nextParameterName());
            }

            expressionTextPane.replaceSelection(signature);
        });

        errorTermCheckBox = new JCheckBox("Automatically add error term");
        errorTermCheckBox.setSelected(Preferences.userRoot().getBoolean("automaticallyAddErrorTerms", true));

        Box b = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        referencedParametersLabel = new JLabel("Parameters:  " + this.parameterString(parser));
        b2.add(referencedParametersLabel);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);
        b.add(Box.createVerticalStrut(5));

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Type Expression:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(expressionsBox);
        b4.add(Box.createHorizontalStrut(5));
        b4.add(insertButton);
        b.add(b4);
        b.add(Box.createVerticalStrut(5));

        JScrollPane expressionScroll = new JScrollPane(expressionTextPane);
        expressionScroll.setPreferredSize(new Dimension(500, 50));
        Box b5 = Box.createHorizontalBox();
        b5.add(expressionScroll);
        b.add(b5);
        b.add(Box.createVerticalStrut(5));

        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Result:"));
        b6.add(Box.createHorizontalGlue());
        b.add(b6);
        b.add(Box.createVerticalStrut(5));

        JScrollPane resultScroll = new JScrollPane(resultTextPane);
        resultScroll.setPreferredSize(new Dimension(500, 50));
        Box b7 = Box.createHorizontalBox();
        b7.add(resultScroll);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        Box b8 = Box.createHorizontalBox();
        b8.add(errorTermCheckBox);
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("* Parameter appears in other expressions."));
        b.add(b8);

        this.setLayout(new BorderLayout());
        this.add(b, BorderLayout.CENTER);

        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }

            public void removeUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }

            public void changedUpdate(DocumentEvent documentEvent) {
                GeneralizedExpressionEditor.this.listen();
            }
        });

        class ColorThread extends Thread {
            private boolean stop;

            @Override
            public void run() {
                StyledDocument document = (StyledDocument) expressionTextPane.getDocument();

                Style red = expressionTextPane.addStyle("Red", null);
                StyleConstants.setForeground(red, Color.RED);

                Style black = expressionTextPane.addStyle("Black", null);
                StyleConstants.setForeground(black, Color.BLACK);

                while (!stop) {
                    if (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() < recolorTime) {
                        continue;
                    }

                    if (color == Color.RED) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Red"), true);
                    } else if (color == Color.BLACK) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Black"), true);
                    }

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            public void scheduleStop() {
                stop = true;
            }
        }

        ColorThread thread = new ColorThread();
        thread.start();

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent componentEvent) {
                thread.scheduleStop();
            }
        });

        this.setFocusCycleRoot(true);
        expressionTextPane.grabFocus();
    }

    //==================================================PUBLIC METHODS==========================================//

    /**
     * @return the expression string (that is, the edited string, with error term appended if necessary, without
     * "variable = " or "parameter ~". This is the final product of the editing.
     */
    public String getExpressionString() {
        return expressionString;
    }

    /**
     * @return the next parameter in the sequence base1, base2,... that's not already being used by the SEM PM.
     */
    private String nextParameterName() {
        Set<String> parameters = semPm.getParameters();
        parameters.addAll(latestParser.getParameters());

        System.out.println("*" + parameters);
        System.out.println(latestParser.getParameters());

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

    //==================================================PRIVATE METHODS=========================================//

    private String niceParentsList(List<Node> nodes) {
        List<String> nodeNames = new ArrayList<>();

        for (Node node : nodes) {
            nodeNames.add(node.getName());
        }

        List<String> _nodeNames = new ArrayList<>(nodeNames);

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < _nodeNames.size(); i++) {
            buf.append(_nodeNames.get(i));

            if (i < nodeNames.size() - 1) {
                buf.append(", ");
            }
        }

        return buf.toString();
    }

    private void listen() {
        String expressionString = expressionTextPane.getText();
        String valueExpressionString;

        ExpressionParser parser = new ExpressionParser(otherVariables, ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);

        try {
            if (!"".equals(expressionString)) {
                parser.parseExpression(expressionString);
            }
            this.color = Color.BLACK;
            this.start = 0;
            this.stringWidth = expressionString.length();
            this.recolorTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            valueExpressionString = expressionString;
        } catch (ParseException e) {
            this.color = Color.RED;
            this.start = e.getErrorOffset();
            this.stringWidth = parser.getNextOffset() - e.getErrorOffset();
            this.recolorTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            valueExpressionString = null;
        }

        if (valueExpressionString != null) {
            String formula = this.expressionTextPane.getText();

            if (this.node != null) {
                if (this.node.getNodeType() != NodeType.ERROR && !formula.contains(this.errorNode.getName())
                        && this.errorTermCheckBox.isSelected()) {
                    if (!formula.trim().endsWith("+") && !"".equals(formula)) {
                        formula += " + ";
                    }

                    formula += this.errorNode.getName();
                }

                this.expressionString = formula;

                if (this.node.getNodeType() == NodeType.ERROR) {
                    this.resultTextPane.setText(this.node + " ~ " + formula);
                } else {
                    this.resultTextPane.setText(this.node + " = " + formula);
                }
            } else if (this.parameter != null) {
                this.expressionString = formula;
                this.resultTextPane.setText(this.parameter + " ~ " + formula);
            }

            this.referencedParametersLabel.setText("Parameters:  " + parameterString(parser));
        }

        this.latestParser = parser;
    }

    private String parameterString(ExpressionParser parser) {
        Set<String> parameters = new LinkedHashSet<>(parser.getParameters());

        for (Node _node : this.semPm.getNodes()) {
            parameters.remove(_node.getName());
        }

        List<String> parametersList = new ArrayList<>(parameters);
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < parametersList.size(); i++) {
            buf.append(parametersList.get(i));

            Set<Node> referencingNodes = this.semPm.getReferencingNodes(parametersList.get(i));
            referencingNodes.remove(this.node);

            if (referencingNodes.size() > 0) {
                buf.append("*");
            }

            if (i < parametersList.size() - 1) {
                buf.append(", ");
            }
        }

        return buf.toString();
    }

    private String[] getExpressionTokens(GeneralizedSemPm semPm, Node node, Map<String, String> expressionsMap) {
        List<String> _tokens = new ArrayList<>(expressionsMap.keySet());

        if (node != null) {
            _tokens.add(semPm.getParents(node).size(), "-New Parameter-");
        }

        String[] expressionTokens = new String[_tokens.size()];
        int i = -1;

        for (String token : _tokens) {
            expressionTokens[++i] = token;
        }
        return expressionTokens;
    }

    private Map<String, String> getExpressionMap(GeneralizedSemPm semPm, Node node) {
        // These are the expressions the user can choose from. The display form is on the left, and the template
        // form is on the. Obviously you use a % for a new parameter. In case you want to change it.
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
                {"Normal(mean, sd)", "Normal(%, %)"},
                {"TruncNormal(mean, sd, low, high)", "TruncNormal(%, %, %, %)"},
                {"Uniform(low, high)", "Uniform(%, %)"},
                {"StudentT(df)", "StudentT(%)"},
                {"Beta(alpha, beta)", "Beta(%, %)"},
                {"Gamma(alpha, lambda)", "Gamma(%, %)"},
                {"ChiSquare(df)", "ChiSquare(%)"},
                {"Hyperbolic(alpha, beta)", "Hyperbolic(%, %)"},
                {"Poisson(lambda)", "Poisson(%)"},
                {"ExponentialPower(tau)", "ExponentialPower(%)"},
                {"Exponential(lambda)", "ExponentialLambda(%)"},
                {"VonMises(freedom)", "VonMises(%)"},
                {"Split(a1, b1, a2, b2, ...)", "Split(%, %, %, %)"},
                {"Discrete(a1, a2, a3, a4, ...)", "Discrete(%, %, %, %)"},
                {"Indicator(p)", "Indicator(.5)"},
                {"Mixture(a1, dist1, b1, dist2, ...)", "Mixture(%, Normal(%, %), %, Normal(%, %))"},
        };

        Map<String, String> expressionsMap = new LinkedHashMap<>();

        if (node != null) {
            List<Node> parents = semPm.getParents(node);

            for (Node parent : parents) {
                expressionsMap.put(parent.getName(), parent.getName());
            }
        }

        for (String[] expression : expressions) {
            expressionsMap.put(expression[0], expression[1]);
        }

        return expressionsMap;
    }
}



