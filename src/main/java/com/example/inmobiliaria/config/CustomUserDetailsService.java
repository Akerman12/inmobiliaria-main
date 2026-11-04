package com.example.inmobiliaria.config;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

        private final UsuarioRepository usuarioRepository;

        public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
                this.usuarioRepository = usuarioRepository;
        }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        String credencial = username.trim();

        Usuario usuario = usuarioRepository.findByUsername(credencial)
                .or(() -> usuarioRepository.findByEmail(credencial.toLowerCase(Locale.ROOT)))
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuario no encontrado: " + username));

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword()) // BCrypt
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + usuario.getRol())
                ))
                .disabled(!usuario.isEnabled())
                .build();
    }
}
