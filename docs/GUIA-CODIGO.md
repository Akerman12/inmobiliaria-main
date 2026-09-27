# Guia del codigo de Inmobiliaria

Esta guia explica las partes importantes del proyecto y como se conectan. No pretende describir cada linea repetida de getters, setters o HTML, sino las decisiones y los flujos que controlan el comportamiento.

## 1. Que tipo de aplicacion es

Es una aplicacion web con:

- Java 21.
- Spring Boot 3.4.5.
- Spring Web para crear endpoints HTTP.
- Spring Data MongoDB para guardar propiedades, usuarios y contactos.
- Spring Security para proteger el panel administrativo.
- Weka 3.8.6 para estimar precios mediante regresion lineal.
- Spring AI conectado al endpoint compatible con OpenAI de Groq para el chat.
- HTML, CSS y JavaScript estatico en `src/main/resources/static`.

La aplicacion se ejecuta en el puerto `8080` y usa MongoDB en `localhost:27017`.

## 2. Como inicia la aplicacion

Archivo: `src/main/java/com/example/inmobiliaria/InmobiliariaApplication.java`

```java
@SpringBootApplication
@EnableMongoAuditing
public class InmobiliariaApplication {
    public static void main(String[] args) {
        SpringApplication.run(InmobiliariaApplication.class, args);
    }
}
```

### Lineas importantes

- `@SpringBootApplication` combina configuracion, deteccion de componentes y auto-configuracion de Spring Boot.
- `@EnableMongoAuditing` activa campos como `@CreatedDate`.
- `main` es el punto de entrada de Java.
- `SpringApplication.run(...)` crea el contenedor de Spring, registra los componentes y levanta el servidor Tomcat.

Spring detecta automaticamente clases marcadas con `@RestController`, `@Service`, `@Configuration` y otras anotaciones.

## 3. Organizacion por capas

El proyecto sigue una organizacion sencilla:

```text
Modelo       -> representa los datos
Repository   -> accede a MongoDB
Service      -> contiene logica reutilizable
Controller   -> recibe peticiones HTTP y devuelve respuestas
Frontend     -> HTML/CSS/JavaScript que consume la API
Config       -> seguridad e inicializacion
```

Ejemplo del flujo para listar propiedades:

```text
anuncios.js
   -> GET /api/propiedades/visibles
   -> PropiedadRestController
   -> PropiedadRepository
   -> MongoDB
   -> JSON
   -> anuncios.js dibuja las tarjetas
```

## 4. Modelos y MongoDB

Archivos principales:

- `model/Propiedad.java`
- `model/Usuario.java`
- `model/Contacto.java`

Ejemplo:

```java
@Document(collection = "propiedades")
public class Propiedad {
    @Id
    private String id;
    private String titulo;
    private double precio;
    private String ciudad;
}
```

### Que significa cada parte

- `@Document(collection = "propiedades")` indica que la clase se guarda en la coleccion `propiedades`.
- `@Id` identifica el documento.
- Los atributos son los datos que se guardan.
- Los getters y setters permiten leer y cambiar esos atributos.
- `getOperacion()` y `getTipo()` aplican valores predeterminados para propiedades antiguas:

```java
public String getOperacion() {
    return operacion == null ? "venta" : operacion;
}
```

Esto es un operador ternario: si `operacion` es `null`, devuelve `venta`; en caso contrario, devuelve el valor existente.

En `Usuario.java`, `@Indexed(unique = true)` evita duplicados de username y email en MongoDB.

## 5. Herencia, interfaces y composicion

### Herencia de clases

No hay una jerarquia propia de clases como `Animal -> Perro`. Los modelos `Propiedad`, `Usuario` y `Contacto` son clases independientes.

Sin embargo, Java hace que toda clase herede implicitamente de `Object`.

### Implementacion de interfaces

Si existe una relacion de tipo "cumple este contrato", se usa `implements`:

```java
public class CustomUserDetailsService implements UserDetailsService
```

`CustomUserDetailsService` debe implementar `loadUserByUsername`, porque lo exige la interfaz de Spring Security.

Los repositorios tambien extienden interfaces de Spring Data:

```java
public interface PropiedadRepository
        extends MongoRepository<Propiedad, String> {
}
```

Esto es herencia de interfaces. Al extender `MongoRepository`, el repositorio recibe metodos como:

- `findAll()`
- `findById(...)`
- `save(...)`
- `deleteById(...)`

Spring genera la implementacion automaticamente.

### Composicion o dependencia

La mayoria de relaciones del proyecto son composicion/dependencia, no herencia:

