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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndTestGin;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for GIN (Generalized Independent Noise) residual-independence test.
 *
 * By default uses OLS(ridge=1e-8) for residualization and dCor backend with
 * an *approximate* p-value unless permutations > 0 is specified.
 *
 * Exposed parameters (proposed; add to Params as needed):
 *  - Params.ALPHA
 *  - Params.GIN_BACKEND           // "dcor" (default) or "pearson"
 *  - Params.GIN_PERMUTATIONS      // int, default 0 (use approx p for dCor)
 *  - Params.GIN_RIDGE             // double, default 1e-8
 *  - Params.SEED                  // (optional; included for consistency)
 */
@TestOfIndependence(
        name = "GIN (Residual Independence)",
        command = "gin-test",
        dataType = DataType.Continuous
)
@General
public class Gin implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    public Gin() { }

    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        DataSet data = (DataSet) dataModel;

        // --- Read parameters safely with sensible defaults ---
        double alpha = getDouble(parameters, Params.ALPHA, 0.05);
        String backend = getString(parameters, Params.GIN_BACKEND, "dcor");
        int permutations = getInt(parameters, Params.GIN_PERMUTATIONS, 0);
        double ridge = getDouble(parameters, Params.GIN_RIDGE, 1e-8);
        boolean verbose = getBoolean(parameters, Params.VERBOSE, false);

        // --- Construct test ---
        IndTestGin test = new IndTestGin(data);
        test.setAlpha(alpha);
        test.setVerbose(verbose);
        test.setRegressor(new IndTestGin.OlsRidge(ridge));

        if ("pearson".equalsIgnoreCase(backend)) {
            test.setBackend(new IndTestGin.PearsonCorrTest());
            test.setNumPermutations(0); // permutations not used for Pearson
        } else {
            test.setBackend(new IndTestGin.DistanceCorrTest());
            test.setNumPermutations(Math.max(0, permutations));
        }

        return test;
    }

    @Override
    public String getDescription() {
        return "GIN (Generalized Independent Noise) residual-independence test "
               + "that reduces CI X ⟂ Y | S to unconditional independence between "
               + "regression residuals rX and rY.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.SEED);
        params.add(Params.ALPHA);
        // Proposed GIN params (add to Params if not present yet):
        params.add(Params.GIN_BACKEND);      // "dcor" or "pearson"
        params.add(Params.GIN_PERMUTATIONS); // int
        params.add(Params.GIN_RIDGE);        // double
        // You can include Params.VERBOSE if you list it elsewhere for tests
        // params.add(Params.VERBOSE);
        return params;
    }

    // ---------- Safe getters (avoid NPE if Params key not registered yet) ----

    private static double getDouble(Parameters p, String key, double def) {
        try { return p.getDouble(key); } catch (Exception e) { return def; }
    }

    private static int getInt(Parameters p, String key, int def) {
        try { return p.getInt(key); } catch (Exception e) { return def; }
    }

    private static String getString(Parameters p, String key, String def) {
        try {
            String v = p.getString(key);
            return (v == null || v.isEmpty()) ? def : v;
        } catch (Exception e) { return def; }
    }

    private static boolean getBoolean(Parameters p, String key, boolean def) {
        try { return p.getBoolean(key); } catch (Exception e) { return def; }
    }
}