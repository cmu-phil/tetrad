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

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.editor.algorithm.AlgorithmCard;
import edu.cmu.tetradapp.editor.algorithm.GraphCard;
import edu.cmu.tetradapp.editor.algorithm.ParameterCard;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import java.awt.CardLayout;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements PropertyChangeListener, FinalizingEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralAlgorithmEditor.class);

    private static final long serialVersionUID = -5719467682865706447L;

    private final String ALGORITHM_CARD = "algorithm card";
    private final String PARAMETER_CARD = "parameter card";
    private final String GRAPH_CARD = "graph card";

    private final GeneralAlgorithmRunner algorithmRunner;
    private final TetradDesktop desktop;
    private final AlgorithmCard algorithmCard;
    private final ParameterCard parameterCard;
    private final GraphCard graphCard;

    public GeneralAlgorithmEditor(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.algorithmCard = new AlgorithmCard(this, algorithmRunner);
        this.parameterCard = new ParameterCard(this, algorithmRunner);
        this.graphCard = new GraphCard(this, algorithmRunner);

        initComponents();

        // repopulate all the previous selections if reopen the search box
        if (algorithmRunner.getGraphs() != null && algorithmRunner.getGraphs().size() > 0) {
            changeCard(GRAPH_CARD);
        }
    }

    private void initComponents() {
        setLayout(new CardLayout());
        add(algorithmCard, ALGORITHM_CARD);
        add(parameterCard, PARAMETER_CARD);
        add(graphCard, GRAPH_CARD);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "algoFwd":
                parameterCard.refresh();
                changeCard(PARAMETER_CARD);
                break;
            case "paramBack":
                changeCard(ALGORITHM_CARD);
                break;
            case "paramFwd":
                parameterCard.disableButtons();
                doSearch();
                break;
        }
    }

    private void changeCard(String card) {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.show(this, card);
    }

    public void setAlgorithmResult(String jsonResult) {
    }

    public void setAlgorithmErrorResult(String errorResult) {
    }

    private void doSearch() {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                HpcAccount hpcAccount = null;
                if (hpcAccount == null) {
                    algorithmRunner.execute();

                    firePropertyChange("modelChanged", null, null);

                    parameterCard.enableButtons();
                    graphCard.refresh();
                    changeCard(GRAPH_CARD);
                }
            }
        };

    }

    @Override
    public boolean finalizeEditor() {
        return true;
    }

}
