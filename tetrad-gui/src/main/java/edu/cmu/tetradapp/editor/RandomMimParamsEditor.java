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

import edu.cmu.tetrad.graph.DataGraphUtilsFlexMim;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

class RandomMimParamsEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = -1478898170626611725L;

    // ---- Parameter keys (centralized) -------------------------------------
    private static final String K_NUM_STRUCTURAL_EDGES = "mimNumStructuralEdges";
    private static final String K_NUM_STRUCTURAL_NODES = "mimNumStructuralNodes";
    private static final String K_LATENT_GROUP_SPECS = "mimLatentGroupSpecs";
    private static final String K_LATENT_MEASURED_IMPURE_PARENTS = "mimLatentMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_PARENTS = "mimMeasuredMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_ASSOC = "mimMeasuredMeasuredImpureAssociations";

    // ---- Defaults (kept close to keys) ------------------------------------
    private static final int D_NUM_STRUCTURAL_EDGES = 3;
    private static final int D_NUM_STRUCTURAL_NODES = 3;
    private static final String D_LATENT_GROUP_SPECS = "5:5(1)";
    private static final int D_ZERO = 0;

    public RandomMimParamsEditor(Parameters parameters) {
        setLayout(new BorderLayout());

        // Structural edges (clamped to simple DAG max given current node count)
        IntTextField numStructuralEdges = new IntTextField(
                parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES), 4
        );
        numStructuralEdges.setFilter((value, oldValue) -> {
            try {
                int n = Math.max(0, parameters.getInt(K_NUM_STRUCTURAL_NODES, D_NUM_STRUCTURAL_NODES));
                int maxEdges = n <= 1 ? 0 : (n * (n - 1)) / 2;
                int clamped = Math.min(Math.max(0, value), maxEdges);
                parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                return clamped;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Latent group specs (validated by parser)
        StringTextField latentGroupSpecs = new StringTextField(
                parameters.getString(K_LATENT_GROUP_SPECS, D_LATENT_GROUP_SPECS), 16
        );
        latentGroupSpecs.setFilter((value, oldValue) -> {
            try {
                // Validate before saving
                DataGraphUtilsFlexMim.parseLatentGroupSpecs(value);
                parameters.set(K_LATENT_GROUP_SPECS, value);
                return value;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Impure edge counts (all must be >= 0)
        IntTextField numLatentMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_LATENT_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numLatentMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_LATENT_MEASURED_IMPURE_PARENTS, value, oldValue));

        IntTextField numMeasuredMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numMeasuredMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_PARENTS, value, oldValue));

        IntTextField numMeasuredMeasuredImpureAssociations = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_ASSOC, D_ZERO), 4
        );
        numMeasuredMeasuredImpureAssociations.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_ASSOC, value, oldValue));

        // ---- Layout (keeps the currently visible rows only) ----------------
        Box root = Box.createVerticalBox();
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        root.add(row("List of count:children:(rank), comma separated; e.g. 5:6(1),2:8(2):", latentGroupSpecs));
        root.add(Box.createVerticalStrut(10));
        root.add(row("Number of structural edges:", numStructuralEdges));

        root.add(Box.createVerticalStrut(10));

        root.add(sectionLabel("Add impure edges:"));
        root.add(row("Latent \u2192 Measured", numLatentMeasuredImpureParents));
        root.add(row("Measured \u2192 Measured", numMeasuredMeasuredImpureParents));
        root.add(row("Measured \u2194 Measured", numMeasuredMeasuredImpureAssociations));

        root.add(Box.createVerticalGlue());
        add(root, BorderLayout.CENTER);
    }

    // ---- Helpers -----------------------------------------------------------

    private static int nonNegativeFilter(Parameters p, String key, int value, int oldValue) {
        try {
            if (value < 0) throw new IllegalArgumentException("Value must be \u2265 0.");
            p.set(key, value);
            return value;
        } catch (Exception ex) {
            TetradLogger.getInstance().log(ex.toString());
            return oldValue;
        }
    }

    private static Box row(String label, JComponent field) {
        Box row = Box.createHorizontalBox();
        JLabel l = new JLabel(label);
        l.setLabelFor(field);
        row.add(l);
        row.add(Box.createHorizontalGlue());
        row.add(field);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static Box sectionLabel(String text) {
        Box box = Box.createHorizontalBox();
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        box.add(l);
        box.add(Box.createHorizontalGlue());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(Box.createVerticalStrut(4));
        return box;
    }
}