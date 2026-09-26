package com.example.inmobiliaria.chat;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.inmobiliaria.chat.ChatDatos.ChatRequest;
import com.example.inmobiliaria.chat.ChatDatos.ChatResponse;

@RestController
@RequestMapping("/api/v2/chat")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(prefix = "groq", name = "api-key")
public class SpringAiChatController {

    private final SpringAiChatService chatService;

    public SpringAiChatController(SpringAiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String respuesta = chatService.responder(request.pregunta(), request.dominio());
        return new ChatResponse(respuesta, chatService.getModelo(), request.dominio());
    }

    @PostMapping("/rapido")
    public ChatResponse chatRapido(@RequestParam String pregunta,
                                   @RequestParam(required = false) String dominio) {
        String respuesta = chatService.responder(pregunta, dominio);
        return new ChatResponse(respuesta, chatService.getModelo(), dominio);
    }

    @GetMapping("/salud")
    public String salud() {
        return "Servicio de chat IA (Spring AI + Groq) operativo. Modelo: " + chatService.getModelo();
    }
}