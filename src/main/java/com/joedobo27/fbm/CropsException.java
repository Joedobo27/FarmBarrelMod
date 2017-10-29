package com.joedobo27.fbm;

import java.util.function.Supplier;

public class CropsException extends RuntimeException implements Supplier<CropsException> {

    CropsException(String message) {
        super(message);
    }

    @Override
    public CropsException get() {
        return this;
    }
}
