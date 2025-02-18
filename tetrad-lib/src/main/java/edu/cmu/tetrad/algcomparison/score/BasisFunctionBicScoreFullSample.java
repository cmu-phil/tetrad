/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Basis Function BIC Score Tabular (BFS-Tabular), which calculates likelihoods using tabular data rather than
 * covariance matrices.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(name = "BFS-FS (Basis Function BIC Full Sample)", command = "bf-bic-score-fs", dataType = DataType.Mixed)
@Mixed
public class BasisFunctionBicScoreFullSample implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Initializes a new instance of the BasisFunctionBicScore class.
     */
    public BasisFunctionBicScoreFullSample() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        edu.cmu.tetrad.search.score.BasisFunctionBicScoreFullSample score = new edu.cmu.tetrad.search.score.BasisFunctionBicScoreFullSample(
                SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT), parameters.getInt(Params.BASIS_TYPE),
                parameters.getDouble(Params.BASIS_SCALE), parameters.getBoolean(Params.ENABLE_REGULARIZATION));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Basis Function BIC Score Tabular Full Sample (BFS-FS)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.TRUNCATION_LIMIT);
        parameters.add(Params.BASIS_TYPE);
        parameters.add(Params.BASIS_SCALE);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.ENABLE_REGULARIZATION);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}
