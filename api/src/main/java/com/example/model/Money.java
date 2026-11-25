package com.example.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * All money is BigDecimal at scale 2. Anything with more than two decimal places is rejected
 * rather than rounded, because silently rounding someone's money is a bug.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        // UNNECESSARY throws ArithmeticException if rounding would be needed
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount has more than " + SCALE + " decimal places: " + amount);
        }
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }
}
