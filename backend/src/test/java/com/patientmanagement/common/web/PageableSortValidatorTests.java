package com.patientmanagement.common.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patientmanagement.common.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageableSortValidatorTests {

    @Test
    void allowedSortFieldPassesValidation() {
        PageableSortValidator.validate(
                PageRequest.of(0, 20, Sort.by("createdAt")),
                Set.of("createdAt", "updatedAt")
        );
    }

    @Test
    void unsupportedSortFieldIsRejected() {
        assertThatThrownBy(() -> PageableSortValidator.validate(
                PageRequest.of(0, 20, Sort.by("passwordHash")),
                Set.of("createdAt", "updatedAt")
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported sort field: passwordHash");
    }

    @Test
    void nestedSortFieldIsRejected() {
        assertThatThrownBy(() -> PageableSortValidator.validate(
                PageRequest.of(0, 20, Sort.by("doctor.passwordHash")),
                Set.of("createdAt", "updatedAt")
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported sort field: doctor.passwordHash");
    }
}
