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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dec 6, 2017 11:53:16 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ScoreAnnotationsTest {

    public ScoreAnnotationsTest() {
    }

    @Test
    public void testAnnotatedNameAttributeForUniqueness() {
        List<AnnotatedClass<Score>> scores = ScoreAnnotations.getInstance().getAnnotatedClasses();
        List<String> values = scores.stream().map(e -> e.annotation().name().toLowerCase()).collect(Collectors.toList());

        long actual = values.size();
        long expected = values.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'name' is not unique.", expected, actual);
    }

    @Test
    public void testAnnotatedCommandAttributeForUniqueness() {
        List<AnnotatedClass<Score>> scores = ScoreAnnotations.getInstance().getAnnotatedClasses();
        List<String> values = scores.stream().map(e -> e.annotation().command().toLowerCase()).toList();

        long actual = values.size();
        long expected = values.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'command' is not unique.", expected, actual);
    }

}

