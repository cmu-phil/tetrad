/*
 * Copyright (C) 2018 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.validation;

/**
 *
 * Feb 16, 2017 1:49:30 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public enum ValidationAttribute {

    CONTINUOUS_VAR_COUNT,
    DISCRETE_VAR_COUNT,
    LINE_NUMBER,
    COLUMN_NUMBER,
    ROW_NUMBER,
    EXPECTED_COUNT,
    ACTUAL_COUNT,
    LINE_COUNT,
    COLUMN_COUNT,
    ROW_COUNT,
    ASSUMED_MISSING_COUNT,
    LABELED_MISSING_COUNT,
    ROW_WITH_MISSING_VALUE_COUNT,
    COLUMN_WITH_MISSING_VALUE_COUNT,
    FILE_NAME,
    VALUE

}
