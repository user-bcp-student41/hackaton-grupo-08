package com.ailabs.registration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record VerifyOtpRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String otp) {

    @Override
    public String toString() {
        return "VerifyOtpRequest[***]";
    }
}
