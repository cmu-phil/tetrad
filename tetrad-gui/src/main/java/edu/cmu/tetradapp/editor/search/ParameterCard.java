/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetradapp.editor.AlgorithmParameterPanel;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;

import java.awt.*;
import java.io.Serial;

/**
 * Apr 15, 2019 3:35:36 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ParameterCard extends AlgorithmParameterPanel {

    @Serial
    private static final long serialVersionUID = 2684962776580724327L;

    /**
     * The algorithm runner.
     */
    private final GeneralAlgorithmRunner algorithmRunner;

    /**
     * <p>Constructor for ParameterCard.</p>
     *
     * @param algorithmRunner a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     */
    public ParameterCard(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;

        initComponents();
    }

    private void initComponents() {
        setPreferredSize(new Dimension(800, 506));
    }

    /**
     * <p>refresh.</p>
     */
    public void refresh() {
        addToPanel(this.algorithmRunner);
    }

}
