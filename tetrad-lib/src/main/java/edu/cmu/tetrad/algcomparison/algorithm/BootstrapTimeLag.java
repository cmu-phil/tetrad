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
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves the time-lag transform ahead of bootstrap resampling.
 * <p>
 * Algorithm wrappers that accept {@code timeLag > 0} apply {@link TsUtils#createLagData} inside their
 * {@code runSearch} core. The bootstrap base classes, however, resample rows BEFORE calling that core, so under
 * bootstrapping each replicate row-resampled the raw series and only then lagged it: {@code X:t-1} in a replicate
 * was a randomly chosen other row of the original data, not the row preceding {@code X:t}. The lagged edges in every
 * replicate were therefore estimated from destroyed transitions, and the ensemble frequencies for cross-lag edges
 * were meaningless (while the contemporaneous frequencies were unaffected, which made the failure hard to spot).
 * <p>
 * The fix implemented here: when {@code numberResampling > 0} and {@code timeLag > 0}, lag each data set ONCE from
 * the original row order, hand the lagged data sets to the resampling loop, and run the wrapper's core with
 * {@code timeLag} set to 0 on a copy of the parameters (so the core does not lag again) and with the wrapper's
 * knowledge set to the lagged knowledge (tiers over the lagged variables) for the duration of the run. Row
 * resampling of lagged rows is then a pairs bootstrap on (current, lagged) tuples, which estimates the same
 * lagged regression the search itself estimates and is valid under the same assumption the search already
 * makes - that the included lags absorb the serial dependence. If the residual dependence is not absorbed (check
 * the residual lag-1 autocorrelations, or use the block wild bootstrap Markov check) the pairs bootstrap
 * understates uncertainty, as any row bootstrap does; a block bootstrap on lagged rows would be the further step
 * and is not implemented here.
 * <p>
 * Base knowledge for lagging follows the convention already used by IMaGES: the wrapper's own knowledge if
 * non-empty, otherwise base-variable (no lag suffix) knowledge attached to the first data set that carries any.
 * Wrappers that do not implement {@link AcceptsKnowledge} cannot receive the lagged knowledge, so for them the
 * data are left unlagged and the old behavior (with its flaw) is retained, with a logged warning.
 * <p>
 * The non-bootstrap path ({@code numberResampling == 0}) is untouched.
 *
 * @author josephramsey
 */
final class BootstrapTimeLag {

    private BootstrapTimeLag() {
    }

    /**
     * Prepares data sets and parameters for a bootstrapped search. If no time lag is requested, or the data are not
     * tabular, or the algorithm cannot accept knowledge, returns the inputs unchanged with a no-op restore.
     *
     * @param algorithm  the algorithm wrapper (checked for {@link AcceptsKnowledge}).
     * @param dataSets   the original (unlagged) data sets.
     * @param parameters the parameters as given by the caller.
     * @return the prepared data sets, the parameters to run the core with, and a restore action that must be run
     * (in a finally block) after the bootstrap completes, to put the wrapper's knowledge back.
     */
    static Prepared prepare(Object algorithm, List<DataModel> dataSets, Parameters parameters) {
        int timeLag = parameters.getInt(Params.TIME_LAG);
        Prepared identity = new Prepared(dataSets, parameters, () -> {
        });

        if (timeLag <= 0) return identity;

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet)) return identity;
        }

        if (!(algorithm instanceof AcceptsKnowledge accepts)) {
            TetradLogger.getInstance().log("Bootstrapping with timeLag > 0 for an algorithm that does not " +
                                           "accept knowledge; rows will be resampled before lagging, which " +
                                           "destroys the time-series structure in each replicate.");
            return identity;
        }

        Knowledge base = accepts.getKnowledge();

        if (base == null || base.isEmpty()) {
            for (DataModel dataModel : dataSets) {
                Knowledge fromData = dataModel.getKnowledge();
                if (fromData == null || fromData.isEmpty()) continue;
                if (isBaseOnly(fromData)) {
                    base = fromData;
                    break;
                }
            }
        }

        if (base == null) base = new Knowledge();

        List<DataModel> lagged = new ArrayList<>(dataSets.size());
        Knowledge laggedKnowledge = null;

        for (DataModel dataModel : dataSets) {
            DataSet timeSeries = TsUtils.createLagData((DataSet) dataModel, timeLag, base);
            if (dataModel.getName() != null) timeSeries.setName(dataModel.getName());
            lagged.add(timeSeries);
            laggedKnowledge = timeSeries.getKnowledge();
        }

        Parameters lagFree = new Parameters(parameters);
        lagFree.set(Params.TIME_LAG, 0);

        final Knowledge original = accepts.getKnowledge();
        accepts.setKnowledge(laggedKnowledge);

        return new Prepared(lagged, lagFree, () -> accepts.setKnowledge(original == null ? new Knowledge() : original));
    }

    private static boolean isBaseOnly(Knowledge knowledge) {
        for (String name : knowledge.getVariables()) {
            if (name.contains(":")) return false;
        }
        return true;
    }

    /**
     * The result of {@link #prepare}.
     *
     * @param dataSets   data sets to hand to the resampling loop.
     * @param parameters parameters to run the wrapper's core with.
     * @param restore    action restoring the wrapper's knowledge; run in a finally block.
     */
    record Prepared(List<DataModel> dataSets, Parameters parameters, Runnable restore) {
    }
}
