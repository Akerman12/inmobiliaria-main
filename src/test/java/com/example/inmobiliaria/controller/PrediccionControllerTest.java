package com.example.inmobiliaria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PrediccionControllerTest {

    @Test
    void incluyeMargenDeErrorCalculadoConDatosDeEntrenamiento() {
        PrediccionController controller = new PrediccionController();
        controller.cargarModelo();

        ResponseEntity<Map<String, Object>> response = controller.predecirPrecio(Map.of(
                "ciudad", "Cartagena",
                "habitaciones", 3,
                "wc", 2,
                "estacionamiento", 1,
                "metrosCuadrados", 90));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(((Number) body.get("precioEstimado")).longValue() > 0);
        assertTrue(((Number) body.get("errorPromedioAbsoluto")).longValue() > 0);
        assertTrue(((Number) body.get("cantidadDatosEntrenamiento")).intValue() > 0);
    }
}