```java
private final PropiedadRepository repo;
private final ImageStorageService imageStorageService;
```

`PropiedadRestController` utiliza esos objetos para cumplir su trabajo. Los recibe por constructor, una practica llamada inyeccion de dependencias.

## 6. Repositories

Archivos:

- `repository/PropiedadRepository.java`
- `repository/UsuarioRepository.java`
- `repository/ContactoRepository.java`

Ejemplo de consulta derivada:

```java
Optional<Usuario> findByUsername(String username);
boolean existsByUsername(String username);
```

Spring Data interpreta el nombre del metodo y crea la consulta MongoDB. No es necesario escribir manualmente el SQL; MongoDB no usa SQL en este proyecto.

`Optional<Usuario>` expresa que el usuario puede existir o no:

```java
usuarioRepository.findByUsername(username)
    .orElseThrow(() -> new UsernameNotFoundException(...));
```

Si no se encuentra, se lanza una excepcion controlada.

## 7. Controllers y endpoints

Un controller convierte peticiones HTTP en llamadas Java.

Archivo: `controller/PropiedadRestController.java`

```java
@RestController
@RequestMapping("/api/propiedades")
public class PropiedadRestController {
```

- `@RestController` indica que los resultados se devuelven normalmente como JSON.
- `@RequestMapping` define el prefijo comun.
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping` y `@DeleteMapping` definen operaciones.

Endpoints principales:

```text
GET    /api/propiedades
GET    /api/propiedades/{id}
GET    /api/propiedades/visibles
POST   /api/propiedades
PUT    /api/propiedades/{id}
PATCH  /api/propiedades/{id}/visibilidad
DELETE /api/propiedades/{id}
```

En una peticion multipart, el controller recibe los datos y las imagenes:

```java
@RequestPart("propiedad") Propiedad propiedad,
@RequestPart(value = "imagenes", required = false)
MultipartFile[] imagenes
```

`@RequestPart` separa las partes del formulario enviado desde JavaScript.

## 8. Servicios e inyeccion por constructor

Archivos principales:

- `Service/PropiedadService.java`
- `Service/UsuarioService.java`
- `Service/ImageStorageService.java`

Ejemplo:

```java
@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
```

- `@Service` registra la clase como componente de Spring.
- Los campos `final` no cambian despues de construir el objeto.
- El constructor recibe las dependencias necesarias.
- Spring crea el servicio y le entrega automaticamente el repositorio y el codificador.

Esta es la razon por la que el proyecto ya no depende de `@Autowired` en campos.

## 9. Spring Security

Archivos:

- `config/SecurityConfig.java`
- `config/CustomUserDetailsService.java`
- `config/DataInitializer.java`
- `static/admin/login.html`
- `static/js/login.js`

### Carga del usuario

`CustomUserDetailsService` busca el usuario en MongoDB:

```java
Usuario usuario = usuarioRepository.findByUsername(username)
    .orElseThrow(() ->
        new UsernameNotFoundException("Usuario no encontrado: " + username));
```

Luego transforma el modelo `Usuario` al objeto que Spring Security entiende:

```java
return User.builder()
    .username(usuario.getUsername())
    .password(usuario.getPassword())
    .authorities(List.of(
        new SimpleGrantedAuthority("ROLE_" + usuario.getRol())
    ))
    .disabled(!usuario.isEnabled())
    .build();
```

### Roles

Si el usuario tiene `rol = "ADMIN"`, se construye la autoridad `ROLE_ADMIN`.

En `SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.POST, "/api/propiedades/**")
    .hasRole("ADMIN")
```

Solo un usuario con ese rol puede crear propiedades.

Las operaciones GET publicas se permiten:

```java
.requestMatchers(HttpMethod.GET,
    "/api/propiedades/**", "/api/imagenes/**")
    .permitAll()
