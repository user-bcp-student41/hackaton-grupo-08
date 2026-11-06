package com.ailabs.registration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record LoginRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class) String documentType,
        @JsonDeserialize(using = StrictStringDeserializer.class) String documentNumber,
        @JsonDeserialize(using = StrictStringDeserializer.class) String password) {

    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
