package com.example.inmobiliaria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Contacto;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;

class ContactoControllerTest {

    private final ContactoRepository contactoRepository = mock(ContactoRepository.class);
    private final PropiedadRepository propiedadRepository = mock(PropiedadRepository.class);
    private final ContactoController controller =
            new ContactoController(contactoRepository, propiedadRepository);

    @Test
    void rechazaContactoConCorreoInvalido() {
        Contacto contacto = new Contacto();
        contacto.setNombre("Ana Perez");
        contacto.setCorreo("correo-invalido");
        contacto.setMensaje("Estoy interesada");
        contacto.setMedioContacto("correo");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> controller.guardarContacto(contacto));

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(contactoRepository, propiedadRepository);
    }

    @Test
    void normalizaContactoYFuerzaEstadoPendiente() {
        Contacto contacto = new Contacto();
        contacto.setNombre("  Ana Perez  ");
        contacto.setCorreo("  ana@example.com  ");
        contacto.setCelular("+57 300 123 4567");
        contacto.setMensaje("  Estoy interesada  ");
        contacto.setMedioContacto("correo");
        contacto.setAtendido(true);
        contacto.setId("forged-id");
        when(contactoRepository.save(contacto)).thenReturn(contacto);

        Contacto guardado = controller.guardarContacto(contacto);

        assertEquals("Ana Perez", guardado.getNombre());
        assertEquals("ana@example.com", guardado.getCorreo());
        assertEquals("Estoy interesada", guardado.getMensaje());
        assertNull(guardado.getId());
        assertFalse(guardado.isAtendido());
        verify(contactoRepository).save(contacto);
    }
}
