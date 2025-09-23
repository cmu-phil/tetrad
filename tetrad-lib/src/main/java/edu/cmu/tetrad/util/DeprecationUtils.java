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

package edu.cmu.tetrad.util;

/**
 * Utility for checking whether classes are marked {@code @Deprecated}.
 */
public final class DeprecationUtils {

    private DeprecationUtils() {
        // prevent instantiation
    }

    /**
     * Determines if the specified class is marked with the {@code @Deprecated} annotation.
     *
     * @param clazz the class to check; must not be null
     * @return {@code true} if the class is annotated with {@code @Deprecated}, {@code false} otherwise
     * @throws IllegalArgumentException if the provided class is null
     */
    public static boolean isClassDeprecated(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class must not be null");
        }
        return clazz.isAnnotationPresent(Deprecated.class);
    }
}
