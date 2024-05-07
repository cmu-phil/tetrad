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

import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author josephramsey
 */
class RandomDagScaleFreeEditor extends JPanel {
    private final IntTextField numNodesField;
    private final IntTextField numLatentsField;

    private final DoubleTextField scaleFreeGammaField;

    /**
     * Constructs a dialog to edit the given workbench randomization parameters. //     * @param preferredNumNodes an
     * integer which, if greater than 1, will revise the number of nodes, //     * number of edges,a nd number of latent
     * nodes. Useful if the interface suggests a number of nodes //     * that overrides the number of nodes set in the
     * preferences.
     */
    public RandomDagScaleFreeEditor() {
        this.numNodesField = new IntTextField(getNumMeasuredNodes(), 4);
        this.numLatentsField = new IntTextField(getNumLatents(), 4);

        NumberFormat nf = new DecimalFormat("0.00");

        DoubleTextField scaleFreeAlphaField = new DoubleTextField(getScaleFreeAlpha(), 4, nf);
        DoubleTextField scaleFreeBetaField = new DoubleTextField(getScaleFreeBeta(), 4, nf);
        this.scaleFreeGammaField = new DoubleTextField(getScaleFreeGamma(), 4, nf);
        DoubleTextField scaleFreeDeltaInField = new DoubleTextField(getScaleFreeDeltaIn(), 4, nf);
        DoubleTextField scaleFreeDeltaOutField = new DoubleTextField(getScaleFreeDeltaOut(), 4, nf);

        // set up text and ties them to the parameters object being edited.
        this.numNodesField.setFilter((value, oldValue) -> {
            if (value == RandomDagScaleFreeEditor.this.numNodesField.getValue()) {
                return oldValue;
            }

            try {
                setNumMeasuredNodes(value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        this.numLatentsField.setFilter((value, oldValue) -> {
            if (value == RandomDagScaleFreeEditor.this.numLatentsField.getValue()) {
                return oldValue;
            }

            try {
                setNumLatents(value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        scaleFreeAlphaField.setFilter((value, oldValue) -> {
            RandomDagScaleFreeEditor.this.setScaleFreeAlpha(value);
            RandomDagScaleFreeEditor.this.setGamma();
            return value;
        });

        scaleFreeBetaField.setFilter((value, oldValue) -> {
            RandomDagScaleFreeEditor.this.setScaleFreeBeta(value);
            RandomDagScaleFreeEditor.this.setGamma();
            return value;
        });

        scaleFreeGammaField.setEnabled(false);
        this.setGamma();

        scaleFreeDeltaInField.setFilter((value, oldValue) -> {
            RandomDagScaleFreeEditor.this.setScaleFreeDeltaIn(value);
            return value;
        });

        scaleFreeDeltaOutField.setFilter((value, oldValue) -> {
            setScaleFreeDeltaOut(value);
            return value;
        });


        // construct the workbench.
        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Parameters for Graph:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b10 = Box.createHorizontalBox();
        b10.add(new JLabel("Number of measured nodes:"));
        b10.add(Box.createHorizontalGlue());
        b10.add(this.numNodesField);
        b1.add(b10);

        Box b11 = Box.createHorizontalBox();
        b11.add(new JLabel("Number of additional latent confounders:"));
        b11.add(Box.createHorizontalGlue());
        b11.add(this.numLatentsField);
        b1.add(b11);

        Box b14 = Box.createHorizontalBox();
        b14.add(new JLabel("Alpha:"));
        b14.add(Box.createHorizontalGlue());
        b14.add(scaleFreeAlphaField);
        b1.add(b14);

        Box b15 = Box.createHorizontalBox();
        b15.add(new JLabel("Beta:"));
        b15.add(Box.createHorizontalGlue());
        b15.add(scaleFreeBetaField);
        b1.add(b15);

        Box b15a = Box.createHorizontalBox();
        b15a.add(new JLabel("Gamma:"));
        b15a.add(Box.createHorizontalGlue());
        b15a.add(this.scaleFreeGammaField);
        b1.add(b15a);

        Box b15b = Box.createHorizontalBox();
        b15b.add(new JLabel("Note: Gamma = 1 - Alpha - Beta; Alpha, Beta, Gamma > 0"));
        b1.add(b15b);

        Box b13 = Box.createHorizontalBox();
        b13.add(new JLabel("Delta In:"));
        b13.add(Box.createHorizontalGlue());
        b13.add(scaleFreeDeltaInField);
        b1.add(b13);

        Box b16 = Box.createHorizontalBox();
        b16.add(new JLabel("Delta Out:"));
        b16.add(Box.createHorizontalGlue());
        b16.add(scaleFreeDeltaOutField);
        b1.add(b16);

        Box d = Box.createVerticalBox();
        b1.setBorder(new TitledBorder(""));
        d.add(b1);

        d.add(Box.createVerticalGlue());

        add(d, BorderLayout.CENTER);
    }

    private void setGamma() {
        this.scaleFreeGammaField.setValue(getScaleFreeGamma());
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return a int
     */
    public int getNumNodes() {
        return getNumMeasuredNodes() + getNumLatents();
    }

    private int getNumMeasuredNodes() {
        return Preferences.userRoot().getInt("newGraphNumMeasuredNodes", 5);
    }

    private void setNumMeasuredNodes(int numMeasuredNodes) {
        if (numMeasuredNodes + getNumLatents() < 2) {
            throw new IllegalArgumentException("Number of nodes Must be greater than or equal to 2.");
        }

        Preferences.userRoot().putInt("newGraphNumMeasuredNodes", numMeasuredNodes);
    }

    /**
     * <p>getNumLatents.</p>
     *
     * @return a int
     */
    public int getNumLatents() {
        return Preferences.userRoot().getInt("newGraphNumLatents", 0);
    }

    private void setNumLatents(int numLatentNodes) {
        if (numLatentNodes < 0) {
            throw new IllegalArgumentException(
                    "Number of additional latent confounders must be" + " >= 0: " +
                    numLatentNodes);
        }

        Preferences.userRoot().putInt("newGraphNumLatents", numLatentNodes);
    }

    /**
     * <p>isConnected.</p>
     *
     * @return a boolean
     */
    public boolean isConnected() {
        return Preferences.userRoot().getBoolean("randomGraphConnected", false);
    }

    /**
     * <p>getScaleFreeAlpha.</p>
     *
     * @return a double
     */
    public double getScaleFreeAlpha() {
        return Preferences.userRoot().getDouble("scaleFreeAlpha", 0.2);
    }

    private void setScaleFreeAlpha(double scaleFreeAlpha) {
        Preferences.userRoot().putDouble("scaleFreeAlpha", scaleFreeAlpha);
    }

    /**
     * <p>getScaleFreeBeta.</p>
     *
     * @return a double
     */
    public double getScaleFreeBeta() {
        return Preferences.userRoot().getDouble("scaleFreeBeta", 0.6);
    }

    private void setScaleFreeBeta(double scaleFreeBeta) {
        Preferences.userRoot().putDouble("scaleFreeBeta", scaleFreeBeta);
    }

    private double getScaleFreeGamma() {
        return 1.0 - getScaleFreeAlpha() - getScaleFreeBeta();
    }

    /**
     * <p>getScaleFreeDeltaIn.</p>
     *
     * @return a double
     */
    public double getScaleFreeDeltaIn() {
        return Preferences.userRoot().getDouble("scaleFreeDeltaIn", 0.2);
    }

    private void setScaleFreeDeltaIn(double scaleFreeDeltaIn) {
        Preferences.userRoot().putDouble("scaleFreeDeltaIn", scaleFreeDeltaIn);
    }

    /**
     * <p>getScaleFreeDeltaOut.</p>
     *
     * @return a double
     */
    public double getScaleFreeDeltaOut() {
        return Preferences.userRoot().getDouble("scaleFreeDeltaOut", 0.2);
    }

    private void setScaleFreeDeltaOut(double scaleFreeDeltaOut) {
        Preferences.userRoot().putDouble("scaleFreeDeltaOut", scaleFreeDeltaOut);
    }
}





