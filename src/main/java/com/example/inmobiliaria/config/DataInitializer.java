package com.example.inmobiliaria.config;

import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Inicializa datos de demostración fuera del perfil de producción.
 */
@Configuration
@Profile("!prod")
public class DataInitializer {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea un usuario administrador por defecto si no existe
     */
    @Bean
    public CommandLineRunner initData() {
        return args -> {
            
            // Verificar si ya existe el usuario admin
            if (!usuarioRepository.existsByUsername("admin")) {
                
                System.out.println("===========================================");
                System.out.println("CREANDO USUARIO ADMINISTRADOR POR DEFECTO");
                System.out.println("===========================================");
                
                // Crear usuario admin
                Usuario admin = new Usuario();
                admin.setNombre("Administrador");
                admin.setUsername("admin");
                admin.setEmail("admin@inmobiliaria.com");
                admin.setPassword(passwordEncoder.encode("admin123")); // Contraseña hasheada
                admin.setRol("ADMIN");
                admin.setEnabled(true);
                
                usuarioRepository.save(admin);
                
                System.out.println("✓ Usuario administrador creado exitosamente");
                System.out.println("  Username: admin");
                System.out.println("  Password: admin123");
                System.out.println("  Email: admin@inmobiliaria.com");
                System.out.println("===========================================");
                System.out.println("");
                System.out.println("⚠️  IMPORTANTE: Cambia esta contraseña en producción");
                System.out.println("");
            } else {
                System.out.println("Usuario administrador ya existe en la base de datos");
            }

            if (!usuarioRepository.existsByUsername("propietario")) {
                Usuario propietario = new Usuario();
                propietario.setNombre("Propietario Demo");
                propietario.setUsername("propietario");
                propietario.setEmail("propietario@inmobiliaria.com");
                propietario.setPassword(passwordEncoder.encode("propietario123"));
                propietario.setRol("PROPIETARIO");
                propietario.setEnabled(true);
                usuarioRepository.save(propietario);

                System.out.println("✓ Usuario propietario creado exitosamente");
                System.out.println("  Username: propietario");
                System.out.println("  Password: propietario123");
            }

            if (!usuarioRepository.existsByUsername("cliente")) {
                Usuario cliente = new Usuario();
                cliente.setNombre("Cliente Demo");
                cliente.setUsername("cliente");
                cliente.setEmail("cliente@inmobiliaria.com");
                cliente.setPassword(passwordEncoder.encode("cliente123"));
                cliente.setRol("CLIENTE");
                cliente.setEnabled(true);
                usuarioRepository.save(cliente);

                System.out.println("✓ Usuario cliente creado exitosamente");
                System.out.println("  Username: cliente");
                System.out.println("  Password: cliente123");
            }
        };
    }
}