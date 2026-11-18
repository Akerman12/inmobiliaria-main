package com.example.inmobiliaria.chat;

import java.util.Locale;
import java.util.Map;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import com.example.inmobiliaria.chat.ChatDatos.ChatRequest;
import com.example.inmobiliaria.chat.ChatDatos.ChatResponse;

@RestController
@RequestMapping("/api/v2/chat")
@ConditionalOnExpression("'${SPRING_AI_MODEL_CHAT:none}' == 'openai' and T(org.springframework.util.StringUtils).hasText('${GROQ_API_KEY:}')")
public class SpringAiChatController {

    private final SpringAiChatService chatService;

    public SpringAiChatController(SpringAiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || !textoValido(request.pregunta(), 1, 2000)
                || !textoOpcionalValido(request.dominio(), 80)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La pregunta o el tema no son válidos."));
        }
        try {
            String respuesta = chatService.responder(request.pregunta().trim(), limpiarOpcional(request.dominio()));
            return ResponseEntity.ok(new ChatResponse(respuesta, chatService.getModelo(), limpiarOpcional(request.dominio())));
        } catch (NonTransientAiException exception) {
            return respuestaErrorGroq(exception);
        }
    }

    @PostMapping("/rapido")
    public ResponseEntity<?> chatRapido(@RequestParam String pregunta,
                                        @RequestParam(required = false) String dominio) {
        if (!textoValido(pregunta, 1, 2000) || !textoOpcionalValido(dominio, 80)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La pregunta o el tema no son válidos."));
        }
        try {
            String respuesta = chatService.responder(pregunta.trim(), limpiarOpcional(dominio));
            return ResponseEntity.ok(new ChatResponse(respuesta, chatService.getModelo(), limpiarOpcional(dominio)));
        } catch (NonTransientAiException exception) {
            return respuestaErrorGroq(exception);
        }
    }

    private ResponseEntity<Map<String, String>> respuestaErrorGroq(NonTransientAiException exception) {
        String detalle = exception.getMessage();
        String detalleNormalizado = detalle == null ? "" : detalle.toLowerCase(Locale.ROOT);
        boolean claveInvalida = detalleNormalizado.contains("invalid api key")
            || detalleNormalizado.contains("invalid_api_key")
            || detalleNormalizado.contains("http 401");
        boolean modeloNoDisponible = detalleNormalizado.contains("model_not_found")
            || (detalleNormalizado.contains("model")
                && (detalleNormalizado.contains("does not exist")
                || detalleNormalizado.contains("do not have access")));
        boolean limiteExcedido = detalleNormalizado.contains("http 429")
            || detalleNormalizado.contains("rate limit");
        String mensaje = claveInvalida
                ? "Groq rechazó la API key. Actualiza GROQ_API_KEY y reinicia la aplicación."
            : modeloNoDisponible
                ? "Groq no reconoce el modelo configurado o tu cuenta no tiene acceso. Revisa spring.ai.openai.chat.options.model."
                : limiteExcedido
                ? "Groq indicó que se superó el límite de solicitudes. Espera y vuelve a intentarlo."
                : "Groq no pudo procesar la solicitud. Revisa los logs de la aplicación.";

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", mensaje));
    }

    private boolean textoValido(String valor, int minimo, int maximo) {
        return valor != null && valor.trim().length() >= minimo && valor.trim().length() <= maximo;
    }

    private boolean textoOpcionalValido(String valor, int maximo) {
        return valor == null || valor.trim().length() <= maximo;
    }

    private String limpiarOpcional(String valor) {
        return valor == null ? null : valor.trim();
    }

    @GetMapping("/salud")
    public String salud() {
        return "Servicio de chat IA (Spring AI + Groq) operativo. Modelo: " + chatService.getModelo();
    }
}