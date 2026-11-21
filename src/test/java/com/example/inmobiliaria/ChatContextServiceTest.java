package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.chat.ChatContextService;
import com.example.inmobiliaria.chat.ChatContextService.ChatContexto;
import com.example.inmobiliaria.chat.ChatRole;
import com.example.inmobiliaria.model.Contacto;
import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Testimonio;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.model.Visita;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.TestimonioRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;
import com.example.inmobiliaria.repository.VisitaRepository;

class ChatContextServiceTest {

    private PropiedadRepository propiedadRepository;
    private VisitaRepository visitaRepository;
    private ContactoRepository contactoRepository;
    private UsuarioRepository usuarioRepository;
    private TestimonioRepository testimonioRepository;
    private ChatContextService service;

    @BeforeEach
    void setUp() {
        propiedadRepository = mock(PropiedadRepository.class);
        visitaRepository = mock(VisitaRepository.class);
        contactoRepository = mock(ContactoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        testimonioRepository = mock(TestimonioRepository.class);
        service = new ChatContextService(
                propiedadRepository,
                visitaRepository,
                contactoRepository,
                usuarioRepository,
                testimonioRepository);
    }

    @Test
    void visitanteSoloVePropiedadesVisiblesYDisponiblesSinDatosPersonales() {
        Propiedad disponible = propiedad("p1", "Casa Cartagena", true, "DISPONIBLE", "prop-owner-1");
        Propiedad enProceso = propiedad("p2", "Apto Reservado", true, "EN_PROCESO", "prop-owner-2");
        Propiedad oculta = propiedad("p3", "Casa Oculta", false, "DISPONIBLE", "prop-owner-3");
        when(propiedadRepository.findByVisibleTrue()).thenReturn(List.of(disponible, enProceso, oculta));

        Authentication anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        ChatContexto contexto = service.construirContexto(anon);

        assertEquals(ChatRole.VISITANTE, contexto.rol());
        assertNull(contexto.usuarioId());
        assertTrue(contexto.contextoDatos().contains("Casa Cartagena"));
        assertFalse(contexto.contextoDatos().contains("Apto Reservado"));
        assertFalse(contexto.contextoDatos().contains("Casa Oculta"));
        assertFalse(contexto.contextoDatos().contains("prop-owner-1"));
        verify(visitaRepository, never()).findAll();
        verify(contactoRepository, never()).findAll();
    }

    @Test
    void clienteSoloConsultaSusPropiasVisitasYPropiedadesVisibles() {
        Usuario cliente = usuario("cli-1", "cliente@demo.com", "CLIENTE", true);
        when(usuarioRepository.findByUsername("cliente@demo.com")).thenReturn(Optional.of(cliente));

        Propiedad visible = propiedad("p1", "Loft Bocagrande", true, "DISPONIBLE", "owner-1");
        when(propiedadRepository.findByVisibleTrue()).thenReturn(List.of(visible));

        Visita miVisita = new Visita();
        miVisita.setId("v1");
        miVisita.setPropiedadId("p1");
        miVisita.setPropiedadTitulo("Loft Bocagrande");
        miVisita.setClienteId("cli-1");
        miVisita.setEstado("CONFIRMADA");
        miVisita.setFechaHora(LocalDateTime.of(2026, 12, 10, 10, 0));
        when(visitaRepository.findByClienteIdOrderByFechaHoraAsc("cli-1")).thenReturn(List.of(miVisita));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "cliente@demo.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));

        ChatContexto contexto = service.construirContexto(auth);

        assertEquals(ChatRole.CLIENTE, contexto.rol());
        assertEquals("cli-1", contexto.usuarioId());
        assertTrue(contexto.contextoDatos().contains("Loft Bocagrande"));
        assertTrue(contexto.contextoDatos().contains("CONFIRMADA"));
        verify(visitaRepository).findByClienteIdOrderByFechaHoraAsc("cli-1");
        verify(visitaRepository, never()).findAll();
        verify(contactoRepository, never()).findAll();
    }

    @Test
    void propietarioSoloVeSusPropiedadesYConsultasOVisitasAsociadas() {
        Usuario propietario = usuario("prop-1", "prop@demo.com", "PROPIETARIO", true);
        when(usuarioRepository.findByUsername("prop@demo.com")).thenReturn(Optional.of(propietario));

        Propiedad mia = propiedad("p10", "Villa Manga", true, "DISPONIBLE", "prop-1");
        when(propiedadRepository.findByPropietarioId("prop-1")).thenReturn(List.of(mia));

        Contacto consulta = new Contacto();
        consulta.setCasaInteres("p10");
        consulta.setNombre("Laura Pérez");
        consulta.setMedioContacto("correo");
        consulta.setMensaje("¿Tiene parqueadero doble?");
        when(contactoRepository.findByCasaInteresInOrderByFechaCreacionDesc(List.of("p10")))
                .thenReturn(List.of(consulta));

        Visita visita = new Visita();
        visita.setPropiedadId("p10");
        visita.setPropiedadTitulo("Villa Manga");
        visita.setClienteNombre("Carlos Ruiz");
        visita.setEstado("SOLICITADA");
        visita.setFechaHora(LocalDateTime.of(2026, 12, 12, 15, 0));
        when(visitaRepository.findByPropiedadIdInOrderByFechaHoraAsc(List.of("p10")))
                .thenReturn(List.of(visita));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "prop@demo.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_PROPIETARIO")));

        ChatContexto contexto = service.construirContexto(auth);

        assertEquals(ChatRole.PROPIETARIO, contexto.rol());
        assertEquals("prop-1", contexto.usuarioId());
        assertTrue(contexto.contextoDatos().contains("Villa Manga"));
        assertTrue(contexto.contextoDatos().contains("Laura Pérez"));
        assertTrue(contexto.contextoDatos().contains("Carlos Ruiz"));
        verify(propiedadRepository).findByPropietarioId("prop-1");
        verify(propiedadRepository, never()).findAll();
        verify(contactoRepository, never()).findAll();
        verify(visitaRepository, never()).findAll();
    }

    @Test
    void propietarioSinPropiedadesNoConsultaContactosNiVisitas() {
        Usuario propietario = usuario("prop-vacio", "vacio@demo.com", "PROPIETARIO", true);
        when(usuarioRepository.findByUsername("vacio@demo.com")).thenReturn(Optional.of(propietario));
        when(propiedadRepository.findByPropietarioId("prop-vacio")).thenReturn(List.of());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "vacio@demo.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_PROPIETARIO")));

        ChatContexto contexto = service.construirContexto(auth);

        assertEquals(ChatRole.PROPIETARIO, contexto.rol());
        verify(contactoRepository, never()).findByCasaInteresInOrderByFechaCreacionDesc(anyList());
        verify(visitaRepository, never()).findByPropiedadIdInOrderByFechaHoraAsc(anyList());
    }

    @Test
    void adminSoloRecibeConteosYNuncaPasswordsHashesNiDatosSensibles() {
        Usuario admin = usuario("adm-1", "admin", "ADMIN", true);
        admin.setPassword("$2a$10$superSecretHashValue");
        admin.setCelular("3001112233");

        Usuario otro = usuario("u-2", "secreto@correo.com", "CLIENTE", true);
        otro.setNombre("Nombre Muy Privado");
        otro.setPassword("$2a$10$otroHashPrivado");
        otro.setCelular("3119998877");

        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findAll()).thenReturn(List.of(admin, otro));
        when(propiedadRepository.findAll()).thenReturn(List.of(
                propiedad("p1", "Casa Norte", true, "DISPONIBLE", "u-9")));

        Contacto contactoPrivado = new Contacto();
        contactoPrivado.setNombre("Contacto Confidencial");
        contactoPrivado.setCorreo("privado@ejemplo.com");
        contactoPrivado.setCelular("3205554444");
        when(contactoRepository.findAll()).thenReturn(List.of(contactoPrivado));

        Visita visitaPrivada = new Visita();
        visitaPrivada.setEstado("SOLICITADA");
        visitaPrivada.setClienteNombre("Visitante Confidencial");
        visitaPrivada.setClienteEmail("visitante@ejemplo.com");
        when(visitaRepository.findAll()).thenReturn(List.of(visitaPrivada));

        Testimonio testimonio = new Testimonio();
        testimonio.setVisible(true);
        when(testimonioRepository.findAll()).thenReturn(List.of(testimonio));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ChatContexto contexto = service.construirContexto(auth);

        assertEquals(ChatRole.ADMIN, contexto.rol());
        String datos = contexto.contextoDatos();
        assertTrue(datos.contains("Total usuarios: 2"));
        assertTrue(datos.contains("ADMIN=1"));
        assertTrue(datos.contains("CLIENTE=1"));
        assertFalse(datos.contains("$2a$"));
        assertFalse(datos.contains("superSecretHashValue"));
        assertFalse(datos.contains("secreto@correo.com"));
        assertFalse(datos.contains("Nombre Muy Privado"));
        assertFalse(datos.contains("3001112233"));
        assertFalse(datos.contains("Contacto Confidencial"));
        assertFalse(datos.contains("privado@ejemplo.com"));
        assertFalse(datos.contains("Visitante Confidencial"));
    }

    @Test
    void rechazaCuentaDeshabilitadaConUnauthorized() {
        Usuario inactivo = usuario("u1", "inactivo@demo.com", "CLIENTE", false);
        when(usuarioRepository.findByUsername("inactivo@demo.com")).thenReturn(Optional.of(inactivo));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "inactivo@demo.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.construirContexto(auth));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    private Propiedad propiedad(String id, String titulo, boolean visible, String estado, String propietarioId) {
        Propiedad p = new Propiedad();
        p.setId(id);
        p.setTitulo(titulo);
        p.setVisible(visible);
        p.setEstadoComercial(estado);
        p.setPropietarioId(propietarioId);
        p.setOperacion("venta");
        p.setTipo("casa");
        p.setPrecio(450_000_000);
        p.setCiudad("Cartagena");
        p.setBarrio("Manga");
        p.setHabitaciones(3);
        p.setWc(2);
        p.setEstacionamiento(1);
        p.setMetrosCuadrados(140);
        return p;
    }

    private Usuario usuario(String id, String username, String rol, boolean enabled) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username);
        u.setNombre("Usuario " + id);
        u.setRol(rol);
        u.setEnabled(enabled);
        return u;
    }
}

