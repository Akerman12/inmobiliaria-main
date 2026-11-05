package com.example.inmobiliaria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Testimonio;
import com.example.inmobiliaria.repository.TestimonioRepository;

class TestimonioRestControllerTest {

    private final TestimonioRepository repository = mock(TestimonioRepository.class);
    private final TestimonioRestController controller = new TestimonioRestController(repository);

    @Test
    void rechazaTestimonioConMasDeCincoEstrellas() {
        Testimonio testimonio = new Testimonio();
        testimonio.setNombre("Ana Perez");
        testimonio.setMensaje("Excelente servicio");
        testimonio.setEstrellas(6);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> controller.crear(testimonio));

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void normalizaCamposAntesDeGuardarTestimonio() {
        Testimonio testimonio = new Testimonio();
        testimonio.setNombre("  Ana Perez  ");
        testimonio.setRol("  Cliente  ");
        testimonio.setMensaje("  Excelente servicio  ");
        testimonio.setEstrellas(5);
        when(repository.save(testimonio)).thenReturn(testimonio);

        Testimonio guardado = controller.crear(testimonio);

        assertEquals("Ana Perez", guardado.getNombre());
        assertEquals("Cliente", guardado.getRol());
        assertEquals("Excelente servicio", guardado.getMensaje());
        verify(repository).save(testimonio);
    }
}
