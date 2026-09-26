package com.patientmanagement.common.web;

import com.patientmanagement.common.exception.InvalidRequestException;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableSortValidator {

    private PageableSortValidator() {
    }

    public static void validate(Pageable pageable, Set<String> allowedSortFields) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort field: " + order.getProperty());
            }
        }
    }
}
