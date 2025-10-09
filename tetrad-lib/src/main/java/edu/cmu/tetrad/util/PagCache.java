/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A cache for storing PAGs so that the only need to be calculated once per DAG.
 */
public class PagCache {

    /**
     * A singleton instance of the PagCache class. This ensures that only one instance of the cache exists at any given
     * time.
     */
    private static transient PagCache instance = null;
    /**
     * A map that stores the PAGs corresponding to the DAGs.
     */
    private final Map<Graph, Graph> pagCache = Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * Private constructor to prevent instantiation of the PagCache class.
     */
    private PagCache() {
    }

    /**
     * Returns the singleton instance of the PagCache.
     *
     * @return the singleton instance of PagCache.
     */
    public static PagCache getInstance() {
        if (instance == null) {
            instance = new PagCache();
        }

        return instance;
    }

    /**
     * Returns the PAG (Partial Ancestral Graph) corresponding to the given DAG (Directed Acyclic Graph). If the
     * conversion has already been performed earlier, the cached result will be returned. Otherwise, the DAG will be
     * converted to a PAG, cached, and then returned.
     *
     * @param graph the input DAG to be transformed into a PAG
     * @return the corresponding PAG of the input DAG
     * @throws IllegalArgumentException if the input graph is not a DAG
     */
    public @NotNull Graph getPag(Graph graph) {

        // This caching caused problems at one point, turning it off for now. jdramsey 2025-06-14
        if (!(graph.paths().isLegalDag() || graph.paths().isLegalMag())) {
            throw new IllegalArgumentException("Graph must be a DAG or a MAG.");
        }

        // If the graph is already in the cache, return it; otherwise, call GraphTransforms.dagToPag(graph)
        // to get the PAG and put it into the cache.
        if (pagCache.containsKey(graph)) {
            return pagCache.get(graph);
        } else {

            Graph pag = null;

            if (graph.paths().isLegalMag()) {
                MagToPag magToPag = new MagToPag(graph);
                pag = magToPag.convert(false);
            } else if (graph.paths().isLegalDag()) {
                MagToPag magToPag = new MagToPag(GraphTransforms.dagToMag(graph));
                pag = magToPag.convert(false);
            } else {
                Graph mag = GraphTransforms.zhangMagFromPag(graph);
                MagToPag magToPag = new MagToPag(mag);
                pag = magToPag.convert(true);
            }

//            MagToPag dagToPag = new MagToPag(graph);
//            Graph pag = dagToPag.convert();
            pagCache.put(graph, pag);
            return pag;
        }
    }

    /**
     * Returns the PAG (Partial Ancestral Graph) corresponding to the given DAG (Directed Acyclic Graph). If the
     * conversion has already been performed earlier, the cached result will be returned. Otherwise, the DAG will be
     * converted to a PAG, cached, and then returned.
     *
     * @param graph     the input DAG to be transformed into a PAG
     * @param knowledge the knowledge that should be used for the conversion
     * @param verbose   whether to print verbose output
     * @return the corresponding PAG of the input DAG
     * @throws IllegalArgumentException if the input graph is not a DAG
     */
    public Graph getPag(Graph graph, Knowledge knowledge, boolean verbose) {
        if (!graph.paths().isLegalDag()) {
            throw new IllegalArgumentException("Graph must be a DAG.");
        }

        // If the graph is already in the cache, return it; otherwise, call GraphTransforms.dagToPag(graph)
        // to get the PAG and put it into the cache.
        if (pagCache.containsKey(graph)) {
            return pagCache.get(graph);
        } else {
            Graph pag = GraphTransforms.dagToPag(graph);
            pagCache.put(graph, pag);
            return pag;
        }
    }
}

