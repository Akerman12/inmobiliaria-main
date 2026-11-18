package com.example.inmobiliaria.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

@RestController
@RequestMapping("/api/propietario")
public class PropietarioController {

    private final PropiedadRepository propiedadRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContactoRepository contactoRepository;
    private final VisitaRepository visitaRepository;
    private final DisponibilidadVisitaRepository disponibilidadVisitaRepository;
    private final ImageStorageService imageStorageService;
    private final PlanificadorVisitasService planificadorVisitasService;

    public PropietarioController(PropiedadRepository propiedadRepository,
                                 UsuarioRepository usuarioRepository,
                                 ContactoRepository contactoRepository,
                                 VisitaRepository visitaRepository,
                                 DisponibilidadVisitaRepository disponibilidadVisitaRepository,
                                 ImageStorageService imageStorageService,
                                 PlanificadorVisitasService planificadorVisitasService) {
        this.propiedadRepository = propiedadRepository;
        this.usuarioRepository = usuarioRepository;
        this.contactoRepository = contactoRepository;
        this.visitaRepository = visitaRepository;
        this.disponibilidadVisitaRepository = disponibilidadVisitaRepository;
        this.imageStorageService = imageStorageService;
        this.planificadorVisitasService = planificadorVisitasService;
    }

    @GetMapping("/propiedades")
    public List<Propiedad> misPropiedades() {
        Usuario propietario = usuarioActual();
        List<Propiedad> propiedades = propiedadRepository.findByPropietarioId(propietario.getId());

        if (propiedades.isEmpty() && "propietario".equalsIgnoreCase(propietario.getUsername())) {
            return propiedadRepository.findAll();
        }

        return propiedades;
    }

