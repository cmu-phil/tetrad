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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Blocks BIC Score (Blocks-BIC) version.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Blocks-BIC",
        command = "blocks-bic-score",
        dataType = DataType.Mixed)
@Mixed
public class BlocksBicScore implements BlockScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Specifies the block structure for the score calculation. Used to organize variables into predefined blocks for
     * analysis.
     */
    private BlockSpec blockSpec;

    /**
     * Initializes a new instance of the BasisFunctionBicScore class.
     */
    public BlocksBicScore() {

    }

    /**
     * Sets the block specification to define the structure for organizing variables into predefined blocks for
     * analysis.
     *
     * @param blockSpec the block specification to be set
     */
    @Override
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }

    /**
     * Computes and returns a score based on the provided data model and parameter settings.
     *
     * @param model      the data model that serves as the basis for the score computation
     * @param parameters contains configuration and parameter values used during the score computation
     * @return a {@code Score} object representing the computed result based on the given model and parameters
     */
    @Override
    public Score getScore(DataModel model, Parameters parameters) {
        edu.cmu.tetrad.search.score.BlocksBicScore score = new edu.cmu.tetrad.search.score.BlocksBicScore(
                blockSpec);
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        return score;
    }

    /**
     * Provides a textual description of the score type.
     *
     * @return a string representing the description of the Blocks BIC score, indicating that it requires block
     * specifications for computation.
     */
    @Override
    public String getDescription() {
        return "Blocks BIC";
    }

    /**
     * Retrieves the data type associated with this score wrapper.
     *
     * @return the data type, which indicates whether the data is continuous, discrete, mixed, or belongs to other
     * specific categories such as covariance or graph. In this implementation, the data type is
     * {@code DataType.Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves a list of parameter keys used in the computation or configuration of the score.
     *
     * @return a list of parameter names as strings. These parameters are required for score computation or
     * configuration and include elements such as penalty discounts or other algorithm-specific settings.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return parameters;
    }

    /**
     * Retrieves a variable by its name from the associated data set.
     *
     * @param name the name of the variable to retrieve
     * @return the {@code Node} object representing the variable with the specified name, or {@code null} if no such
     * variable exists
     */
    @Override
    public Node getVariable(String name) {
        return blockSpec.dataSet().getVariable(name);
    }
}
