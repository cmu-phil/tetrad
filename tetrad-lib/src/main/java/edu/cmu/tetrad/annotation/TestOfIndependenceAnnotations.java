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

import java.util.List;

/**
 *
 * Sep 26, 2017 1:18:28 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TestOfIndependenceAnnotations extends AbstractAnnotations<TestOfIndependence> {

    private static final TestOfIndependenceAnnotations INSTANCE = new TestOfIndependenceAnnotations();

    private TestOfIndependenceAnnotations() {
        super("edu.cmu.tetrad.algcomparison.independence", TestOfIndependence.class);
    }

    public static TestOfIndependenceAnnotations getInstance() {
        return INSTANCE;
    }

    public List<AnnotatedClass<TestOfIndependence>> filterOutExperimental(List<AnnotatedClass<TestOfIndependence>> list) {
        return filterOutByAnnotation(list, Experimental.class);
    }

}
