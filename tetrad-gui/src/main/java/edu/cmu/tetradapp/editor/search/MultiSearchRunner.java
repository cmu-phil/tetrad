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

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphResultList;
import edu.cmu.tetradapp.util.WatchedProcess;

public final class MultiSearchRunner {

    public static void run(DataWrapper dataWrapper, Algorithm algo, Parameters params, Callback cb) {
        new WatchedProcess() {
            @Override
            public void watch() {
                try {
                    DataModelList dml = dataWrapper.getDataModelList();
                    if (dml == null || dml.isEmpty()) {
                        cb.onSuccess(new GraphResultList()); // nothing to do
                        return;
                    }

                    if (algo instanceof MultiDataSetAlgorithm mds) {
                        // Single graph from many datasets
                        Graph g = mds.search(java.util.List.copyOf(dml.getModelList()), params);
                        GraphResultList out = new GraphResultList();
                        out.add(g, "Combined");
                        cb.onSuccess(out);
                    } else {
                        // One graph per dataset
                        GraphResultList out = new GraphResultList();
                        int idx = 1;
                        for (DataModel dm : dml) {
                            Graph g = algo.search(dm, params); // Algorithm.search(DataModel, Parameters)
                            String name = (dm == null || dm.getName() == null || dm.getName().isBlank())
                                    ? ("Dataset " + idx) : dm.getName();
                            out.add(g, name);
                            idx++;
                        }
                        cb.onSuccess(out);
                    }
                } catch (Exception ex) {
                    cb.onError(ex);
                }
            }
        };
    }

    public interface Callback {
        void onSuccess(GraphResultList results);

        void onError(Exception ex);
    }
}
