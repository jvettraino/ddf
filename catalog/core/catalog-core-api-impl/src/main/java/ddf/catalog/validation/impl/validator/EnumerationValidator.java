/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.validation.impl.validator;

import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;

import ddf.catalog.data.Attribute;
import ddf.catalog.validation.AttributeValidator;
import ddf.catalog.validation.impl.report.AttributeValidationReportImpl;
import ddf.catalog.validation.impl.violation.ValidationViolationImpl;
import ddf.catalog.validation.report.AttributeValidationReport;
import ddf.catalog.validation.violation.ValidationViolation.Severity;

/**
 * Validates an attribute's value(s) against a set of acceptable values.
 */
public class EnumerationValidator implements AttributeValidator {
    private final Set<String> values;

    /**
     * Constructs an {@code EnumerationValidator} with a given set of acceptable values.
     *
     * @param values the values accepted by this validator
     * @throws IllegalArgumentException if {@code values} is null or empty
     */
    public EnumerationValidator(final Set<String> values) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(values),
                "Must specify at least one possible enumeration value.");

        this.values = values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Validates each of {@code attribute}'s values against the set of acceptable values by calling
     * {@link String#valueOf(Object)} on each value and checking whether that string is in the set.
     * <p>
     * Note: comparisons are <strong>case-sensitive</strong>.
     */
    @Override
    public Optional<AttributeValidationReport> validate(final Attribute attribute) {
        Preconditions.checkArgument(attribute != null, "The attribute cannot be null.");

        final String name = attribute.getName();
        for (final Serializable value : attribute.getValues()) {
            final String stringValue = String.valueOf(value);
            if (!values.contains(stringValue)) {
                final AttributeValidationReportImpl report = new AttributeValidationReportImpl();
                // TODO (jrnorth) - escape the value.
                report.addViolation(new ValidationViolationImpl(Collections.singleton(name),
                        name + " has an invalid value: [" + stringValue + "]",
                        Severity.ERROR));
                values.forEach(report::addSuggestedValue);
                return Optional.of(report);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EnumerationValidator that = (EnumerationValidator) o;

        return new EqualsBuilder().append(values, that.values)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(23, 37).append(values)
                .toHashCode();
    }
}
