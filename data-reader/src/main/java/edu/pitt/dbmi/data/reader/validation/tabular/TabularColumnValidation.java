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
package edu.pitt.dbmi.data.reader.validation.tabular;

import edu.pitt.dbmi.data.reader.DataReader;
import edu.pitt.dbmi.data.reader.validation.Validation;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import java.util.List;
import java.util.Set;

/**
 *
 * Dec 12, 2018 2:34:56 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface TabularColumnValidation extends Validation, DataReader {

    public List<ValidationResult> validate();

    public List<ValidationResult> validate(int[] excludedColumns);

    public List<ValidationResult> validate(Set<String> excludedColumns);

}
