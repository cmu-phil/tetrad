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

import edu.cmu.tetrad.util.AlgorithmDescriptions;
import edu.cmu.tetrad.util.IndependenceTestDescriptions;
import edu.cmu.tetrad.util.ScoreDescriptions;
import org.junit.Assert;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Checks that every algorithm, independence test and score the user can select has a description in the HTML manual.
 * <p>
 * Tetrad reads its own manual at runtime. {@link AlgorithmDescriptions}, {@link IndependenceTestDescriptions} and
 * {@link ScoreDescriptions} parse {@code docs/manual/index.html} and look up the element whose {@code id} equals the
 * annotation's {@code command} attribute. When no such element exists, each of those classes returns the literal string
 * "Please add a description for &lt;command&gt;.", and that string is what the interface shows the user in place of a
 * description. Adding an annotated class without adding a matching block to the manual is therefore a user-visible
 * defect, and it is one that nothing else in the build detects.
 * <p>
 * This test closes that gap. It asks the description classes the same question the interface asks, by command, and
 * fails with the full list of offenders rather than the first one.
 * <p>
 * Classes marked {@code @Deprecated} are skipped, because the interface filters them out of the choosers it offers (see
 * {@code AlgorithmModels} and {@code AlgorithmChooserLogic}), so the user cannot reach their descriptions.
 * {@code @Experimental} classes are <em>not</em> skipped: the interface shows them whenever the user turns on
 * experimental methods, so their descriptions are reachable and are checked here.
 *
 * @author josephramsey
 * @see AlgorithmDescriptions
 * @see IndependenceTestDescriptions
 * @see ScoreDescriptions
 */
public class DescriptionCoverageTest {

    /**
     * The prefix of the placeholder string returned by the description classes when a command has no manual entry.
     */
    private static final String PLACEHOLDER_PREFIX = "Please add a description for";

    /**
     * Constructs a new instance of this test class.
     */
    public DescriptionCoverageTest() {
    }

    /**
     * Every selectable algorithm has a description in the HTML manual.
     */
    @Test
    public void testEveryAlgorithmHasAManualEntry() {
        assertDescribed("algorithm",
                AlgorithmAnnotations.getInstance().getAnnotatedClasses(),
                annotation -> annotation.command(),
                command -> AlgorithmDescriptions.getInstance().get(command));
    }

    /**
     * Every selectable independence test has a description in the HTML manual.
     */
    @Test
    public void testEveryIndependenceTestHasAManualEntry() {
        assertDescribed("independence test",
                TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses(),
                annotation -> annotation.command(),
                command -> IndependenceTestDescriptions.getInstance().get(command));
    }

    /**
     * Every selectable score has a description in the HTML manual.
     */
    @Test
    public void testEveryScoreHasAManualEntry() {
        assertDescribed("score",
                ScoreAnnotations.getInstance().getAnnotatedClasses(),
                annotation -> annotation.command(),
                command -> ScoreDescriptions.getInstance().get(command));
    }

    /**
     * Fails if any non-deprecated annotated class in the given list has a missing, blank or placeholder description.
     *
     * @param kind              the kind of thing being checked, for the failure message
     * @param annotatedClasses  the annotated classes to check
     * @param commandOf         extracts the command attribute from one of those annotations
     * @param descriptionOf     looks the description up by command, as the interface does
     * @param <T>               the annotation type
     */
    private static <T extends Annotation> void assertDescribed(
            String kind,
            List<AnnotatedClass<T>> annotatedClasses,
            Function<T, String> commandOf,
            Function<String, String> descriptionOf) {

        List<String> undescribed = new ArrayList<>();

        for (AnnotatedClass<T> annotatedClass : annotatedClasses) {
            if (annotatedClass.clazz().isAnnotationPresent(Deprecated.class)) {
                continue;
            }

            String command = commandOf.apply(annotatedClass.annotation());
            String description = descriptionOf.apply(command);

            if (description == null || description.isBlank() || description.startsWith(PLACEHOLDER_PREFIX)) {
                undescribed.add(command + " (" + annotatedClass.clazz().getSimpleName() + ")");
            }
        }

        if (!undescribed.isEmpty()) {
            Assert.fail(undescribed.size() + " " + kind + "(s) have no entry in docs/manual/index.html, so the "
                        + "interface shows a placeholder instead of a description. Add a <div id=\"command\"> block "
                        + "with one or more child paragraphs for each of: " + String.join(", ", undescribed));
        }
    }
}
