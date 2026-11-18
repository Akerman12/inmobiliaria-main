package com.example.inmobiliaria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.example.inmobiliaria.Service.PlanificadorVisitasService;
import com.example.inmobiliaria.model.DisponibilidadVisita;
import com.example.inmobiliaria.model.PlanVisitas;
import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.model.Visita;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.DisponibilidadVisitaRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;
import com.example.inmobiliaria.repository.VisitaRepository;

class PropietarioControllerTest {

    private final PropiedadRepository propiedadRepository = mock(PropiedadRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final VisitaRepository visitaRepository = mock(VisitaRepository.class);
    private final DisponibilidadVisitaRepository disponibilidadVisitaRepository =
            mock(DisponibilidadVisitaRepository.class);
    private final ImageStorageService imageStorageService = mock(ImageStorageService.class);
    private final PropietarioController controller = new PropietarioController(
            propiedadRepository, usuarioRepository, mock(ContactoRepository.class),
            visitaRepository, disponibilidadVisitaRepository,
            imageStorageService, new PlanificadorVisitasService());

    @BeforeEach
    void autenticarPropietario() {
        Usuario propietario = new Usuario();
        propietario.setId("owner-id");
        propietario.setUsername("owner@example.com");
        propietario.setRol("PROPIETARIO");
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

    @Test
    void planificaSoloSolicitudesYHorariosDelPropietarioAutenticado() {
        LocalDateTime fecha = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
        Visita visita = new Visita();
        visita.setId("visit-id");
        visita.setPropietarioId("owner-id");
        visita.setEstado("SOLICITADA");
        visita.setFechaHora(fecha);
        visita.setFechaSolicitud(LocalDateTime.now());
        DisponibilidadVisita horario = new DisponibilidadVisita();
        horario.setId("slot-id");
        horario.setPropietarioId("owner-id");
        horario.setFechaHora(fecha);
        when(visitaRepository.findByPropietarioIdOrderByFechaHoraAsc("owner-id"))
                .thenReturn(List.of(visita));
        when(disponibilidadVisitaRepository
                .findByPropietarioIdAndReservadaFalseAndFechaHoraAfterOrderByFechaHoraAsc(
                        any(), any()))
                .thenReturn(List.of(horario));

        var plan = controller.planificarVisitas();

        assertEquals(1, plan.solicitudesAsignadas());
        assertEquals("visit-id", plan.asignaciones().get(0).visitaId());
        assertEquals("slot-id", plan.asignaciones().get(0).disponibilidadId());
    }

    @Test
    void confirmaLaPropuestaYReservaElHorario() {
        LocalDateTime fecha = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        Visita visita = new Visita();
        visita.setId("confirm-visit-id");
        visita.setPropietarioId("owner-id");
        visita.setEstado("SOLICITADA");
        visita.setFechaHora(fecha);
        visita.setFechaSolicitud(LocalDateTime.now());
        DisponibilidadVisita horario = new DisponibilidadVisita();
        horario.setId("confirm-slot-id");
        horario.setPropietarioId("owner-id");
        horario.setFechaHora(fecha);

        when(visitaRepository.findByPropietarioIdOrderByFechaHoraAsc("owner-id"))
                .thenReturn(List.of(visita));
        when(disponibilidadVisitaRepository
                .findByPropietarioIdAndReservadaFalseAndFechaHoraAfterOrderByFechaHoraAsc(
                        any(), any()))
                .thenReturn(List.of(horario));
        when(visitaRepository.findById("confirm-visit-id")).thenReturn(Optional.of(visita));
        when(disponibilidadVisitaRepository.findById("confirm-slot-id")).thenReturn(Optional.of(horario));

        PlanVisitas resultado = controller.confirmarPlan(new PlanVisitas.Confirmacion(List.of(
                new PlanVisitas.AsignacionConfirmada("confirm-visit-id", "confirm-slot-id"))));

        assertEquals(1, resultado.solicitudesAsignadas());
        assertEquals("CONFIRMADA", visita.getEstado());
        assertEquals(fecha, visita.getFechaAsignada());
        assertEquals("confirm-slot-id", visita.getDisponibilidadId());
        assertTrue(horario.isReservada());
        verify(visitaRepository).save(visita);
        verify(disponibilidadVisitaRepository).save(horario);
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
