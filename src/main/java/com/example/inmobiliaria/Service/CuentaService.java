package com.example.inmobiliaria.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.UsuarioRepository;

@Service
public class CuentaService {

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[\\p{L}][\\p{L} '\\u2019.-]*$");
    private static final Pattern CELULAR_VALIDO = Pattern.compile("^\\+?[0-9][0-9\\s().-]*$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CuentaService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario crearCuenta(String nombre, String email, String celular, String password, String tipoCuenta) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        String emailLimpio = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String celularLimpio = celular == null ? "" : celular.trim();
        String rol = tipoCuenta == null ? "" : tipoCuenta.trim().toUpperCase(Locale.ROOT);

        if (!esNombreValido(nombreLimpio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un nombre válido.");
        }
        if (!emailLimpio.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || emailLimpio.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un correo electrónico válido.");
        }
        if (!esCelularValido(celularLimpio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un número de celular válido.");
        }
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos 8 caracteres y no superar el límite permitido.");
        }
        if (!rol.equals("CLIENTE") && !rol.equals("PROPIETARIO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un tipo de cuenta válido.");
        }
        if (usuarioRepository.existsByUsername(emailLimpio) || usuarioRepository.existsByEmail(emailLimpio)) {
            throw correoEnUso();
        }
        String celularNormalizado = normalizarCelular(celularLimpio);
        if (usuarioRepository.existsByCelularNormalizado(celularNormalizado)
                || usuarioRepository.existsByCelularRegex(patronCelular(celularNormalizado))) {
            throw celularEnUso();
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombreLimpio);
        usuario.setUsername(emailLimpio);
        usuario.setEmail(emailLimpio);
        usuario.setCelular(celularLimpio);
        usuario.setCelularNormalizado(celularNormalizado);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setEnabled(true);
        usuario.setFavoritos(List.of());

        try {
            return usuarioRepository.save(usuario);
        } catch (DuplicateKeyException exception) {
            if (esDuplicadoCelular(exception)) {
                throw celularEnUso(exception);
            }
            throw correoEnUso(exception);
        }
    }

    public Usuario buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida."));
    }

    public Usuario actualizarPerfil(String username, String nombre, String celular) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        String celularLimpio = celular == null ? "" : celular.trim();
        if (!esNombreValido(nombreLimpio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un nombre válido.");
        }
        if (!esCelularValido(celularLimpio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un número de celular válido.");
        }

        Usuario usuario = buscarPorUsername(username);
        String celularNormalizado = normalizarCelular(celularLimpio);
        if (usuarioRepository.existsByCelularNormalizadoAndUsernameNot(celularNormalizado, username)
                || usuarioRepository.existsByCelularRegexAndUsernameNot(
                        patronCelular(celularNormalizado), username)) {
            throw celularEnUso();
        }
        usuario.setNombre(nombreLimpio);
        usuario.setCelular(celularLimpio);
        usuario.setCelularNormalizado(celularNormalizado);
        try {
            return usuarioRepository.save(usuario);
        } catch (DuplicateKeyException exception) {
            throw celularEnUso(exception);
        }
    }

    public void cambiarPassword(String username, String passwordActual, String passwordNueva) {
        Usuario usuario = buscarPorUsername(username);
        if (passwordActual == null || !passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        }
        if (passwordNueva == null || passwordNueva.length() < 8
                || passwordNueva.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe tener al menos 8 caracteres y no superar el límite permitido.");
        }

        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);
    }

    public List<String> obtenerFavoritos(String username) {
        return new ArrayList<>(buscarPorUsername(username).getFavoritos());
    }

    public List<String> actualizarFavoritos(String username, List<String> ids) {
        if (ids == null || ids.size() > 100
                || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista de favoritos no es válida.");
        }

        List<String> favoritos = ids.stream().distinct().toList();
        Usuario usuario = buscarPorUsername(username);
        usuario.setFavoritos(favoritos);
        return usuarioRepository.save(usuario).getFavoritos();
    }

    private boolean esNombreValido(String nombre) {
        return nombre.length() >= 2 && nombre.length() <= 100
                && NOMBRE_VALIDO.matcher(nombre).matches()
                && nombre.codePoints().filter(Character::isLetter).count() >= 2;
    }

    private boolean esCelularValido(String celular) {
        long digitos = celular.chars().filter(Character::isDigit).count();
        return celular.length() <= 30 && CELULAR_VALIDO.matcher(celular).matches()
                && digitos >= 7 && digitos <= 15;
    }

    private String normalizarCelular(String celular) {
        return celular.replaceAll("\\D", "");
    }

    private String patronCelular(String celularNormalizado) {
        return "^\\D*" + String.join("\\D*", celularNormalizado.split("")) + "\\D*$";
    }

    private boolean esDuplicadoCelular(DuplicateKeyException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains("celular_normalizado_unique_idx");
    }

    private ResponseStatusException correoEnUso() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Este correo ya está en uso. Prueba con otro.");
    }

    private ResponseStatusException correoEnUso(DuplicateKeyException cause) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Este correo ya está en uso. Prueba con otro.", cause);
    }

    private ResponseStatusException celularEnUso() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Este número de celular ya está en uso. Prueba con otro.");
    }

    private ResponseStatusException celularEnUso(DuplicateKeyException cause) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Este número de celular ya está en uso. Prueba con otro.", cause);
    }
}
