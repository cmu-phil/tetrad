///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * DG-BGe: the BGe score applied to the degenerate Gaussian embedding of {@link DegenerateGaussianScore}. Continuous
 * variables are kept as they are (no scaling, no basis expansion) and each discrete variable is replaced by one 0/1
 * indicator column per category but the last; the resulting columns are then scored with the Normal-Wishart marginal
 * likelihood of {@link BgeScore}, with the family score log p(D_{A u B}) - log p(D_B) over the embedded column sets
 * of the child and its parents. On purely continuous data this is exactly {@link BgeScore}; on purely discrete data it
 * is the marginal-likelihood counterpart of DG-BIC in the same way that BDeu is the counterpart of the discrete BIC
 * score, though, like DG-BIC, it models only additive (main-effect) dependence of a child's indicators on the parents'
 * indicators.
 * <p>
 * This is {@link BasisFunctionBgeScore} with truncation limit 1 and the unscaled embedding; see that class for the
 * formula, the prior conventions, and the meaning of the hyperparameters.
 *
 * @author josephramsey
 * @see BasisFunctionBgeScore
 * @see DegenerateGaussianScore
 * @see BgeScore
 */
public class DegenerateGaussianBgeScore extends BasisFunctionBgeScore {

    /**
     * Constructs a DG-BGe score.
     *
     * @param dataSet the (mixed) data set.
     */
    public DegenerateGaussianBgeScore(DataSet dataSet) {
        super(dataSet, 1, embed(dataSet, 1, -1), false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "DG-BGe Score alphaMu = " + nf.format(getAlphaMu()) + " alphaW = p + " + nf.format(getAlphaWOffset());
    }
}
