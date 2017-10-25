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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.reflections.Reflections;

/**
 *
 * Sep 20, 2017 10:59:43 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @param <T> annotation type
 */
public abstract class AbstractAnnotations<T extends Annotation> {

    protected final List<AnnotatedClass<T>> annotatedClasses;

    public AbstractAnnotations(String packageName, Class<T> type) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(type);

        this.annotatedClasses = classes.parallelStream()
                .map(e -> new AnnotatedClass<>(e, e.getAnnotation(type)))
                .collect(Collectors.toList());
    }

    public List<AnnotatedClass<T>> getAnnotatedClasses() {
        return Collections.unmodifiableList(annotatedClasses);
    }

    public List<AnnotatedClass<T>> filterByAnnotation(List<AnnotatedClass<T>> annoClasses, Class<? extends Annotation> type) {
        if (annoClasses == null || type == null) {
            return Collections.EMPTY_LIST;
        }

        List<AnnotatedClass<T>> list = annoClasses.stream()
                .filter(e -> e.getClazz().isAnnotationPresent(type))
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    public List<AnnotatedClass<T>> filterOutByAnnotation(List<AnnotatedClass<T>> annoClasses, Class<? extends Annotation> type) {
        if (annoClasses == null || type == null) {
            return Collections.EMPTY_LIST;
        }

        List<AnnotatedClass<T>> list = annoClasses.stream()
                .filter(e -> !e.getClazz().isAnnotationPresent(type))
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

}
