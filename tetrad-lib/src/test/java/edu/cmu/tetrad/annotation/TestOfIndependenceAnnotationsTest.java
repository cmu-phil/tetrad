/*
 * Copyright (C) 2017 University of Pittsburgh.
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
package edu.cmu.tetrad.annotation;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dec 6, 2017 12:03:04 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TestOfIndependenceAnnotationsTest {

    public TestOfIndependenceAnnotationsTest() {
    }

    @Test
    public void testAnnotatedNameAttributeForUniqueness() {
        List<AnnotatedClass<TestOfIndependence>> indTests = TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses();
        List<String> values = indTests.stream().map(e -> e.annotation().name().toLowerCase()).collect(Collectors.toList());

        long actual = values.size();
        long expected = values.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'name' is not unique.", expected, actual);
    }

    @Test
    public void testAnnotatedCommandAttributeForUniqueness() {
        List<AnnotatedClass<TestOfIndependence>> indTests = TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses();
        List<String> values = indTests.stream().map(e -> e.annotation().name().toLowerCase()).collect(Collectors.toList());

        long actual = values.size();
        long expected = values.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'command' is not unique.", expected, actual);
    }

}
