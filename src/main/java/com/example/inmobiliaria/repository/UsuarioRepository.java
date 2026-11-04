package com.example.inmobiliaria.repository;

import com.example.inmobiliaria.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByCelularNormalizado(String celularNormalizado);

    boolean existsByCelularRegex(String celularRegex);

    boolean existsByCelularNormalizadoAndUsernameNot(String celularNormalizado, String username);

    boolean existsByCelularRegexAndUsernameNot(String celularRegex, String username);

    java.util.List<Usuario> findByRolAndEnabledTrue(String rol);
}

