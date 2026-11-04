package com.example.inmobiliaria.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CuentaService cuentaService;

    @Test
    void creaCuentaConCorreoNormalizadoYClaveCifrada() {
        when(usuarioRepository.existsByUsername("persona@ejemplo.com")).thenReturn(false);
        when(usuarioRepository.existsByEmail("persona@ejemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash-seguro");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario creado = cuentaService.crearCuenta(
                "  Ana Pérez ", " PERSONA@EJEMPLO.COM ", "+57 300 123 4567",
                "clave-segura", "cliente");

        assertEquals("Ana Pérez", creado.getNombre());
        assertEquals("persona@ejemplo.com", creado.getUsername());
        assertEquals("persona@ejemplo.com", creado.getEmail());
        assertEquals("+57 300 123 4567", creado.getCelular());
        assertEquals("hash-seguro", creado.getPassword());
        assertEquals("CLIENTE", creado.getRol());
        assertEquals(List.of(), creado.getFavoritos());
    }

    @Test
    void rechazaUnCorreoYaRegistrado() {
        when(usuarioRepository.existsByUsername("persona@ejemplo.com")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.crearCuenta(
                        "Ana Pérez", "persona@ejemplo.com", "+57 300 123 4567",
                        "clave-segura", "CLIENTE"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Este correo ya está en uso. Prueba con otro.", error.getReason());
    }

    @Test
    void rechazaUnCelularYaRegistradoAunqueTengaFormatoDiferente() {
        when(usuarioRepository.existsByCelularRegex("^\\D*5\\D*7\\D*3\\D*0\\D*0\\D*1\\D*2\\D*3\\D*4\\D*5\\D*6\\D*7\\D*$"))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.crearCuenta(
                        "Ana Pérez", "nueva@ejemplo.com", "+57 300 123 4567",
                        "clave-segura", "CLIENTE"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Este número de celular ya está en uso. Prueba con otro.", error.getReason());
    }

    @Test
    void permiteRepetirNombreYContrasenaConCorreoYCelularDistintos() {
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash-seguro");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario primero = cuentaService.crearCuenta(
                "Ana Pérez", "primera@ejemplo.com", "+57 300 123 4567", "clave-segura", "CLIENTE");
        Usuario segundo = cuentaService.crearCuenta(
                "Ana Pérez", "segunda@ejemplo.com", "+57 300 765 4321", "clave-segura", "CLIENTE");

        assertEquals(primero.getNombre(), segundo.getNombre());
        assertEquals(primero.getPassword(), segundo.getPassword());
        assertEquals("573001234567", primero.getCelularNormalizado());
        assertEquals("573007654321", segundo.getCelularNormalizado());
        verify(usuarioRepository, times(2)).save(any(Usuario.class));
    }

    @Test
    void rechazaNombreSinUnNombreValido() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.crearCuenta(
                        "g", "persona@ejemplo.com", "+57 300 123 4567",
                        "clave-segura", "CLIENTE"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rechazaCelularConMuyPocosDigitos() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.crearCuenta(
                        "Ana Pérez", "persona@ejemplo.com", "4",
                        "clave-segura", "CLIENTE"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void actualizaFavoritosSinDuplicados() {
        Usuario usuario = new Usuario();
        usuario.setFavoritos(List.of("antiguo"));
        when(usuarioRepository.findByUsername("persona@ejemplo.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        List<String> favoritos = cuentaService.actualizarFavoritos(
                "persona@ejemplo.com", List.of("propiedad-1", "propiedad-1", "propiedad-2"));

        assertEquals(List.of("propiedad-1", "propiedad-2"), favoritos);
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizaNombreYCelularDelPerfil() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Nombre anterior");
        when(usuarioRepository.findByUsername("persona@ejemplo.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario actualizado = cuentaService.actualizarPerfil(
                "persona@ejemplo.com", "  Ana Pérez ", " +57 300 123 4567 ");

        assertEquals("Ana Pérez", actualizado.getNombre());
        assertEquals("+57 300 123 4567", actualizado.getCelular());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void rechazaCambiarPerfilAUnCelularDeOtraCuenta() {
        Usuario usuario = new Usuario();
        usuario.setUsername("persona@ejemplo.com");
        when(usuarioRepository.findByUsername("persona@ejemplo.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByCelularNormalizadoAndUsernameNot(
                "573001234567", "persona@ejemplo.com")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.actualizarPerfil(
                        "persona@ejemplo.com", "Ana Pérez", "+57 300 123 4567"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Este número de celular ya está en uso. Prueba con otro.", error.getReason());
    }

    @Test
    void requiereLaContraseñaActualParaCambiarla() {
        Usuario usuario = new Usuario();
        usuario.setPassword("hash-anterior");
        when(usuarioRepository.findByUsername("persona@ejemplo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-anterior")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cuentaService.cambiarPassword("persona@ejemplo.com", "incorrecta", "nueva-segura"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void cifraLaNuevaContraseñaTrasValidarLaActual() {
        Usuario usuario = new Usuario();
        usuario.setPassword("hash-anterior");
        when(usuarioRepository.findByUsername("persona@ejemplo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("actual-segura", "hash-anterior")).thenReturn(true);
        when(passwordEncoder.encode("nueva-segura")).thenReturn("hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        cuentaService.cambiarPassword("persona@ejemplo.com", "actual-segura", "nueva-segura");

        assertEquals("hash-nuevo", usuario.getPassword());
        verify(usuarioRepository).save(usuario);
    }
}
