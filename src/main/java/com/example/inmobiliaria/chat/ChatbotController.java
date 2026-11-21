package com.example.inmobiliaria.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.chat.ChatContextService.ChatContexto;
import com.example.inmobiliaria.chat.ChatContextService.IdentidadChat;
import com.example.inmobiliaria.chat.ChatDatos.ChatbotEstadoResponse;
import com.example.inmobiliaria.chat.ChatDatos.ChatbotRequest;
import com.example.inmobiliaria.chat.ChatDatos.ChatbotResponse;
import com.example.inmobiliaria.chat.ChatDatos.TurnoHistorial;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);
    private static final int MAX_MENSAJE_LEN = 500;
    private static final int MAX_HISTORIAL_TURNOS = 6;
    private static final String SESSION_HISTORY_KEY = "CHATBOT_HISTORIAL";
    private static final String SESSION_OWNER_KEY = "CHATBOT_IDENTIDAD";

    private final ObjectProvider<SpringAiChatService> chatServiceProvider;
    private final ChatContextService chatContextService;
    private final ChatbotRateLimiter rateLimiter;
    private final String groqApiKey;

    public ChatbotController(ObjectProvider<SpringAiChatService> chatServiceProvider,
                             ChatContextService chatContextService,
                             ChatbotRateLimiter rateLimiter,
                             @Value("${GROQ_API_KEY:}") String groqApiKey) {
        this.chatServiceProvider = chatServiceProvider;
        this.chatContextService = chatContextService;
        this.rateLimiter = rateLimiter;
        this.groqApiKey = groqApiKey;
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estado(Authentication authentication) {
        try {
            IdentidadChat identidad = chatContextService.resolverIdentidad(authentication);
            return ResponseEntity.ok(new ChatbotEstadoResponse(estaDisponible(), identidad.rol().getEtiqueta()));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Sesión no válida."));
        }
    }

    @PostMapping
    public ResponseEntity<?> conversar(@RequestBody(required = false) ChatbotRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpRequest) {
        IdentidadChat identidad;
        try {
            identidad = chatContextService.resolverIdentidad(authentication);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Sesión no válida."));
        }

        boolean autenticado = identidad.rol() != ChatRole.VISITANTE;
        String rateKey = autenticado
                ? "USER:" + identidad.usuarioId()
                : "IP:" + extraerIpCliente(httpRequest);
        if (!rateLimiter.tryConsume(rateKey, autenticado)) {
            log.warn("Rate limit alcanzado en chatbot para rol={}", identidad.rol());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Has enviado demasiadas solicitudes. Espera un momento antes de intentar de nuevo."));
        }

        String mensaje = sanitizarMensaje(request == null ? null : request.mensaje());
        if (mensaje == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El mensaje es obligatorio y debe tener entre 1 y 500 caracteres."));
        }

        SpringAiChatService chatService = chatServiceProvider.getIfAvailable();
        if (!estaDisponible() || chatService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El asistente virtual está desactivado en este momento."));
        }

        try {
            ChatContexto contexto = chatContextService.construirContexto(authentication);
            String clavePropietarioHistorial = identidad.rol().name() + ":"
                    + (identidad.usuarioId() == null ? "anon" : identidad.usuarioId());
            List<TurnoHistorial> historial = obtenerHistorial(httpRequest, clavePropietarioHistorial);

            String respuesta = chatService.responderChatbot(contexto, historial, mensaje);
            if (respuesta == null || respuesta.isBlank()) {
                respuesta = "No pude generar una respuesta en este momento. Intenta de nuevo o visita /contacto.html.";
            }

            guardarTurnoHistorial(httpRequest, clavePropietarioHistorial, historial, mensaje, respuesta);
            log.debug("Consulta chatbot atendida para rol={}", identidad.rol());
            return ResponseEntity.ok(new ChatbotResponse(respuesta.trim(), identidad.rol().getEtiqueta(), true));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "No autorizado."));
        } catch (NonTransientAiException | TransientAiException ex) {
            return manejarErrorProveedor(ex);
        } catch (RuntimeException ex) {
            log.error("Error inesperado al procesar solicitud de chatbot para rol={}", identidad.rol());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El asistente virtual no está disponible temporalmente."));
        }
    }

    private boolean estaDisponible() {
        return StringUtils.hasText(groqApiKey) && chatServiceProvider.getIfAvailable() != null;
    }

    private String sanitizarMensaje(String crudo) {
        if (crudo == null) {
            return null;
        }
        String limpio = crudo.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (limpio.isEmpty() || limpio.length() > MAX_MENSAJE_LEN) {
            return null;
        }
        return limpio;
    }

    private String extraerIpCliente(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "unknown";
        }
        return request.getRemoteAddr();
    }

    @SuppressWarnings("unchecked")
    private List<TurnoHistorial> obtenerHistorial(HttpServletRequest request, String claveIdentidad) {
        if (request == null) {
            return List.of();
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return List.of();
        }
        Object owner = session.getAttribute(SESSION_OWNER_KEY);
        if (!claveIdentidad.equals(owner)) {
            session.removeAttribute(SESSION_HISTORY_KEY);
            session.setAttribute(SESSION_OWNER_KEY, claveIdentidad);
            return List.of();
        }
        Object raw = session.getAttribute(SESSION_HISTORY_KEY);
        if (raw instanceof List<?> lista) {
            List<TurnoHistorial> resultado = new ArrayList<>();
            for (Object item : lista) {
                if (item instanceof TurnoHistorial turno) {
                    resultado.add(turno);
                }
            }
            return resultado;
        }
        return List.of();
    }

    private void guardarTurnoHistorial(HttpServletRequest request,
                                       String claveIdentidad,
                                       List<TurnoHistorial> historialPrevio,
                                       String mensajeUsuario,
                                       String respuestaBot) {
        if (request == null) {
            return;
        }
        HttpSession session = request.getSession(true);
        List<TurnoHistorial> actualizado = new ArrayList<>(historialPrevio);
        actualizado.add(new TurnoHistorial("user", mensajeUsuario));
        actualizado.add(new TurnoHistorial("assistant", respuestaBot.trim()));
        int desde = Math.max(0, actualizado.size() - MAX_HISTORIAL_TURNOS);
        session.setAttribute(SESSION_OWNER_KEY, claveIdentidad);
        session.setAttribute(SESSION_HISTORY_KEY, new ArrayList<>(actualizado.subList(desde, actualizado.size())));
    }

    private ResponseEntity<Map<String, String>> manejarErrorProveedor(Exception exception) {
        String detalle = exception.getMessage();
        String normalizado = detalle == null ? "" : detalle.toLowerCase(Locale.ROOT);
        if (normalizado.contains("http 429") || normalizado.contains("rate limit")) {
            log.warn("Proveedor IA devolvió límite de tasa (429)");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "El asistente recibió demasiadas solicitudes. Espera un momento e intenta de nuevo."));
        }
        log.warn("Error de disponibilidad con el proveedor IA del chatbot");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "El asistente virtual no está disponible en este momento."));
    }
}

