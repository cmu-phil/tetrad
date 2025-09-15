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

package edu.cmu.tetrad.annotation;

/**
 * Author : Jeremy Espino MD Created 6/30/17 10:36 AM
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum AlgType {

    /**
     * If an algorithm forbids latent common causes.
     */
    forbid_latent_common_causes, // PC_All, PcStableMax, FGES, IMaGES_Discrete, IMaGES_Continuous, FANG, EFANG

    /**
     * If an algorithm allows latent common causes.
     */
    allow_latent_common_causes, // FCI, RFCI, FGES-FCI, SVARFCI, SvarGFCI

    /**
     * If an algorithm searches for Markov blanekts.
     */
    search_for_Markov_blankets, // FGES-MB, PC-MB

    /**
     * If an algorithm produces undirected graphs.
     */
    produce_undirected_graphs, // FAS, MGM, GLASSO

    /**
     * If algorithm orients edges pairwise.
     */
    orient_pairwise, // R3, RSkew, Skew

    /**
     * If an algorithm searches for structure over latents.
     */
    search_for_structure_over_latents // BPC, FOFC, FTFC
}

