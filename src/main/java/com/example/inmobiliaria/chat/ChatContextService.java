package com.example.inmobiliaria.chat;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

@Service
public class ChatContextService {

    private static final int MAX_ELEMENTOS_LISTA = 25;
    private static final DateTimeFormatter FECHA_HORA_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("es-CO"));
    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");

    private final PropiedadRepository propiedadRepository;
    private final VisitaRepository visitaRepository;
    private final ContactoRepository contactoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TestimonioRepository testimonioRepository;

    public ChatContextService(PropiedadRepository propiedadRepository,
                              VisitaRepository visitaRepository,
                              ContactoRepository contactoRepository,
                              UsuarioRepository usuarioRepository,
                              TestimonioRepository testimonioRepository) {
        this.propiedadRepository = propiedadRepository;
        this.visitaRepository = visitaRepository;
        this.contactoRepository = contactoRepository;
        this.usuarioRepository = usuarioRepository;
        this.testimonioRepository = testimonioRepository;
    }

    public IdentidadChat resolverIdentidad(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new IdentidadChat(ChatRole.VISITANTE, null);
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida.");
        }

        ChatRole rolAutoridad = extraerRolDeAutoridades(authentication);
        Usuario usuario = usuarioRepository.findByUsername(username)
                .filter(Usuario::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida."));

        String rolUsuario = usuario.getRol() == null ? "" : usuario.getRol().trim().toUpperCase(Locale.ROOT);
        if (!rolAutoridad.name().equals(rolUsuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rol de cuenta inconsistente.");
        }

        return new IdentidadChat(rolAutoridad, usuario.getId());
    }

    public ChatContexto construirContexto(Authentication authentication) {
        IdentidadChat identidad = resolverIdentidad(authentication);
        return switch (identidad.rol()) {
            case VISITANTE -> construirContextoVisitante();
            case CLIENTE -> construirContextoCliente(identidad.usuarioId());
            case PROPIETARIO -> construirContextoPropietario(identidad.usuarioId());
            case ADMIN -> construirContextoAdmin(identidad.usuarioId());
        };
    }

    private ChatRole extraerRolDeAutoridades(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            if ("ROLE_ADMIN".equals(value)) {
                return ChatRole.ADMIN;
            }
            if ("ROLE_PROPIETARIO".equals(value)) {
                return ChatRole.PROPIETARIO;
            }
            if ("ROLE_CLIENTE".equals(value)) {
                return ChatRole.CLIENTE;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para usar este asistente.");
    }

    private ChatContexto construirContextoVisitante() {
        List<Propiedad> disponibles = propiedadRepository.findByVisibleTrue().stream()
                .filter(Propiedad::isVisible)
                .filter(p -> "DISPONIBLE".equalsIgnoreCase(p.getEstadoComercial()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Páginas útiles del sitio: Catálogo /anuncio.html | Contacto /contacto.html | Valoración /valoracion.html | Iniciar sesión o registrarse /ingresar.html\n");
        sb.append("Propiedades visibles y disponibles (total ").append(disponibles.size()).append("):\n");

        if (disponibles.isEmpty()) {
            sb.append("- En este momento no hay propiedades disponibles publicadas.\n");
        } else {
            disponibles.stream().limit(MAX_ELEMENTOS_LISTA)
                    .forEach(p -> sb.append(formatearPropiedadPublica(p, false)).append('\n'));
            if (disponibles.size() > MAX_ELEMENTOS_LISTA) {
                sb.append("... y ").append(disponibles.size() - MAX_ELEMENTOS_LISTA)
                        .append(" propiedades más en /anuncio.html.\n");
            }
        }

        String alcance = "Puedes consultar únicamente información pública de propiedades visibles y disponibles "
                + "(título, tipo, operación, precio, ubicación, características y enlace) y orientar hacia "
                + "/anuncio.html, /contacto.html, /valoracion.html o /ingresar.html. "
                + "Debes rechazar cualquier consulta sobre visitas de clientes, contactos privados, datos de propietarios, "
                + "propiedades ocultas o estadísticas internas.";

        return new ChatContexto(ChatRole.VISITANTE, null, alcance, sb.toString().trim());
    }

    private ChatContexto construirContextoCliente(String clienteId) {
        List<Propiedad> visibles = propiedadRepository.findByVisibleTrue().stream()
                .filter(Propiedad::isVisible)
                .toList();
        List<Visita> misVisitas = visitaRepository.findByClienteIdOrderByFechaHoraAsc(clienteId);

        StringBuilder sb = new StringBuilder();
        sb.append("Páginas útiles para cliente: Portal cliente /cliente/index.html | Catálogo /anuncio.html | Favoritos /favoritos.html | Contacto /contacto.html\n");
        sb.append("Propiedades visibles (total ").append(visibles.size()).append("):\n");
        if (visibles.isEmpty()) {
            sb.append("- No hay propiedades visibles en este momento.\n");
        } else {
            visibles.stream().limit(MAX_ELEMENTOS_LISTA)
                    .forEach(p -> sb.append(formatearPropiedadPublica(p, true)).append('\n'));
            if (visibles.size() > MAX_ELEMENTOS_LISTA) {
                sb.append("... y ").append(visibles.size() - MAX_ELEMENTOS_LISTA)
                        .append(" propiedades más en /anuncio.html.\n");
            }
        }

        sb.append("\nMis solicitudes de visita (total ").append(misVisitas.size()).append("):\n");
        if (misVisitas.isEmpty()) {
            sb.append("- No tienes solicitudes de visita registradas. Puedes solicitar una desde /cliente/index.html.\n");
        } else {
            misVisitas.stream().limit(MAX_ELEMENTOS_LISTA).forEach(v -> {
                sb.append("- Propiedad: ").append(limpiarTexto(v.getPropiedadTitulo(), 120))
                        .append(" | Estado: ").append(limpiarTexto(v.getEstado(), 30))
                        .append(" | Fecha solicitada: ").append(formatearFecha(v.getFechaHora()))
                        .append(" | Fecha asignada: ").append(v.getFechaAsignada() != null ? formatearFecha(v.getFechaAsignada()) : "Pendiente");
                if (v.getPropiedadId() != null && !v.getPropiedadId().isBlank()) {
                    sb.append(" | Enlace: /propiedad.html?id=").append(limpiarId(v.getPropiedadId()));
                }
                if (v.getMensaje() != null && !v.getMensaje().isBlank()) {
                    sb.append(" | Nota: ").append(limpiarTexto(v.getMensaje(), 120));
                }
                sb.append('\n');
            });
        }

        String alcance = "Puedes consultar las propiedades visibles y el estado de tus propias solicitudes de visita. "
                + "Para solicitar una nueva visita o gestionar tus favoritos debes usar tu portal (/cliente/index.html) o /favoritos.html. "
                + "Debes rechazar consultas sobre visitas de otros clientes, consultas de contacto de otras personas, "
                + "datos privados de propietarios o métricas de administración.";

        return new ChatContexto(ChatRole.CLIENTE, clienteId, alcance, sb.toString().trim());
    }

    private ChatContexto construirContextoPropietario(String propietarioId) {
        List<Propiedad> misPropiedades = propiedadRepository.findByPropietarioId(propietarioId);
        List<String> propiedadIds = misPropiedades.stream()
                .map(Propiedad::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, String> titulosPorPropiedad = misPropiedades.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(
                        Propiedad::getId,
                        p -> limpiarTexto(p.getTitulo(), 100),
                        (a, b) -> a));

        List<Contacto> consultas = propiedadIds.isEmpty()
                ? List.of()
                : contactoRepository.findByCasaInteresInOrderByFechaCreacionDesc(propiedadIds);
        List<Visita> visitas = propiedadIds.isEmpty()
                ? List.of()
                : visitaRepository.findByPropiedadIdInOrderByFechaHoraAsc(propiedadIds);

        StringBuilder sb = new StringBuilder();
        sb.append("Páginas útiles para propietario: Panel propietario /propietario/index.html\n");
        sb.append("Mis propiedades (total ").append(misPropiedades.size()).append("):\n");
        if (misPropiedades.isEmpty()) {
            sb.append("- Aún no tienes propiedades registradas a tu nombre.\n");
        } else {
            misPropiedades.stream().limit(MAX_ELEMENTOS_LISTA).forEach(p -> {
                sb.append("- ").append(limpiarTexto(p.getTitulo(), 100))
                        .append(" | Operación: ").append(limpiarTexto(p.getOperacion(), 20))
                        .append(" | Tipo: ").append(limpiarTexto(p.getTipo(), 30))
                        .append(" | Precio: ").append(formatearMoneda(p.getPrecio()))
                        .append(" | Ciudad: ").append(limpiarTexto(p.getCiudad(), 60))
                        .append(" | Aprobación: ").append(p.isVisible() ? "Visible/Aprobada" : "En revisión")
                        .append(" | Estado comercial: ").append(limpiarTexto(p.getEstadoComercial(), 30))
                        .append(" | Vistas: ").append(p.getVisitas());
                if (p.getId() != null) {
                    sb.append(" | Enlace: /propiedad.html?id=").append(limpiarId(p.getId()));
                }
                sb.append('\n');
            });
        }

        sb.append("\nConsultas recibidas sobre mis propiedades (total ").append(consultas.size()).append("):\n");
        if (consultas.isEmpty()) {
            sb.append("- No hay consultas registradas para tus propiedades.\n");
        } else {
            consultas.stream().limit(MAX_ELEMENTOS_LISTA).forEach(c -> {
                String tituloProp = titulosPorPropiedad.getOrDefault(c.getCasaInteres(), "Propiedad");
                sb.append("- Propiedad: ").append(tituloProp)
                        .append(" | Interesado: ").append(limpiarTexto(c.getNombre(), 60))
                        .append(" | Medio preferido: ").append(limpiarTexto(c.getMedioContacto(), 20))
                        .append(" | Estado: ").append(c.isAtendido() ? "Atendido" : "Pendiente")
                        .append(" | Fecha: ").append(formatearFecha(c.getFechaCreacion()))
                        .append(" | Mensaje: ").append(limpiarTexto(c.getMensaje(), 140))
                        .append('\n');
            });
        }

        sb.append("\nVisitas solicitadas para mis propiedades (total ").append(visitas.size()).append("):\n");
        if (visitas.isEmpty()) {
            sb.append("- No hay solicitudes de visita para tus propiedades.\n");
        } else {
            visitas.stream().limit(MAX_ELEMENTOS_LISTA).forEach(v -> {
                String tituloProp = v.getPropiedadTitulo() != null && !v.getPropiedadTitulo().isBlank()
                        ? limpiarTexto(v.getPropiedadTitulo(), 100)
                        : titulosPorPropiedad.getOrDefault(v.getPropiedadId(), "Propiedad");
                sb.append("- Propiedad: ").append(tituloProp)
                        .append(" | Cliente: ").append(limpiarTexto(v.getClienteNombre(), 60))
                        .append(" | Estado: ").append(limpiarTexto(v.getEstado(), 30))
                        .append(" | Solicitada: ").append(formatearFecha(v.getFechaHora()))
                        .append(" | Asignada: ").append(v.getFechaAsignada() != null ? formatearFecha(v.getFechaAsignada()) : "Sin asignar");
                if (v.getMensaje() != null && !v.getMensaje().isBlank()) {
                    sb.append(" | Nota: ").append(limpiarTexto(v.getMensaje(), 100));
                }
                sb.append('\n');
            });
        }

        String alcance = "Puedes consultar únicamente el estado de tus propias propiedades (aprobación, estado comercial, vistas y características) "
                + "y las consultas de contacto o solicitudes de visita asociadas a ellas. "
                + "Para publicar, editar propiedades o confirmar horarios de visita debes ir a /propietario/index.html. "
                + "Debes rechazar consultas sobre propiedades de otros propietarios, visitas ajenas o estadísticas globales del sistema.";

        return new ChatContexto(ChatRole.PROPIETARIO, propietarioId, alcance, sb.toString().trim());
    }

    private ChatContexto construirContextoAdmin(String adminId) {
        List<Propiedad> propiedades = propiedadRepository.findAll();
        List<Contacto> contactos = contactoRepository.findAll();
        List<Visita> visitas = visitaRepository.findAll();
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<Testimonio> testimonios = testimonioRepository.findAll();

        long propVisibles = propiedades.stream().filter(Propiedad::isVisible).count();
        long propOcultas = propiedades.size() - propVisibles;
        long propDisponibles = propiedades.stream()
                .filter(p -> "DISPONIBLE".equalsIgnoreCase(p.getEstadoComercial())).count();
        long propEnProceso = propiedades.stream()
                .filter(p -> "EN_PROCESO".equalsIgnoreCase(p.getEstadoComercial())).count();
        long propVenta = propiedades.stream()
                .filter(p -> "venta".equalsIgnoreCase(p.getOperacion())).count();
        long propArriendo = propiedades.stream()
                .filter(p -> "arriendo".equalsIgnoreCase(p.getOperacion())).count();

        Map<String, Long> porCiudad = propiedades.stream()
                .map(p -> p.getCiudad() == null || p.getCiudad().isBlank() ? "Sin ciudad" : limpiarTexto(p.getCiudad(), 50))
                .collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> porTipo = propiedades.stream()
                .map(p -> limpiarTexto(p.getTipo(), 30).toLowerCase(Locale.ROOT))
                .collect(Collectors.groupingBy(t -> t, LinkedHashMap::new, Collectors.counting()));

        long contactosPendientes = contactos.stream().filter(c -> !c.isAtendido()).count();
        long contactosAtendidos = contactos.size() - contactosPendientes;

        Map<String, Long> visitasPorEstado = visitas.stream()
                .map(v -> v.getEstado() == null ? "DESCONOCIDO" : limpiarTexto(v.getEstado(), 30).toUpperCase(Locale.ROOT))
                .collect(Collectors.groupingBy(e -> e, LinkedHashMap::new, Collectors.counting()));

        long usuariosActivos = usuarios.stream().filter(Usuario::isEnabled).count();
        long usuariosInactivos = usuarios.size() - usuariosActivos;
        Map<String, Long> usuariosPorRol = usuarios.stream()
                .map(u -> u.getRol() == null ? "SIN_ROL" : limpiarTexto(u.getRol(), 20).toUpperCase(Locale.ROOT))
                .collect(Collectors.groupingBy(r -> r, LinkedHashMap::new, Collectors.counting()));

        long testimoniosVisibles = testimonios.stream().filter(Testimonio::isVisible).count();

        StringBuilder sb = new StringBuilder();
        sb.append("Páginas del panel administrativo: Dashboard /admin/index.html | Propiedades /admin/propiedades.html | ")
                .append("Contactos /admin/contactos.html | Testimonios /admin/testimonios.html\n");
        sb.append("Resumen general de propiedades:\n")
                .append("- Total: ").append(propiedades.size())
                .append(" | Visibles: ").append(propVisibles)
                .append(" | Pendientes/Ocultas: ").append(propOcultas)
                .append(" | Disponibles: ").append(propDisponibles)
                .append(" | En proceso: ").append(propEnProceso)
                .append(" | Venta: ").append(propVenta)
                .append(" | Arriendo: ").append(propArriendo).append('\n')
                .append("- Distribución por tipo: ").append(formatearConteos(porTipo)).append('\n')
                .append("- Top ciudades: ").append(formatearTopConteos(porCiudad, 8)).append('\n');

        sb.append("Resumen de contactos:\n")
                .append("- Total: ").append(contactos.size())
                .append(" | Pendientes: ").append(contactosPendientes)
                .append(" | Atendidos: ").append(contactosAtendidos).append('\n');

        sb.append("Resumen de visitas:\n")
                .append("- Total: ").append(visitas.size())
                .append(" | Por estado: ").append(formatearConteos(visitasPorEstado)).append('\n');

        sb.append("Resumen de cuentas (solo conteos agregados, sin datos personales ni credenciales):\n")
                .append("- Total usuarios: ").append(usuarios.size())
                .append(" | Activos: ").append(usuariosActivos)
                .append(" | Inactivos: ").append(usuariosInactivos)
                .append(" | Por rol: ").append(formatearConteos(usuariosPorRol)).append('\n');

        sb.append("Resumen de testimonios:\n")
                .append("- Total: ").append(testimonios.size())
                .append(" | Visibles: ").append(testimoniosVisibles);

        String alcance = "Puedes consultar únicamente conteos agregados y resúmenes generales del sistema "
                + "(propiedades, contactos, visitas, usuarios por rol y testimonios). "
                + "Para gestionar o modificar registros debes indicar las vistas de /admin/index.html. "
                + "Nunca debes entregar contraseñas, hashes, correos, teléfonos, nombres personales ni datos individuales de usuarios.";

        return new ChatContexto(ChatRole.ADMIN, adminId, alcance, sb.toString().trim());
    }

    private String formatearPropiedadPublica(Propiedad p, boolean incluirEstadoComercial) {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(limpiarTexto(p.getTitulo(), 100))
                .append(" | ").append("arriendo".equalsIgnoreCase(p.getOperacion()) ? "Arriendo" : "Venta")
                .append(" | Tipo: ").append(limpiarTexto(p.getTipo(), 30))
                .append(" | Precio: ").append(formatearMoneda(p.getPrecio()));

        String ubicacion = List.of(
                        limpiarTexto(p.getBarrio(), 60),
                        limpiarTexto(p.getCiudad(), 60))
                .stream()
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        if (!ubicacion.isBlank()) {
            sb.append(" | Ubicación: ").append(ubicacion);
        }

        sb.append(" | ").append(p.getHabitaciones()).append(" hab, ")
                .append(p.getWc()).append(" baños, ")
                .append(p.getEstacionamiento()).append(" parq, ")
                .append(Math.round(p.getMetrosCuadrados())).append(" m2");

        if (incluirEstadoComercial) {
            sb.append(" | Estado: ").append(limpiarTexto(p.getEstadoComercial(), 30));
        }
        if (p.getId() != null && !p.getId().isBlank()) {
            sb.append(" | Enlace: /propiedad.html?id=").append(limpiarId(p.getId()));
        }
        if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
            sb.append(" | Resumen: ").append(limpiarTexto(p.getDescripcion(), 140));
        }
        return sb.toString();
    }

    private String formatearConteos(Map<String, Long> conteos) {
        if (conteos.isEmpty()) {
            return "Sin registros";
        }
        return conteos.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private String formatearTopConteos(Map<String, Long> conteos, int limite) {
        if (conteos.isEmpty()) {
            return "Sin registros";
        }
        return conteos.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limite)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private String formatearMoneda(double valor) {
        if (!Double.isFinite(valor) || valor <= 0) {
            return "Consultar";
        }
        NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_CO);
        nf.setMaximumFractionDigits(0);
        return nf.format(valor) + " COP";
    }

    private String formatearFecha(LocalDateTime fecha) {
        return fecha == null ? "Sin fecha" : FECHA_HORA_FMT.format(fecha);
    }

    private String limpiarTexto(String valor, int maxLen) {
        if (valor == null) {
            return "";
        }
        String limpio = valor.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (limpio.length() <= maxLen) {
            return limpio;
        }
        return limpio.substring(0, maxLen) + "...";
    }

    private String limpiarId(String id) {
        return id == null ? "" : id.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    public record IdentidadChat(ChatRole rol, String usuarioId) {
    }

    public record ChatContexto(
            ChatRole rol,
            String usuarioId,
            String alcancePermitido,
            String contextoDatos) {
    }
}

