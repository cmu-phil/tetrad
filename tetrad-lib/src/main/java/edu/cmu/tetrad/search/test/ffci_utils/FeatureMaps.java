package edu.cmu.tetrad.search.test.ffci_utils;

import edu.cmu.tetrad.search.test.ffci_utils.*;
import org.ejml.simple.SimpleMatrix;

import java.util.Random;

public enum FeatureMaps implements FeatureMap {

    RFF_RBF {
        @Override
        public SimpleMatrix compute(SimpleMatrix raw, FeatureSpec spec, Random rng) {
            return RffUtils.rffRbf(
                    raw,
                    spec.numFeatures(),
                    spec.sigma(),
                    rng
            );
        }

        @Override
        public String id() {
            return "RFF_RBF";
        }
    },

    ORF_RBF {
        @Override
        public SimpleMatrix compute(SimpleMatrix raw, FeatureSpec spec, Random rng) {
            return RffUtils.orfRbf(
                    raw,
                    spec.numFeatures(),
                    spec.sigma(),
                    rng
            );
        }

        @Override
        public String id() {
            return "ORF_RBF";
        }
    };
}