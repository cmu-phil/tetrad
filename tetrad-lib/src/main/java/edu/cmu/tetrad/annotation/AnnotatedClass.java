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
import java.util.Objects;

/**
 * AnnotatedClass represents a class along with its associated annotation.
 */
public final class AnnotatedClass<T extends Annotation> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5060798016477163171L;

    /**
     * Represents a Class used as a parameter to an AnnotatedClass object. It is stored as a private final member in the
     * AnnotatedClass class.
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>
     * AnnotatedClass&lt;MyAnnotation&gt; annotatedClass = new AnnotatedClass&lt;&gt;(MyClass.class, myAnnotation);
     * Class&lt;?&gt; clazz = annotatedClass.clazz();
     * </pre>
     *
     * @see AnnotatedClass
     */
    private final Class<?> clazz;

    /**
     * AnnotatedClass represents a class along with its associated annotation.
     */
    private final T annotation;


    /**
     * Creates an annotated class.
     *
     * @param clazz      class
     * @param annotation annotation
     */
    public AnnotatedClass(Class<?> clazz, T annotation) {
        this.clazz = clazz;
        this.annotation = annotation;
    }

    /**
     * Gets the class.
     *
     * @return class
     */
    public Class<?> clazz() {
        return this.clazz;
    }

    /**
     * Gets the annotation.
     *
     * @return annotation
     */
    public T annotation() {
        return this.annotation;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        @SuppressWarnings("rawtypes") var that = (AnnotatedClass) obj;
        return Objects.equals(this.clazz, that.clazz) &&
               Objects.equals(this.annotation, that.annotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, annotation);
    }

    @Override
    public String toString() {
        return "AnnotatedClass[" +
               "clazz=" + clazz + ", " +
               "annotation=" + annotation + ']';
    }


}