    @PostMapping(value = "/propiedades", consumes = "multipart/form-data")
    public ResponseEntity<Propiedad> publicarPropiedad(
            @RequestPart("propiedad") Propiedad solicitud,
            @RequestPart(value = "imagenes", required = false) MultipartFile[] imagenes) throws IOException {
        validarPropiedad(solicitud);
        Usuario propietario = usuarioActual();
        List<MultipartFile> archivos = archivosValidos(imagenes, true);
        String id = new ObjectId().toHexString();
        List<String> rutas = guardarImagenes(archivos, id, solicitud.getTitulo());

        Propiedad propiedad = copiarDatosPublicables(solicitud);
        propiedad.setId(id);
        propiedad.setPropietarioId(propietario.getId());
        propiedad.setVisible(false);
        propiedad.setEstadoComercial("DISPONIBLE");
        propiedad.setVisitas(0);
        propiedad.setImagenes(rutas);

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(propiedadRepository.save(propiedad));
        } catch (RuntimeException error) {
            eliminarImagenes(rutas);
            throw error;
        }
    }

    @PutMapping(value = "/propiedades/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Propiedad> actualizarPublicacion(
            @PathVariable String id,
            @RequestPart("propiedad") Propiedad solicitud,
            @RequestPart(value = "imagenes", required = false) MultipartFile[] imagenes) throws IOException {
        validarPropiedad(solicitud);
        Usuario propietario = usuarioActual();
        Propiedad existente = propiedadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada."));
        verificarPropiedadPropia(propietario, existente);

        List<MultipartFile> archivos = archivosValidos(imagenes, false);
        List<String> rutasNuevas = archivos.isEmpty()
                ? existente.getImagenes()
                : guardarImagenes(archivos, existente.getId(), solicitud.getTitulo());
        List<String> rutasAnteriores = existente.getImagenes();
        copiarDatosPublicables(solicitud, existente);
        existente.setImagenes(rutasNuevas);
        existente.setVisible(false);

        try {
            Propiedad guardada = propiedadRepository.save(existente);
            if (!archivos.isEmpty()) eliminarImagenes(rutasAnteriores);
            return ResponseEntity.ok(guardada);
        } catch (RuntimeException error) {
            if (!archivos.isEmpty()) eliminarImagenes(rutasNuevas);
            throw error;
        }
    }

    @GetMapping("/consultas")
    public List<com.example.inmobiliaria.model.Contacto> misConsultas() {
        Usuario propietario = usuarioActual();
        List<String> propiedadIds = propiedadRepository.findByPropietarioId(propietario.getId()).stream()
                .map(Propiedad::getId)
                .toList();

        if (propiedadIds.isEmpty()) {
            return List.of();
        }

        return contactoRepository.findByCasaInteresInOrderByFechaCreacionDesc(propiedadIds);
    }

    @GetMapping("/visitas")
    public List<Visita> misVisitas() {
        Usuario propietario = usuarioActual();
        return visitaRepository.findByPropietarioIdOrderByFechaHoraAsc(propietario.getId());
    }

    @GetMapping("/visitas/disponibilidad")
    public List<DisponibilidadVisita> misHorariosDisponibles() {
        Usuario propietario = usuarioActual();
        return disponibilidadVisitaRepository.findByPropietarioIdAndFechaHoraAfterOrderByFechaHoraAsc(
                propietario.getId(), LocalDateTime.now());
    }

    @PostMapping("/visitas/disponibilidad")
    public ResponseEntity<DisponibilidadVisita> agregarHorarioDisponible(
            @RequestBody SolicitudDisponibilidad solicitud) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusDays(90);
        if (solicitud == null || solicitud.fechaHora() == null
                || !solicitud.fechaHora().isAfter(ahora)
                || solicitud.fechaHora().isAfter(limite)
                || solicitud.fechaHora().getMinute() != 0
                || solicitud.fechaHora().getSecond() != 0
                || solicitud.fechaHora().getNano() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El horario debe iniciar en una hora exacta futura, dentro de los próximos 90 días.");
        }

        Usuario propietario = usuarioActual();
        if (disponibilidadVisitaRepository.existsByPropietarioIdAndFechaHora(
                propietario.getId(), solicitud.fechaHora())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraste ese horario disponible.");
        }

        DisponibilidadVisita disponibilidad = new DisponibilidadVisita();
        disponibilidad.setPropietarioId(propietario.getId());
        disponibilidad.setFechaHora(solicitud.fechaHora());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disponibilidadVisitaRepository.save(disponibilidad));
    }

    @DeleteMapping("/visitas/disponibilidad/{id}")
    public ResponseEntity<Void> eliminarHorarioDisponible(@PathVariable String id) {
        Usuario propietario = usuarioActual();
        DisponibilidadVisita disponibilidad = disponibilidadVisitaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horario no encontrado."));
        if (!propietario.getId().equals(disponibilidad.getPropietarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar un horario ajeno.");
        }
        if (disponibilidad.isReservada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El horario ya está asignado a una visita.");
        }
        disponibilidadVisitaRepository.delete(disponibilidad);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/visitas/planificar")
    public PlanVisitas planificarVisitas() {
        Usuario propietario = usuarioActual();
        LocalDateTime ahora = LocalDateTime.now();
        List<Visita> visitas = visitaRepository.findByPropietarioIdOrderByFechaHoraAsc(propietario.getId());
        Set<LocalDateTime> horasOcupadas = visitas.stream()
                .filter(visita -> "CONFIRMADA".equalsIgnoreCase(visita.getEstado())
                        || "COMPLETADA".equalsIgnoreCase(visita.getEstado()))
                .map(visita -> visita.getFechaAsignada() == null
                        ? visita.getFechaHora()
                        : visita.getFechaAsignada())
                .filter(fecha -> fecha != null && fecha.isAfter(ahora))
                .collect(java.util.stream.Collectors.toSet());
        List<DisponibilidadVisita> disponibles = disponibilidadVisitaRepository
                .findByPropietarioIdAndReservadaFalseAndFechaHoraAfterOrderByFechaHoraAsc(
                        propietario.getId(), ahora)
                .stream()
                .filter(horario -> !horasOcupadas.contains(horario.getFechaHora()))
                .toList();
        return planificadorVisitasService.planificar(visitas, disponibles);
    }

    @PostMapping("/visitas/planificar/confirmar")
    public PlanVisitas confirmarPlan(@RequestBody PlanVisitas.Confirmacion confirmacion) {
        if (confirmacion == null || confirmacion.asignaciones() == null
                || confirmacion.asignaciones().isEmpty()
                || confirmacion.asignaciones().size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan no contiene asignaciones válidas.");
        }

        Usuario propietario = usuarioActual();
        PlanVisitas planActual = planificarVisitas();
        Set<String> asignacionesSolicitadas = new HashSet<>();
        for (PlanVisitas.AsignacionConfirmada asignacion : confirmacion.asignaciones()) {
            if (asignacion == null || asignacion.visitaId() == null
                    || asignacion.disponibilidadId() == null
                    || !asignacionesSolicitadas.add(asignacion.visitaId() + ":" + asignacion.disponibilidadId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan contiene asignaciones duplicadas o inválidas.");
            }
        }
        Set<String> asignacionesOptimas = planActual.asignaciones().stream()
                .map(asignacion -> asignacion.visitaId() + ":" + asignacion.disponibilidadId())
                .collect(java.util.stream.Collectors.toSet());
        if (!asignacionesSolicitadas.equals(asignacionesOptimas)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Las solicitudes o los horarios cambiaron. Calcula el plan nuevamente antes de confirmarlo.");
        }

        List<Map.Entry<Visita, DisponibilidadVisita>> registrosValidados = new ArrayList<>();
        for (PlanVisitas.Asignacion asignacion : planActual.asignaciones()) {
            Visita visita = visitaRepository.findById(asignacion.visitaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Una solicitud ya no existe."));
            DisponibilidadVisita horario = disponibilidadVisitaRepository.findById(asignacion.disponibilidadId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Un horario ya no existe."));
            if (!propietario.getId().equals(visita.getPropietarioId())
                    || !"SOLICITADA".equalsIgnoreCase(visita.getEstado())
                    || !propietario.getId().equals(horario.getPropietarioId())
                    || horario.isReservada()
                    || !horario.getFechaHora().equals(asignacion.fechaAsignada())
                    || horario.getFechaHora().isBefore(visita.getFechaHora())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Una solicitud o un horario cambió. Calcula el plan nuevamente.");
            }
            registrosValidados.add(Map.entry(visita, horario));
        }

        for (Map.Entry<Visita, DisponibilidadVisita> registro : registrosValidados) {
            Visita visita = registro.getKey();
            DisponibilidadVisita horario = registro.getValue();
            horario.setReservada(true);
            disponibilidadVisitaRepository.save(horario);
            visita.setFechaAsignada(horario.getFechaHora());
            visita.setDisponibilidadId(horario.getId());
            visita.setEstado("CONFIRMADA");
            visitaRepository.save(visita);
        }
        return planActual;
    }

    @PatchMapping("/visitas/{id}/estado")
    public ResponseEntity<Visita> actualizarEstadoVisita(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Usuario propietario = usuarioActual();
        Visita visita = visitaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!propietario.getId().equals(visita.getPropietarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar esta visita");
        }

        String nuevoEstado = body.get("estado");
        boolean transicionPermitida = "SOLICITADA".equals(visita.getEstado())
                && ("CONFIRMADA".equals(nuevoEstado) || "RECHAZADA".equals(nuevoEstado))
                || "CONFIRMADA".equals(visita.getEstado()) && "COMPLETADA".equals(nuevoEstado);
        if (!transicionPermitida) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transición de estado no permitida");
        }

        visita.setEstado(nuevoEstado);
        if ("CONFIRMADA".equals(nuevoEstado) && visita.getFechaAsignada() == null) {
            visita.setFechaAsignada(visita.getFechaHora());
        }
        return ResponseEntity.ok(visitaRepository.save(visita));
    }

    @PatchMapping("/propiedades/{id}/estado")
    public ResponseEntity<Propiedad> actualizarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        Usuario propietario = usuarioActual();
        Propiedad propiedad = propiedadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada"));

        verificarPropiedadPropia(propietario, propiedad);
        if (!propiedad.isVisible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La propiedad debe ser aprobada antes de cambiar su estado.");
        }

        String nuevoEstado = body.getOrDefault("estadoComercial", "DISPONIBLE");
        if (!List.of("DISPONIBLE", "EN_PROCESO").contains(nuevoEstado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado comercial no es válido.");
        }
        propiedad.setEstadoComercial(nuevoEstado);
        return ResponseEntity.ok(propiedadRepository.save(propiedad));
    }

    private void validarPropiedad(Propiedad propiedad) {
        if (propiedad == null || propiedad.getTitulo() == null || propiedad.getTitulo().isBlank()
                || propiedad.getTitulo().trim().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un título válido.");
        }
        if (propiedad.getDescripcion() == null || propiedad.getDescripcion().isBlank()
                || propiedad.getDescripcion().length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa una descripción válida.");
        }
        if (!List.of("venta", "arriendo").contains(propiedad.getOperacion())
                || !List.of("casa", "apartamento", "villa", "local", "terreno", "otro")
                        .contains(propiedad.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo u operación no es válido.");
        }
        if (!Double.isFinite(propiedad.getPrecio()) || propiedad.getPrecio() <= 0
                || !Double.isFinite(propiedad.getMetrosCuadrados()) || propiedad.getMetrosCuadrados() <= 0
                || propiedad.getHabitaciones() < 0 || propiedad.getWc() < 0
                || propiedad.getEstacionamiento() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa el precio y las características.");
        }
        if (propiedad.getCiudad() == null || propiedad.getCiudad().isBlank()
                || propiedad.getCiudad().trim().length() > 100
                || propiedad.getDireccion() == null || propiedad.getDireccion().isBlank()
                || propiedad.getDireccion().trim().length() > 200
                || (propiedad.getBarrio() != null && propiedad.getBarrio().trim().length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa la ciudad, dirección y barrio.");
        }
        if ((propiedad.getLatitud() == null) != (propiedad.getLongitud() == null)
                || (propiedad.getLatitud() != null
                && (!Double.isFinite(propiedad.getLatitud()) || propiedad.getLatitud() < -90
                || propiedad.getLatitud() > 90 || !Double.isFinite(propiedad.getLongitud())
                || propiedad.getLongitud() < -180 || propiedad.getLongitud() > 180))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las coordenadas no son válidas.");
        }
    }

    private List<MultipartFile> archivosValidos(MultipartFile[] imagenes, boolean requeridos) {
        List<MultipartFile> archivos = imagenes == null ? List.of()
                : java.util.Arrays.stream(imagenes)
                        .filter(imagen -> imagen != null && !imagen.isEmpty())
                        .toList();
        if (archivos.size() > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se permiten hasta cuatro imágenes.");
        }
        if (requeridos && archivos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta al menos una imagen.");
        }
        return archivos;
    }

    private List<String> guardarImagenes(List<MultipartFile> archivos, String propiedadId, String titulo)
            throws IOException {
        List<String> rutas = new ArrayList<>();
        try {
            for (int i = 0; i < archivos.size(); i++) {
                rutas.add("/api/imagenes/" + imageStorageService.store(
                        archivos.get(i), propiedadId, titulo, i + 1));
            }
            return rutas;
        } catch (IOException | RuntimeException error) {
            eliminarImagenes(rutas);
            throw error;
        }
    }

    private void eliminarImagenes(List<String> rutas) {
        if (rutas != null) rutas.forEach(imageStorageService::deleteByReference);
    }

    private Propiedad copiarDatosPublicables(Propiedad origen) {
        Propiedad destino = new Propiedad();
        copiarDatosPublicables(origen, destino);
        return destino;
    }

    private void copiarDatosPublicables(Propiedad origen, Propiedad destino) {
        destino.setTitulo(origen.getTitulo().trim());
        destino.setDescripcion(origen.getDescripcion().trim());
        destino.setOperacion(origen.getOperacion());
        destino.setTipo(origen.getTipo());
        destino.setPrecio(origen.getPrecio());
        destino.setHabitaciones(origen.getHabitaciones());
        destino.setWc(origen.getWc());
        destino.setEstacionamiento(origen.getEstacionamiento());
        destino.setMetrosCuadrados(origen.getMetrosCuadrados());
        destino.setDireccion(origen.getDireccion().trim());
        destino.setCiudad(origen.getCiudad().trim());
        destino.setBarrio(origen.getBarrio() == null ? "" : origen.getBarrio().trim());
        destino.setLatitud(origen.getLatitud());
        destino.setLongitud(origen.getLongitud());
    }

    private void verificarPropiedadPropia(Usuario propietario, Propiedad propiedad) {
        if (!propietario.getId().equals(propiedad.getPropietarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar una propiedad ajena.");
        }
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    public record SolicitudDisponibilidad(LocalDateTime fechaHora) { }
}
