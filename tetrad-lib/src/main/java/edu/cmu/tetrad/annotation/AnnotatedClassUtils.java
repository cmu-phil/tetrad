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

import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sep 6, 2017 11:11:38 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class AnnotatedClassUtils {

    private AnnotatedClassUtils() {
    }

    /**
     * Gets a list of annotated classes in the given package.
     *
     * @param packageName package name
     * @param type        annotation type
     * @param <T>         annotation type.
     * @return list of annotated classes
     */
    public static <T extends Annotation> List<AnnotatedClass<T>> getAnnotatedClasses(String packageName, Class<T> type) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(type);

        return classes.stream()
                .map(e -> new AnnotatedClass<>(e, e.getAnnotation(type)))
                .collect(Collectors.toList());
    }

    /**
     * Filters a list of annotated classes by the given annotation.
     *
     * @param annotation       annotation
     * @param annotatedClasses list of annotated classes
     * @param <T>              annotation type.
     * @return list of annotated classes
     */
    public static <T extends Annotation> List<AnnotatedClass<T>> filterByAnnotations(Class<? extends Annotation> annotation, List<AnnotatedClass<T>> annotatedClasses) {
        List<AnnotatedClass<T>> list = new LinkedList<>();

        if (annotatedClasses != null && !annotatedClasses.isEmpty()) {
            annotatedClasses.stream()
                    .filter(e -> e.clazz().isAnnotationPresent(annotation))
                    .collect(Collectors.toCollection(() -> list));
        }

        return list;
    }

}

