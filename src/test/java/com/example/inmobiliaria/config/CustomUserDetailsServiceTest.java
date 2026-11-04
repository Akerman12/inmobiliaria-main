package com.example.inmobiliaria.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void permiteAutenticarseConElCorreoDeUnaCuentaExistente() {
        Usuario usuario = new Usuario("Cliente", "cliente", "cliente@ejemplo.com", "hash", "CLIENTE");
        when(usuarioRepository.findByUsername("CLIENTE@EJEMPLO.COM")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("cliente@ejemplo.com")).thenReturn(Optional.of(usuario));

        UserDetails detalles = userDetailsService.loadUserByUsername("CLIENTE@EJEMPLO.COM");

        assertEquals("cliente", detalles.getUsername());
        assertEquals("ROLE_CLIENTE", detalles.getAuthorities().iterator().next().getAuthority());
        verify(usuarioRepository).findByEmail("cliente@ejemplo.com");
    }
}
