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

import java.lang.annotation.Annotation;

/**
 *
 * Sep 20, 2017 2:28:50 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @param <T> annotation
 */
public class AnnotatedClassWrapper<T extends Annotation> implements Comparable<AnnotatedClassWrapper> {

    protected final String name;

    protected final AnnotatedClass<T> annotatedClass;

    public AnnotatedClassWrapper(String name, AnnotatedClass<T> annotatedClass) {
        this.name = name;
        this.annotatedClass = annotatedClass;
    }

    @Override
    public int compareTo(AnnotatedClassWrapper other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public AnnotatedClass<T> getAnnotatedClass() {
        return annotatedClass;
    }

}
