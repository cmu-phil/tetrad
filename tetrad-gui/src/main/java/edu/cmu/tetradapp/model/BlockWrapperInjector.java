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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.block.BlockIndependenceFactory;
import edu.cmu.tetradapp.model.block.BlockScoreFactory;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Injects block wrappers into algorithms that can accept them.
 */
final class BlockWrapperInjector {

    private BlockWrapperInjector() {
    }

    /**
     * Core injector for a concrete algorithm instance. Call this anywhere you have the actual object (e.g., right after
     * instantiation in a runner).
     */
    static void applyToInstance(Object algo, DataModel data, Parameters params, BlockSpec spec) {
        Objects.requireNonNull(algo, "algorithm instance is null");

        if (algo instanceof TakesIndependenceWrapper iw) {
            IndependenceWrapper ind = BlockIndependenceFactory.build(data, spec, params);
            iw.setIndependenceWrapper(ind);
        }
        if (algo instanceof TakesScoreWrapper sw) {
            ScoreWrapper sc = BlockScoreFactory.build(data, spec, params);
            sw.setScoreWrapper(sc);
        }
    }

    /**
     * Model-level injector. If the model carries a live instance, inject now. If it carries a class/descriptor, attach
     * a post-instantiation hook so the runner can inject when it creates the instance.
     */
    static void apply(List<AlgorithmModel> models, DataModel data, Parameters params, BlockSpec spec) {
        for (AlgorithmModel m : models) {
            Object descriptorOrInstance = m.getAlgorithm();
            if (descriptorOrInstance == null) continue;

            // Case A: model already holds an instance
            if (descriptorOrInstance instanceof TakesIndependenceWrapper
                || descriptorOrInstance instanceof TakesScoreWrapper) {
                applyToInstance(descriptorOrInstance, data, params, spec);
                continue;
            }

            // Case B: model holds a descriptor; extract its Class<?> via clazz() if present
            Class<?> clazz = extractClazzSafely(descriptorOrInstance);
            if (clazz == null) continue;

            boolean wantsIndep = TakesIndependenceWrapper.class.isAssignableFrom(clazz);
            boolean wantsScore = TakesScoreWrapper.class.isAssignableFrom(clazz);
            if (!wantsIndep && !wantsScore) continue;

            // Register a post-instantiation hook so the runner can inject after constructing the instance.
            Consumer<Object> hook = inst -> applyToInstance(inst, data, params, spec);

            // If AlgorithmModel exposes addPostInstantiationHook(Consumer<Object>), use it.
            try {
                var method = m.getClass().getMethod("addPostInstantiationHook", Consumer.class);
                method.invoke(m, hook);
            } catch (NoSuchMethodException nsme) {
                // No hook available; fall back to runner-side injection (see note below).
            } catch (Exception reflect) {
                // Reflection failed; ignore and rely on runner-side injection.
            }
        }
    }

    /**
     * Try to get a Class<?> from a descriptor via a no-arg method named 'clazz'. Returns null if not available.
     */
    private static Class<?> extractClazzSafely(Object descriptor) {
        try {
            var m = descriptor.getClass().getMethod("clazz");
            Object c = m.invoke(descriptor);
            return (c instanceof Class<?> k) ? k : null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
