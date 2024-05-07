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
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.*;

/**
 * Edits an expression for a node in the generalized SEM PM.
 *
 * @author josephramsey
 */
class GeneralizedExpressionParameterizer extends JComponent {
    private final Node node;
    private final GeneralizedSemPm semPm;

    private final Map<String, Double> substitutedValues;
    private final JTextArea resultTextPane;

    /**
     * <p>Constructor for GeneralizedExpressionParameterizer.</p>
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     * @param node  a {@link edu.cmu.tetrad.graph.Node} object
     */
    public GeneralizedExpressionParameterizer(GeneralizedSemIm semIm, Node node) {
        if (semIm == null) {
            throw new NullPointerException("SEM IM must be provided.");
        }

        if (node == null) {
            throw new NullPointerException("Node must be provided.");
        }

        if (!semIm.getSemPm().getNodes().contains(node)) {
            throw new IllegalArgumentException("The node provided must be in the graph of the SEM PM.");
        }

        this.semPm = semIm.getSemPm();
        this.node = node;

        String expressionString1 = this.semPm.getNodeExpressionString(node);

        Set<String> otherVariables = new LinkedHashSet<>();

        for (Node _node : this.semPm.getNodes()) {
            if (this.semPm.getParents(node).contains(_node)) {
                continue;
            }

            otherVariables.add(_node.getName());
        }

        ExpressionParser parser = new ExpressionParser(otherVariables, ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);

        try {
            parser.parseExpression(expressionString1);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot parser the stored expression.", e);
        }


        String expressionString = this.semPm.getNodeExpressionString(node);
        Set<String> referencedParameters = this.semPm.getReferencedParameters(node);

        this.substitutedValues = new HashMap<>();

        for (String parameter : referencedParameters) {
            this.substitutedValues.put(parameter, semIm.getParameterValue(parameter));
        }

        String substitutedString = semIm.getNodeSubstitutedString(node, this.substitutedValues);

        if (node.getNodeType() == NodeType.ERROR) {
            this.resultTextPane = new JTextArea(node + " ~ " + substitutedString);
        } else {
            this.resultTextPane = new JTextArea(node + " = " + substitutedString);
        }

        this.resultTextPane.setEditable(false);
        this.resultTextPane.setBackground(Color.LIGHT_GRAY);

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();

        if (node.getNodeType() == NodeType.ERROR) {
            b1.add(new JLabel(node + " ~ " + expressionString));
        } else {
            b1.add(new JLabel(node + " = " + expressionString));
        }
        b1.add(Box.createHorizontalGlue());
        b.add(b1);
        b.add(Box.createVerticalStrut(5));

        Box b2 = Box.createHorizontalBox();
        String parameterString = parameterString(parser);

        if (parameterString.isEmpty()) parameterString = "--NONE--";

        JLabel referencedParametersLabel = new JLabel("Parameters:  " + parameterString);
        b2.add(referencedParametersLabel);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);
        b.add(Box.createVerticalStrut(5));

        // Need to keep these in a particular order.
        class MyTextField extends DoubleTextField {
            private final String parameter;

            public MyTextField(String parameter, double value, int width, NumberFormat format) {
                super(value, width, format);
                this.parameter = parameter;
            }

            public String getParameter() {
                return this.parameter;
            }
        }

        for (String parameter : referencedParameters) {
            Box c = Box.createHorizontalBox();
            c.add(new JLabel(parameter + " = "));
            MyTextField field = new MyTextField(parameter, semIm.getParameterValue(parameter), 8,
                    NumberFormatUtil.getInstance().getNumberFormat());

            field.setFilter((value, oldValue) -> {
                GeneralizedExpressionParameterizer.this.substitutedValues.put(field.getParameter(), value);
                GeneralizedExpressionParameterizer.this.resultTextPane.setText(node + " = " + semIm.getNodeSubstitutedString(node,
                        GeneralizedExpressionParameterizer.this.substitutedValues));
                return value;
            });

            c.add(field);
            c.add(Box.createHorizontalGlue());
            b.add(c);
            b.add(Box.createVerticalStrut(5));
        }

        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Result:"));
        b6.add(Box.createHorizontalGlue());
        b.add(b6);
        b.add(Box.createVerticalStrut(5));

        JScrollPane resultScroll = new JScrollPane(this.resultTextPane);
        resultScroll.setPreferredSize(new Dimension(500, 50));
        Box b7 = Box.createHorizontalBox();
        b7.add(resultScroll);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("* Parameter appears in other expressions."));
        b.add(b8);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    /**
     * <p>getParameterValues.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Double> getParameterValues() {
        return this.substitutedValues;
    }

    private String parameterString(ExpressionParser parser) {
        Result result = getResult(parser);

        for (int i = 0; i < result.parametersList().size(); i++) {
            result.buf().append(result.parametersList().get(i));

            Set<Node> referencingNodes = this.semPm.getReferencingNodes(result.parametersList().get(i));
            referencingNodes.remove(this.node);

            if (!referencingNodes.isEmpty()) {
                result.buf().append("*");
            }

            if (i < result.parametersList().size() - 1) {
                result.buf().append(", ");
            }
        }

        return result.buf().toString();
    }

    @NotNull
    private Result getResult(ExpressionParser parser) {
        Set<String> parameters = new LinkedHashSet<>(parser.getParameters());

        for (Node _node : this.semPm.getNodes()) {
            parameters.remove(_node.getName());
        }

        List<String> parametersList = new ArrayList<>(parameters);
        StringBuilder buf = new StringBuilder();

        return new Result(parametersList, buf);
    }

    private record Result(List<String> parametersList, StringBuilder buf) {
    }
}


