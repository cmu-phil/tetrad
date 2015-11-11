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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;


/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemImParamsEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private GeneralizedSemIm semIm;


    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;

    /**
     * The PM being edited.
     */
    private GeneralizedSemPm semPm;

    /**
     * The set of launched editors--or rather, the nodes for the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<Object, EditorWindow>();

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemImParamsEditor(GeneralizedSemIm semIm, Map<Object, EditorWindow> launchedEditors) {
        this.semIm = semIm;
        this.launchedEditors = launchedEditors;
        this.semPm = semIm.getSemPm();
        freshenDisplay();
    }

    //========================PRIVATE PROTECTED METHODS======================//

    public void freshenDisplay() {
        removeAll();
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(initialValuesPane());
        scroll.setPreferredSize(new Dimension(450, 450));
        add(scroll, BorderLayout.CENTER);
    }

    private JComponent initialValuesPane() {
        Box b = Box.createVerticalBox();

        java.util.List<String> parameters = new ArrayList<String>(semPm().getParameters());
        Collections.sort(parameters);

        // Need to keep these in a particular order.
        class MyTextField extends DoubleTextField {
            private String parameter;

            public MyTextField(String parameter, double value, int width, NumberFormat format) {
                super(value, width, format);
                this.parameter = parameter;
            }

            public String getParameter() {
                return this.parameter;
            }
        }

        List<String> _parameters = new ArrayList(semPm.getParameters());
        Collections.sort(_parameters);

        for (String parameter : _parameters) {
            final String _parameter = parameter;

            Box c = Box.createHorizontalBox();
            c.add(new JLabel(parameter + " = "));
            final MyTextField field = new MyTextField(parameter, semIm.getParameterValue(parameter), 8,
                    NumberFormatUtil.getInstance().getNumberFormat());

            field.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    semIm.setParameterValue(_parameter, value);
                    return value;
                }
            });

            c.add(field);
            c.add(Box.createHorizontalGlue());
            b.add(c);
            b.add(Box.createVerticalStrut(5));
        }

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        return b;
    }

    private GeneralizedSemPm semPm() {
        return this.semPm;
    }

    private Graph graph() {
        return semPm().getGraph();
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        this.savedTooltipDelay = savedTooltipDelay;
    }
}


