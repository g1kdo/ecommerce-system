package rw.smart.ecommerce.utils.validation;

import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.util.regex.Pattern;

public class RegexValidator {
    private static final Pattern USER_EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");


    public static void validateUserEmail(String email) {
        if (email == null || !USER_EMAIL_PATTERN.matcher(email).matches())
            throw new InvalidInputException("Invalid User email format (e.g., example@gmail.com)");
    }
}
