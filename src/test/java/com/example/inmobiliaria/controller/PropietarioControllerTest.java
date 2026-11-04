package com.example.inmobiliaria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.Service.ImageStorageService;
import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;
import com.example.inmobiliaria.repository.VisitaRepository;

class PropietarioControllerTest {

    private final PropiedadRepository propiedadRepository = mock(PropiedadRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ImageStorageService imageStorageService = mock(ImageStorageService.class);
    private final PropietarioController controller = new PropietarioController(
            propiedadRepository, usuarioRepository, mock(ContactoRepository.class),
            mock(VisitaRepository.class), imageStorageService);

    @BeforeEach
    void autenticarPropietario() {
        Usuario propietario = new Usuario();
        propietario.setId("owner-id");
        propietario.setUsername("owner@example.com");
        when(usuarioRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(propietario));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", "credential",
                        List.of(new SimpleGrantedAuthority("ROLE_PROPIETARIO"))));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicaSoloComoBorradorPropioPendienteDeRevision() throws Exception {
        when(imageStorageService.store(any(), any(), any(), any(Integer.class))).thenReturn("image-id");
        when(propiedadRepository.save(any(Propiedad.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        Propiedad solicitud = propiedadValida();
        MockMultipartFile imagen = new MockMultipartFile(
                "imagenes", "casa.jpg", "image/jpeg", new byte[]{1, 2, 3});

        var response = controller.publicarPropiedad(solicitud, new MockMultipartFile[]{imagen});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("owner-id", response.getBody().getPropietarioId());
        assertFalse(response.getBody().isVisible());
        assertEquals(List.of("/api/imagenes/image-id"), response.getBody().getImagenes());
        verify(propiedadRepository).save(any(Propiedad.class));
    }

    @Test
    void impideEditarUnaPropiedadDeOtroPropietario() {
        Propiedad propiedad = propiedadValida();
        propiedad.setId("property-id");
        propiedad.setPropietarioId("other-owner");
        when(propiedadRepository.findById("property-id")).thenReturn(Optional.of(propiedad));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarPublicacion("property-id", propiedadValida(), null));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private Propiedad propiedadValida() {
        Propiedad propiedad = new Propiedad();
        propiedad.setTitulo("Apartamento luminoso");
        propiedad.setDescripcion("Descripción de la propiedad.");
        propiedad.setOperacion("venta");
        propiedad.setTipo("apartamento");
        propiedad.setPrecio(250_000_000);
        propiedad.setMetrosCuadrados(80);
        propiedad.setHabitaciones(3);
        propiedad.setWc(2);
        propiedad.setEstacionamiento(1);
        propiedad.setCiudad("Bogotá");
        propiedad.setDireccion("Calle 1");
        return propiedad;
    }
}
