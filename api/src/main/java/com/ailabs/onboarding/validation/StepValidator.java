package com.ailabs.onboarding.validation;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de validación de pasos (EP-HU1_2-03): aísla al proveedor de validación. El servicio de
 * orquestación solo depende de esta interfaz, por lo que cambiar la implementación no altera el contrato REST.
 */
public interface StepValidator {

    /** Catálogo ordenado de pasos requeridos. */
    List<StepDefinition> catalog();

    /**
     * Valida los datos de un paso.
     *
     * @return lista vacía si los datos son válidos; en otro caso, los errores accionables.
     * @throws ValidationUnavailableException si el proveedor no está disponible.
     */
    List<FieldError> validate(String stepCode, Map<String, Object> data);
}
