package com.patientmanagement.common.validation;

public final class ValidationPatterns {

    public static final String PERSON_NAME = "^[\\p{L}][\\p{L}\\p{M}'’.-]*(?:[ \\t]+[\\p{L}][\\p{L}\\p{M}'’.-]*)*$";
    public static final String TEXT_WITH_LETTER = ".*[\\p{L}].*";
    public static final String PHONE = "^(?=(?:.*\\d){7,})\\+?[0-9 .()\\-]{7,25}$";
    public static final String LICENSE_NUMBER = "^[A-Za-z0-9][A-Za-z0-9 ./_-]{2,99}$";

    private ValidationPatterns() {
    }
}
