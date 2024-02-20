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

import java.io.Serial;
import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * AnnotatedClass represents a class with its associated annotation.
 *
 * @param <T> the type of the annotation
 */
public record AnnotatedClass<T extends Annotation>(Class<?> clazz, T annotation) implements Serializable {

    @Serial
    private static final long serialVersionUID = 5060798016477163171L;

    /**
     * Creates an annotated class.
     *
     * @param clazz      class
     * @param annotation annotation
     */
    public AnnotatedClass {
    }

    /**
     * Gets the class.
     *
     * @return class
     */
    @Override
    public Class<?> clazz() {
        return this.clazz;
    }

    /**
     * Gets the annotation.
     *
     * @return annotation
     */
    @Override
    public T annotation() {
        return this.annotation;
    }

}
