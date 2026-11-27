///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.pitt.dbmi.algo.resampling;

/**
 * Sep 12, 2018 4:07:46 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * @version $Id: $Id
 */
public enum ResamplingEdgeEnsemble {

    /**
     * Choose an edge iff there is an edge that its prob. is the highest and it's not nil otherwise choose nil, The
     * default choice
     */
    Preserved,

    /**
     * Choose an edge iff its prob. is the highest (even it's nil, which means that there is no edge).
     */
    Highest,

    /**
     * Choose an edge iff its prob. > .5 even it's nil.
     */
    Majority,

    /**
     * Choose an edge iff its prob. > some user-defimed threshold (even it's nil, which means that that there is no
     * edge).
     */
    Threshold,

    /**
     * Instead of assembling a composite of edges drawn from different bootstrap graphs (which in general is not a
     * legal member of the algorithm's output class), display the single bootstrap graph closest to the ensemble
     * edge frequencies. The displayed graph is one the algorithm actually produced, so it inherits the algorithm's
     * legality guarantee (e.g., a legal CPDAG or PAG). See GraphSampling.selectMedianEnsembleGraph. Added 2026-8-13.
     */
    Median,

    /**
     * Decide the adjacency and each of the two endpoint marks separately. An edge is kept iff it is present in at
     * least a given fraction of the resamples; an arrowhead or tail is kept at an endpoint iff that mark sits at that
     * endpoint in at least a given (higher) fraction of all the resamples; any other endpoint of a kept edge is a
     * circle. A precision-oriented summary that says less where the resamples disagree; like the other composites
     * it is generally not a legal member of the algorithm's output class. See
     * GraphSampling.createEndpointwiseDisplayGraph. Added 2026-11-27.
     */
    Endpointwise,
}