```

El panel `/admin/**` requiere autenticacion.

### Login y logout

```java
.formLogin(form -> form
    .loginPage("/admin/login.html")
    .loginProcessingUrl("/login")
    .defaultSuccessUrl("/admin/index.html", true)
)
```

Spring recibe el formulario en `/login`, autentica las credenciales y redirige al panel.

```java
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutSuccessUrl("/admin/login.html?logout=true")
)
```

`BCryptPasswordEncoder` guarda contrasenas con hash, no en texto plano.

### Usuario administrador inicial

`DataInitializer` se ejecuta al iniciar la aplicacion. Si no existe `admin`, crea uno y codifica su contrasena:

```java
admin.setPassword(passwordEncoder.encode("admin123"));
```

En produccion se debe cambiar esa contrasena inicial.

## 10. Modelo predictivo con Weka

Archivos:

- `controller/PrediccionController.java`
- `src/main/resources/modelo_precio.model`
- `src/main/resources/propiedades_colombia.arff`
- `pom.xml`, dependencia `weka-stable` 3.8.6

Weka no es un chat de IA. En este proyecto se usa Machine Learning tradicional: un modelo de regresion lineal que estima el precio.

### Carga del modelo

```java
@PostConstruct
public void cargarModelo() {
    InputStream isModelo =
        getClass().getResourceAsStream("/modelo_precio.model");
    modeloEntrenado = (LinearRegression)
        weka.core.SerializationHelper.read(isModelo);
}
```

`@PostConstruct` ejecuta el metodo despues de crear el controller y sus dependencias.

El modelo se carga desde `resources`, que queda disponible en el classpath al empaquetar la aplicacion.

Tambien se carga el ARFF:

```java
estructuraDataset = new DataSource(isArff).getDataSet();
estructuraDataset.setClassIndex(
    estructuraDataset.numAttributes() - 1);
```

El ultimo atributo es `precio`, la variable que el modelo intenta predecir.

### Flujo de prediccion

Endpoint:

```text
POST /api/prediccion/precio
```

El frontend envia habitaciones, banos, parqueadero, metros cuadrados y ciudad. El controller:

1. Convierte los valores JSON a numeros y texto.
2. Comprueba que la ciudad exista en el ARFF.
3. Crea una instancia con la misma estructura del entrenamiento.
4. Marca `precio` como desconocido.
5. Ejecuta `classifyInstance`.
6. Redondea y formatea el resultado.
7. Devuelve JSON.

La linea clave es:

```java
double precioPredecho = modeloEntrenado.classifyInstance(instancia);
```

El resultado se redondea a millones:

```java
long precioRedondeado =
    Math.round(precioPredecho / 1_000_000.0) * 1_000_000L;
```

La interfaz de `admin/crear.html` muestra el boton de estimacion. El JavaScript envia la peticion y permite usar el precio sugerido en el formulario.

## 11. Groq y Spring AI

Archivos:

- `chat/SpringAiChatController.java`
- `chat/SpringAiChatService.java`
- `chat/ChatDatos.java`
- `application.properties`

Configuracion en `src/main/resources/application.properties`:

```properties
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.api-key=${GROQ_API_KEY:chat-disabled}
spring.ai.openai.chat.options.model=openai/gpt-oss-20b
```

Spring AI usa un cliente compatible con OpenAI, pero la URL base se cambia para enviar las peticiones a Groq.

El chat solo se registra cuando la variable de entorno `GROQ_API_KEY` tiene contenido:

```java
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${GROQ_API_KEY:}')")
```

Esto permite que la web y el catalogo funcionen sin clave.

`spring.ai.openai.api-key` obtiene el valor de `GROQ_API_KEY`; si no existe, usa
un valor de reserva para permitir que Spring AI inicialice sin activar el chat.
`@ConditionalOnExpression` registra el controller y el servicio solo cuando
`GROQ_API_KEY` tiene contenido.

### Activar Groq en Windows

1. Crea una API key en <https://console.groq.com/> desde **API Keys → Create API Key**.
2. En una terminal de PowerShell configura la clave solo para la sesion actual:

```powershell
$env:GROQ_API_KEY = "gsk_TU_KEY_AQUI"
.\mvnw.cmd spring-boot:run
```

3. Para dejarla guardada para futuras terminales, usa las variables de entorno de
     Windows o configura la variable desde el perfil de ejecucion de VS Code. No
     escribas la clave dentro del repositorio ni la subas a Git.

Si no configuras `GROQ_API_KEY`, el resto de la aplicacion puede arrancar, pero
los endpoints del chat no deben considerarse disponibles.

### Probar Groq

```powershell
curl.exe http://localhost:8080/api/v2/chat/salud

curl.exe -X POST http://localhost:8080/api/v2/chat `
    -H "Content-Type: application/json" `
    -d '{"pregunta":"Explica Spring Boot en dos oraciones","dominio":"Java"}'
```

La respuesta incluye `respuesta`, `modelo` y `dominio`. Para listar los modelos
disponibles para la clave configurada, ejecuta en PowerShell:

```powershell
$headers = @{ Authorization = "Bearer $env:GROQ_API_KEY" }
(Invoke-RestMethod -Uri "https://api.groq.com/openai/v1/models" -Headers $headers).data |
    Select-Object -ExpandProperty id
```

Copia un ID devuelto en `spring.ai.openai.chat.options.model` y reinicia la
aplicacion. Un `model_not_found` indica que el ID ya no existe o tu cuenta no
tiene acceso; un `401` indica una API key rechazada y un `429` que se alcanzo el
limite de solicitudes.

El servicio construye el mensaje del sistema usando concatenacion:

```java
"Eres un asistente experto en " + dominio
```

Luego envia el prompt y la pregunta:

```java
return chatClient.prompt()
    .system(promptSistema)
    .user(pregunta)
    .call()
    .content();
```

Endpoints:

```text
GET  /api/v2/chat/salud
POST /api/v2/chat
POST /api/v2/chat/rapido?pregunta=...
```

Groq genera respuestas de lenguaje. Weka estima precios con un modelo numerico ya entrenado; son funcionalidades distintas.

## 12. Concatenacion y plantillas

### Concatenacion en Java

Concatenar es unir textos con `+`:

```java
"ROLE_" + usuario.getRol()
```

Si el rol es `ADMIN`, el resultado es `ROLE_ADMIN`.

Otro ejemplo:

```java
"Error: " + e.getMessage()
```

### Concatenacion en JavaScript

En los archivos `.js` se usan template literals con backticks:

```javascript
`/api/propiedades/${p.id}`
```

`${p.id}` inserta el valor de la propiedad dentro de la URL.

Tambien se generan fragmentos HTML:

```javascript
`
  <h3>${p.titulo}</h3>
  <p>$${p.precio.toLocaleString('es-CO')}</p>
`
```

El primer `$` es texto visible y `${...}` ejecuta una expresion JavaScript.

### Concatenacion de listas

Para construir una direccion se usa `join`:

```javascript
[
  direccion,
  barrio,
  ciudad,
  'Colombia'
].filter(Boolean).join(', ')
```

`filter(Boolean)` elimina valores vacios y `join(', ')` une el resto con coma y espacio.

## 13. Imagenes y GridFS

`ImageStorageService` guarda imagenes en GridFS, el sistema de archivos de MongoDB.

El servicio:

1. Valida que el archivo no este vacio.
2. Lee sus bytes.
3. Redimensiona y comprime JPG/PNG.
4. Conserva WEBP.
5. Crea un nombre seguro usando `slug`.
6. Guarda el archivo y sus metadatos.

Ejemplo de nombre construido:

```java
"propiedad-" + propertyId + "-" + slug(propertyTitle)
    + "-imagen-" + imageNumber + "-" + token + extension
```

`slug` normaliza un titulo como `Casa Niza` y lo convierte en un texto apto para nombre de archivo.

`ImagenController` expone:

```text
POST /api/imagenes/subir
GET  /api/imagenes/{id}
```

Las imagenes se sirven como recursos HTTP y tienen cache de un ano.

## 14. Frontend y llamadas fetch

Los archivos estaticos se encuentran en `src/main/resources/static`.

`anuncios.js` carga propiedades:

```javascript
const res = await fetch(
  'http://localhost:8080/api/propiedades/visibles');
const propiedades = await res.json();
```

Despues filtra con `filter` y genera las tarjetas con `map`.

`crear.js` crea una propiedad con `FormData` porque envia datos y archivos:

```javascript
const formData = new FormData();
formData.append('propiedad', new Blob([
  JSON.stringify(propiedad)
], { type: 'application/json' }));
```

El formulario se envia con `POST /api/propiedades`.

`actualizar.js` usa `PUT` para editar una propiedad.

## 15. Como ejecutar y probar

Desde PowerShell, en la raiz del proyecto:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GROQ_API_KEY = 'tu_clave' # Opcional
.\mvnw.cmd spring-boot:run
```

La web queda en:

```text
http://localhost:8080/
```

Pruebas:

```powershell
.\mvnw.cmd test
```

Comprobar el modelo predictivo:

```text
GET http://localhost:8080/api/prediccion/estado
```

Si devuelve `modeloCargado: true`, Weka encontro el modelo y el archivo ARFF.

## 16. Resumen mental

- Spring Boot inicia y conecta las piezas.
- Controllers exponen URLs.
- Services contienen logica reutilizable.
- Repositories hablan con MongoDB.
- Models representan documentos.
- Security decide quien puede entrar o modificar.
- Weka calcula una estimacion numerica de precio.
- Groq genera respuestas de texto mediante Spring AI.
- JavaScript consume los endpoints y actualiza la interfaz.
- HTML define la estructura y CSS define la apariencia.
