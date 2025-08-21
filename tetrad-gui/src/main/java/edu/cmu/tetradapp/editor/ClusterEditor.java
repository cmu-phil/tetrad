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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.ui.BlockClusteringWizard;
import edu.cmu.tetrad.search.blocks.ui.BlockSpecEditorPanel;
import edu.cmu.tetrad.util.JsonUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.editor.search.AlgorithmCard;
import edu.cmu.tetradapp.editor.search.GraphCard;
import edu.cmu.tetradapp.editor.search.ParameterCard;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.ClusterRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author josephramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ClusterEditor extends JPanel implements PropertyChangeListener, ActionListener, FinalizingEditor {
    @Serial
    private static final long serialVersionUID = -23L;

    /**
     * The algorithm card.
     */
    private final ClusterRunner clusterRunner;

    /**
     * The desktop.
     */
    private final TetradDesktop desktop;
    private BlockClusteringWizard wizard;

    /**
     * <p>Constructor for GeneralAlgorithmEditor.</p>
     *
     * @param clusterRunner a {@link ClusterRunner} object
     */
    public ClusterEditor(ClusterRunner clusterRunner) {
        this.clusterRunner = clusterRunner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        DataModelList dataModelList = clusterRunner.getDataWrapper().getDataModelList();
        DataSet first = (DataSet) (dataModelList.getFirst());
        wizard = new BlockClusteringWizard(first);
        wizard.setPreferredSize(new Dimension(600, 400));
        setLayout( new BorderLayout());
        add(wizard, BorderLayout.CENTER);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent evt) {
    }

    @Override
    public boolean finalizeEditor() {
        BlockSpec blockSpec = this.clusterRunner.getBlockSpec();
        if (blockSpec == null) {
            int option = JOptionPane.showConfirmDialog(this, "You have not performed a clustering. Close anyway?", "Close?",
                    JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }

        return true;
    }
}
