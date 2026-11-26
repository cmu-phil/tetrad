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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Says why a search cannot be run on a data set of the wrong kind: which kind the data is, whether it is the algorithm
 * itself or the test or score chosen for it that takes another kind, and what to change. This replaces "The algorithm
 * was not expecting that type of data.", which named neither kind and blamed the algorithm even when the test or score
 * was the mismatch.
 *
 * @author Claude
 */
final class DataKindMismatch {

    private DataKindMismatch() {
    }

    /**
     * Whether something that takes data of the given type can be run on the given data. This is the rule the Search
     * box applies before a search: continuous data needs Continuous or Mixed, discrete data needs Discrete or Mixed,
     * and mixed data needs Mixed.
     *
     * @param takes the type of data taken by an algorithm, test, or score
     * @param data  the data
     * @return true if the data is of a kind that is taken
     */
    static boolean accepts(DataType takes, DataModel data) {
        return (data.isContinuous() && (takes == DataType.Continuous || takes == DataType.Mixed))
               || (data.isDiscrete() && (takes == DataType.Discrete || takes == DataType.Mixed))
               || (data.isMixed() && takes == DataType.Mixed);
    }

    /**
     * Whether an algorithm that searches several data sets together cannot take one of them. Such an algorithm's kind
     * of data is that of the test and score chosen for it; several report that they take all kinds whatever was
     * chosen, so the test and the score are asked directly. An algorithm with neither is asked itself.
     *
     * @param algorithm the algorithm, with its test and score set if it takes them
     * @param data      one of the data sets
     * @return true if the test, the score, or (having neither) the algorithm does not take data of this kind
     */
    static boolean refuses(Algorithm algorithm, DataModel data) {
        boolean asked = false;

        if (algorithm instanceof TakesIndependenceWrapper takesTest && takesTest.getIndependenceWrapper() != null) {
            asked = true;
            if (!accepts(takesTest.getIndependenceWrapper().getDataType(), data)) return true;
        }

        if (algorithm instanceof TakesScoreWrapper takesScore && takesScore.getScoreWrapper() != null) {
            asked = true;
            if (!accepts(takesScore.getScoreWrapper().getDataType(), data)) return true;
        }

        if (asked) return false;

        DataType own = algorithm.getDataType();
        return own != DataType.All && !accepts(own, data);
    }

    /**
     * The message for an algorithm that cannot be run on the given data. Each sentence is on its own line.
     *
     * @param algorithm the algorithm, with its test and score set if it takes them
     * @param data      the data it was asked to search, which it does not accept
     * @return the message
     */
    static String message(Algorithm algorithm, DataModel data) {
        String dataName = data.getName() == null || data.getName().isBlank()
                ? "The data set" : "The data set '" + data.getName() + "'";
        String kind = data.isContinuous() ? "continuous" : data.isDiscrete() ? "discrete"
                : data.isMixed() ? "mixed" : null;
        String isKind = kind == null ? "is of a kind this search cannot use"
                : kind.equals("mixed") ? "is mixed (continuous and discrete columns)" : "is " + kind;
        String forThisData = kind == null ? "this data" : kind + " data";
        String algorithmName = algorithmName(algorithm);

        // The kind of data most algorithms take is the kind their test or score takes, so look there first.
        List<String> chosen = new ArrayList<>();
        List<String> toChoose = new ArrayList<>();

        if (algorithm instanceof TakesIndependenceWrapper takesTest) {
            IndependenceWrapper test = takesTest.getIndependenceWrapper();
            if (test != null && !accepts(test.getDataType(), data)) {
                chosen.add("the test chosen, " + testName(test) + ", is " + takes(test.getDataType()));
                toChoose.add("a test");
            }
        }

        if (algorithm instanceof TakesScoreWrapper takesScore) {
            ScoreWrapper score = takesScore.getScoreWrapper();
            if (score != null && !accepts(score.getDataType(), data)) {
                chosen.add("the score chosen, " + scoreName(score) + ", is " + takes(score.getDataType()));
                toChoose.add("a score");
            }
        }

        if (!chosen.isEmpty()) {
            return algorithmName + " cannot be run on this data as it is set up.\n"
                   + dataName + " " + isKind + ", and " + String.join(", and ", chosen) + ".\n"
                   + "Choose " + String.join(" and ", toChoose) + " for " + forThisData
                   + " in this Search box, or convert the data in a Data box first.";
        }

        return algorithmName + " is " + takes(algorithm.getDataType()) + ", and " + lowerFirst(dataName) + " " + isKind
               + ".\n"
               + "Choose an algorithm for " + forThisData + " in this Search box, or convert the data in a Data box"
               + " first.";
    }

    private static String takes(DataType type) {
        if (type == DataType.Continuous) return "for continuous data";
        if (type == DataType.Discrete) return "for discrete data";
        if (type == DataType.Mixed) return "for continuous, discrete, or mixed data";
        if (type == DataType.Graph) return "for a graph, not for data";
        return "for data of type " + type;
    }

    private static String algorithmName(Algorithm algorithm) {
        edu.cmu.tetrad.annotation.Algorithm annotation
                = algorithm.getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class);
        return annotation != null ? annotation.name() : algorithm.getClass().getSimpleName();
    }

    private static String testName(IndependenceWrapper test) {
        TestOfIndependence annotation = test.getClass().getAnnotation(TestOfIndependence.class);
        return annotation != null ? annotation.name() : test.getClass().getSimpleName();
    }

    private static String scoreName(ScoreWrapper score) {
        Score annotation = score.getClass().getAnnotation(Score.class);
        return annotation != null ? annotation.name() : score.getClass().getSimpleName();
    }

    private static String lowerFirst(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
