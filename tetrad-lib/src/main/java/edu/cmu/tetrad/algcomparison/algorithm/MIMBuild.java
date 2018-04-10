/*
 * Copyright (C) 2018 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Nonexecutable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import java.util.Collections;
import java.util.List;

/**
 *
 * Jan 18, 2018 2:39:29 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "MIMBuild",
        command = "mimbuild",
        algoType = AlgType.search_for_structure_over_latents
)
@Nonexecutable
public class MIMBuild implements Algorithm {

    private static final long serialVersionUID = 23L;

    private final String description = "This MIMBuild algorithm requires both data and a measurement model as inputs; \n"
            + "You can run either Bpc or Fofc to obtain a measurement model.";

    private final String unsupportedMsg = "MIMBuild cannot be run directly from this class.  "
            + "Please see the Tetrad documentation on how to setup and run MIMBuild.";

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        throw new UnsupportedOperationException(unsupportedMsg);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        throw new UnsupportedOperationException(unsupportedMsg);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public DataType getDataType() {
        return DataType.Graph;
    }

    @Override
    public List<String> getParameters() {
        return Collections.EMPTY_LIST;
    }

}
