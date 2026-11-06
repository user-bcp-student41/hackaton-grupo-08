package com.ailabs.registration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Cuerpo del Paso 1. Los textos se deserializan solo desde strings JSON (preserva ceros). toString sin secretos. */
public record RegisterRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class) String documentType,
        @JsonDeserialize(using = StrictStringDeserializer.class) String documentNumber,
        @JsonDeserialize(using = StrictStringDeserializer.class) String email,
        @JsonDeserialize(using = StrictStringDeserializer.class) String password,
        @JsonDeserialize(using = StrictStringDeserializer.class) String acquisitionSource) {

    @Override
    public String toString() {
        return "RegisterRequest[***]";
    }
}
