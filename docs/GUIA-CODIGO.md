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
- Spring AI conectado a Groq para exponer endpoints de chat en el backend; no hay una interfaz de chat integrada en el sitio web.
- HTML, CSS y JavaScript estatico en `src/main/resources/static`.

En su alcance funcional actual, los visitantes pueden consultar el catalogo y
contactar a la inmobiliaria. Las cuentas `CLIENTE` pueden explorar propiedades
y solicitar visitas; las cuentas `PROPIETARIO` gestionan inmuebles asignados,
consultas y solicitudes de visita. El personal `ADMIN` gestiona propiedades,
propietarios y mensajes desde un panel protegido, que tambien incluye una
herramienta para estimar precios. La API de chat es una capacidad tecnica del
backend y no una funcion visible para los visitantes.

La aplicacion se ejecuta en el puerto `8080` y requiere una URI de MongoDB
mediante `MONGODB_URI`. No utiliza `localhost` como conexion de respaldo: si
falta la URI, el arranque falla en vez de conectarse silenciosamente a Compass
o a otra base local.

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
- `model/Visita.java`

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
- `repository/VisitaRepository.java`

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
- `static/ingresar.html` y `static/js/ingresar.js`
- `Service/CuentaService.java` y `controller/CuentaRestController.java`

### Carga del usuario

`CustomUserDetailsService` busca el usuario en MongoDB:

```java
Usuario usuario = usuarioRepository.findByUsername(username)
    .or(() -> usuarioRepository.findByEmail(username))
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

El campo `Usuario.rol` contiene uno de estos valores: `ADMIN`, `PROPIETARIO` o
`CLIENTE`. `CustomUserDetailsService` los transforma en las autoridades
`ROLE_ADMIN`, `ROLE_PROPIETARIO` y `ROLE_CLIENTE`.

En `SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.GET, "/api/propiedades/visibles", "/api/propiedades/*")
    .permitAll()
.requestMatchers(HttpMethod.GET, "/api/propiedades/**")
    .hasRole("ADMIN")
.requestMatchers("/api/propietario/**").hasRole("PROPIETARIO")
.requestMatchers("/api/cliente/**").hasRole("CLIENTE")
```

El orden de las reglas importa: primero se permiten solo el catalogo visible y
las fichas individuales; despues, el resto de lecturas de propiedades queda
para `ADMIN`. `PropiedadRestController` tambien devuelve `404` para una ficha
oculta cuando quien consulta no es administrador. Las imagenes se pueden leer
publicamente, pero la subida requiere `ADMIN`.

Resumen de permisos principales:

| Ruta | Permiso |
| --- | --- |
| `GET /api/propiedades/visibles` y `GET /api/propiedades/{id}` | Publico; la ficha oculta devuelve `404` |
| Otras lecturas y cambios en `/api/propiedades/**` | `ADMIN` |
| `GET /api/testimonios/visibles` | Publico |
| Resto de `/api/testimonios/**` | `ADMIN` |
| `POST /api/contacto` | Publico, con validacion en el servidor |
| Listado y cambios en `/api/contacto/**` | `ADMIN` |
| `/api/propietario/**` | `PROPIETARIO` |
| `/api/cliente/**` | `CLIENTE` |
| `GET /api/imagenes/**` | Publico; solo tipos de imagen permitidos |
| `POST /api/imagenes/subir` y `/api/admin/**` | `ADMIN` |
| `POST /api/cuenta/registro` | Publico; solo crea roles `CLIENTE` o `PROPIETARIO` |
| `/admin/**`, `/propietario/**`, `/cliente/**` | Sus respectivos roles |
| API de prediccion y endpoints POST del chat | Publicos y validados; igualmente protegidos con CSRF |

Las rutas CORS con `origins = "*"` se retiraron. El frontend usa rutas relativas
y consume las APIs desde el mismo origen.

### Cookies de sesion y proteccion CSRF

CSRF permanece habilitado en la cadena principal de Spring Security para
peticiones que cambian estado (`POST`, `PUT`, `PATCH`, `DELETE`). Los endpoints
publicos no estan exentos: publicar un contacto o enviar una pregunta al chat
tambien requiere el token.

`GET /api/csrf` devuelve el token y hace que Spring emita la cookie
`XSRF-TOKEN`. `static/js/csrf.js` obtiene el token al necesitarlo, agrega la
cabecera `X-XSRF-TOKEN` a `fetch` del mismo origen y agrega el campo `_csrf` a
formularios HTML antes de enviarlos. El script debe cargarse antes de los
scripts de pagina que envian escrituras. No registres el valor del token en
consola ni lo incluyas en URLs.

La cookie `XSRF-TOKEN` se configura con `SameSite=Lax` y sin `HttpOnly` porque
el script del sitio necesita leer el token; **no es una credencial de sesion**.
La cookie `JSESSIONID` es `HttpOnly` y `SameSite=Lax`. En desarrollo local,
`server.servlet.session.cookie.secure=false` permite HTTP; el perfil `prod`
activa `Secure=true`, por lo que el despliegue debe servirse por HTTPS. La
estrategia de cabeceras reenviadas permite que Spring detecte el HTTPS terminado
por el proxy del hosting.

Antes de probar una escritura manualmente desde Thunder Client o una herramienta
similar, solicita `GET /api/csrf`, conserva las cookies y envia el valor de
`XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN`. Un formulario o `fetch` sin token
debe ser rechazado por Spring Security.

### Validacion, carga de archivos y salida HTML

Las validaciones del navegador mejoran la experiencia, pero los controladores y
servicios vuelven a validar los datos: contactos limitan nombre, correo,
telefono y mensaje; testimonios limitan nombre, rol, mensaje y estrellas;
propiedades limitan campos, numeros, coordenadas y categorias; chat limita la
pregunta y el dominio. Los mensajes de visita tienen un maximo de 1000
caracteres. Los favoritos tienen un maximo de 100 elementos. No se debe confiar
en valores `id`, estados, rol o banderas enviados por el cliente; el servidor
impone sus valores y comprueba la propiedad/propietario correspondiente.

Las subidas aceptan un maximo de 10 MB por imagen y el decodificador verifica
que el contenido sea un formato compatible; no confies solo en la extension o
en el `Content-Type` del navegador. Las respuestas de imagen limitan el tipo
servido a JPEG, PNG o WEBP y agregan `X-Content-Type-Options: nosniff`.

Para renderizar datos de MongoDB en HTML, usa salida codificada en el contexto
correcto. `static/js/html-seguro.js` expone `escapeHtmlForHtml` y
`safeMediaUrl`; los scripts que usan estas funciones cargan el helper antes.
El escape HTML no valida URLs: usa `safeMediaUrl` para medios y no construyas
atributos `href`, `src`, estilos o manejadores inline con datos no confiables.
Para acciones sobre registros, prefiere atributos `data-*` y listeners a
interpolar IDs en `onclick`. La codificacion al renderizar protege las vistas,
pero no reemplaza la validacion al guardar los datos.

### Login y logout

```java
.formLogin(form -> form
    .loginPage("/admin/login.html")
    .loginProcessingUrl("/login")
    .successHandler((request, response, authentication) -> {
        boolean esAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean esPropietario = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_PROPIETARIO"));

        if (esAdmin) {
            response.sendRedirect("/admin/index.html");
        } else if (esPropietario) {
            response.sendRedirect("/propietario/index.html");
        } else {
            response.sendRedirect("/cliente/index.html");
        }
    })
)
```

El formulario `/admin/login.html` se conserva para administración. Las cuentas
de clientes y propietarios usan `/ingresar.html`, que permite iniciar sesión o
registrarse mediante `POST /api/cuenta/registro`. Las contraseñas se codifican
con BCrypt; el nombre de usuario de las nuevas cuentas es su correo electrónico.
`CustomUserDetailsService` acepta también el correo de cuentas existentes.
Spring procesa el inicio de sesión en `/login` y `SecurityConfig` redirige según
el rol autenticado. El formulario administrativo envía `adminLogin`; el
formulario público envía `publicLogin`. El manejador de éxito solo permite
`ROLE_ADMIN` en el acceso administrativo y solo `CLIENTE` o `PROPIETARIO` en el
público. Cuando el rol no corresponde, invalida la sesión antes de redirigir.

```java
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutSuccessHandler(...)
)
```

`BCryptPasswordEncoder` guarda contrasenas con hash, no en texto plano.

### Favoritos por cuenta

`CuentaRestController` expone el perfil autenticado y las rutas
`GET /api/cuenta/favoritos` y `PUT /api/cuenta/favoritos`. Los IDs favoritos se
guardan en el documento MongoDB de `Usuario`, de modo que siguen a la cuenta en
otros dispositivos. Las personas sin sesión pueden usar el almacenamiento
local del navegador; al iniciar sesión, esos favoritos se combinan con los de
la cuenta. El navegador no muestra una lista local perteneciente a otra cuenta.

El mismo controlador expone `GET` y `PUT /api/cuenta/perfil` y
`PATCH /api/cuenta/password`. El usuario autenticado solo puede editar su nombre
y celular; el correo se mantiene de solo lectura para no cambiar su identidad
de sesión. `CuentaService` valida los datos tanto en registro como en perfil:
el nombre debe tener al menos dos letras y no incluir números, y el celular
debe incluir entre 7 y 15 dígitos. El cambio de contraseña exige la clave
actual y almacena la nueva con BCrypt.

En el registro, `POST /api/cuenta/registro` solo admite los roles `CLIENTE` y
`PROPIETARIO`; el cliente no puede crear una cuenta administradora. El
formulario valida el nombre y el celular antes de enviarlos y el servicio
repite las comprobaciones en el servidor. Los nombres y contraseñas no son
únicos. El correo y el celular sí: el correo se compara sin distinguir
mayúsculas/minúsculas y el celular se normaliza a dígitos, ignorando espacios,
guiones y otros separadores. Si alguno ya pertenece a otra cuenta, el servidor
responde `409 Conflict` con un mensaje específico para ese campo. La unicidad
del celular se protege además con el índice sparse único
`celular_normalizado_unique_idx`; la comprobación por patrón también detecta
celulares existentes que se guardaron antes con formato visual.

### Tema y textos legales

`/js/tema.js` inicializa el tema claro por defecto, agrega el selector claro/oscuro
a la navegación pública y a los portales, y conserva la preferencia en el
almacenamiento local. `/css/identidad-tema.css` centraliza los colores azules de
marca y los estilos compartidos de los portales de cliente y propietario.
`/terminos.html` y `/privacidad.html` contienen textos originales del proyecto;
el formulario de registro y el de contacto enlazan ambos documentos y requieren
aceptar la casilla. La página de valoración expone los enlaces en su pie.

### Usuario administrador inicial

`DataInitializer` solo se ejecuta cuando se activa explicitamente el perfil
`demo` fuera de `prod`. Si no existe `admin`, crea uno y codifica su contrasena:

```java
admin.setPassword(passwordEncoder.encode("admin123"));
```

La clase tiene `@Profile("demo & !prod")`, por lo que las cuentas demo no se
crean en ejecuciones normales ni en produccion. No actives ese perfil al conectarte a
Atlas. Provisiona las cuentas reales directamente en Atlas con contrasenas
BCrypt. Cuentas demo que ya existan deben eliminarse manualmente despues de
verificar que no tengan propiedades o visitas asociadas.

No escribas URI de MongoDB, claves de Groq, contrasenas ni tokens en el
repositorio o en logs. `.env.local` es solo para desarrollo y esta excluido de
Git. En Render, configura `MONGODB_URI`, `SPRING_PROFILES_ACTIVE=prod` y, si
usas el chat, `GROQ_API_KEY` como variables privadas. No actives el perfil `demo`
en produccion.

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

## 11. API de chat del backend con Groq y Spring AI

Esta seccion documenta endpoints disponibles para pruebas o integraciones. En
el estado actual, el sitio web no incluye una pantalla, boton o widget para que
los visitantes conversen con este servicio.

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

Cuando se configura `GROQ_API_KEY`, estos endpoints del backend pueden generar
respuestas de lenguaje. Esto no forma parte de la interfaz actual del sitio.
Weka, en cambio, estima precios y su boton esta integrado en el formulario de
creacion del panel administrativo.

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

### Busqueda desde el inicio y en el catalogo

El formulario de `src/main/resources/static/index.html` envia la operacion y
el tipo seleccionados como `operacion` y `tipo`, y el texto del campo **Ciudad**
como `ciudad`. Estos parametros se aplican a sus filtros correspondientes en
`anuncio.html`; la ciudad no se interpreta como texto de busqueda libre.

El campo **¿Que estas buscando?** del catalogo usa el parametro `busqueda`.
`src/main/resources/static/js/anuncios.js` compara cada palabra significativa
escrita con el titulo, la descripcion, el barrio, la ciudad, la direccion, el
tipo y la operacion de las propiedades visibles. Se deben encontrar todas las
palabras para mostrar una propiedad. La comparacion ignora mayusculas y tildes,
y omite palabras de enlace comunes como "de", "en" y "con". Es una busqueda
local por texto; no interpreta el significado de una frase mediante
inteligencia artificial.

Por ejemplo, **Ciudad: Cartagena**, **Tipo: Apartamento** y **Operacion: Venta**
filtran esos campos por separado. En cambio, **¿Que estas buscando?:
apartamento con piscina** busca ambos terminos en los datos de cada propiedad.

`crear.js` crea una propiedad con `FormData` porque envia datos y archivos:

```javascript
const formData = new FormData();
formData.append('propiedad', new Blob([
  JSON.stringify(propiedad)
], { type: 'application/json' }));
```

El formulario se envia con `POST /api/propiedades`.

`actualizar.js` usa `PUT` para editar una propiedad.

### Contacto preferido en el panel administrativo

`src/main/resources/static/admin/contactos.html` muestra cada mensaje y usa el
campo `medioContacto` para elegir la acción principal:

- `telefono`: crea un enlace `tel:` con el celular.
- `correo` (tambien valor predeterminado): crea un enlace `mailto:` con asunto.
- `whatsapp`: crea un enlace `https://wa.me/` y abre WhatsApp en otra pestana.

La funcion `generarLinkContacto(c)` limpia el numero de WhatsApp y agrega el
indicativo de Colombia `57` cuando no viene incluido. El texto del mensaje se
codifica con `encodeURIComponent` e incluye el nombre y la propiedad consultada.
Si falta el correo o el telefono, el enlace se deshabilita en vez de abrir un
destino vacio. Los enlaces `tel:` y `mailto:` dependen de las aplicaciones
asociadas en el computador del administrador.

La cuadricula y el ajuste del contenido se definen en
`src/main/resources/static/css/app.css`: `auto-fill` conserva las columnas de
ancho minimo de 280 px, `min-width: 0` permite que el contenido se contraiga y
`overflow-wrap: anywhere` evita que nombres, direcciones o mensajes largos
desborden la tarjeta. Las acciones usan una cuadricula interna con espacio fijo
para eliminar y los botones principales apilados.

Los datos de contacto que se muestran en el sitio publico estan en
`src/main/resources/static/contacto.html`: telefono `+57 321 507 3671` y correo
`inmobiliariabelar@gmail.com`.

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

## 16. Portales, propiedad y atencion de clientes

### Inicio de sesion y autorizacion

`SecurityConfig` protege cada portal y sus APIs:

| Rol | Panel | APIs |
| --- | --- | --- |
| `ADMIN` | `/admin/**` | `/api/admin/**` y operaciones de gestion en `/api/propiedades/**` |
| `PROPIETARIO` | `/propietario/**` | `/api/propietario/**` |
| `CLIENTE` | `/cliente/**` | `/api/cliente/**` |

La administracion usa `/admin/login.html`; clientes y propietarios usan
`/ingresar.html` y pueden crear una cuenta. El rol se carga desde MongoDB por
`CustomUserDetailsService`; por eso la ruta de destino depende del valor real de
`Usuario.rol`, no de la opcion visual del formulario.

Por defecto no se crean cuentas de demostracion. `DataInitializer` solo corre
con el perfil opcional `demo` y no corre si `prod` tambien esta activo; nunca lo actives en Atlas o produccion. Si una
ejecucion anterior ya creo cuentas demo, el codigo no las elimina: revisa sus
relaciones y borralas manualmente de Atlas si corresponde.

### Asignacion de una propiedad

La relacion se guarda en `Propiedad.propietarioId`, que contiene el ID de un
documento `Usuario` con rol `PROPIETARIO`.

1. El administrador abre **Admin → Propiedades**.
2. Selecciona un propietario habilitado en la columna **Propietario** y guarda.
3. La interfaz llama a `GET /api/admin/propietarios` para listar propietarios
    habilitados y a `PATCH /api/admin/propiedades/{id}/propietario` para asignar
    o quitar la relacion.
4. `AdminPropiedadController` valida en el servidor que la cuenta exista, este
    habilitada y tenga rol `PROPIETARIO`.

La creacion desde el panel admin deja la propiedad sin asignar; no se vincula
al usuario administrador por defecto. El portal consulta las propiedades
mediante `GET /api/propietario/propiedades`. Las operaciones de cambio de estado
comprueban que `propietarioId` coincida con el usuario autenticado.

Hay una compatibilidad temporal para el usuario demo `propietario`: si no tiene
propiedades enlazadas, su lista puede mostrar todas las propiedades. Ese
fallback es solo de visualizacion; no autoriza a modificar una propiedad sin
asignacion real y debe retirarse antes de produccion.

### Panel del propietario

La pagina es `static/propietario/index.html` y su logica esta en
`static/js/propietario.js`. El dashboard presenta conteos de propiedades,
disponibilidad y solicitudes, un resumen de cartera y accesos a las secciones.
**Mis propiedades** muestra tarjetas con imagen, operacion, ubicacion,
caracteristicas, precio y cantidad de solicitudes. Permite buscar por nombre o
ciudad, filtrar por estado comercial, abrir la ficha y cambiar el estado entre
`DISPONIBLE` y `EN_PROCESO`.

El cambio usa `PATCH /api/propietario/propiedades/{id}/estado`. El controller
valida que la propiedad pertenezca al propietario autenticado. El contador
`Propiedad.visitas` representa solicitudes de visita registradas, no vistas de
la pagina.

El propietario tambien puede publicar con `POST /api/propietario/propiedades`
y editar con `PUT /api/propietario/propiedades/{id}`. Ambos endpoints reciben
multipart con los datos de la propiedad y hasta cuatro imagenes guardadas en
GridFS. El backend asigna el propietario desde la sesion, establece
`visible=false` y no acepta que el cliente indique otro propietario. Una
edicion vuelve a ocultar el anuncio para que el administrador lo revise de
nuevo. Los cambios de disponibilidad solo se permiten despues de la aprobacion.

`portal-cuenta.js` comparte entre ambos portales la edicion de nombre y celular
y el flujo de cambio de contrasena. El correo se presenta como solo lectura.

### Consultas

El formulario publico de `contacto.html` envia un `Contacto` a
`POST /api/contacto`. El campo `casaInteres` guarda el ID de la propiedad y
`atendido` comienza en `false`.

`GET /api/propietario/consultas` obtiene primero los IDs de las propiedades
asignadas al usuario y busca solo contactos cuyo `casaInteres` coincida. El
repositorio usa `findByCasaInteresInOrderByFechaCreacionDesc`; de esta forma un
propietario no recibe mensajes de propiedades ajenas. La interfaz muestra
pendientes y atendidas, permite buscar por cliente, inmueble o mensaje, filtrar
por estado y responder mediante `mailto:` o `tel:`.

Si una propiedad pasa a `EN_PROCESO`, la ficha publica oculta la accion de
contacto y el backend responde `409 Conflict` ante un intento directo de enviar
una consulta para ese inmueble.

### Solicitudes de visita

Una solicitud real es un documento de MongoDB en la coleccion `visitas`, modelado
por `Visita.java`. Guarda `propiedadId`, `propiedadTitulo`, `propietarioId`,
`clienteId`, nombre y correo del cliente, fecha/hora solicitada, mensaje,
`estado` y fecha de solicitud.

El cliente pulsa **Solicitar visita**, elige fecha y hora futuras y puede
escribir un mensaje. `POST /api/cliente/visitas` obtiene el cliente desde la
sesion autenticada; no confia en un ID de cliente enviado por el navegador.
Tambien comprueba que la propiedad exista, este visible, tenga propietario
asignado y no este `EN_PROCESO`. Las solicitudes se guardan como `SOLICITADA` y
el contador de la propiedad aumenta.

El cliente consulta sus propias solicitudes en `GET /api/cliente/visitas`. El
propietario obtiene solo las dirigidas a su cuenta con
`GET /api/propietario/visitas` y puede actualizar el estado con
`PATCH /api/propietario/visitas/{id}/estado`. El servidor valida la propiedad de
la solicitud y permite estas transiciones:

```text
SOLICITADA -> CONFIRMADA
SOLICITADA -> RECHAZADA
CONFIRMADA -> COMPLETADA
```

El portal del propietario muestra los datos y permite confirmar, rechazar o
marcar una visita confirmada como realizada. El portal del cliente presenta el
estado resultante en **Mis visitas**. Actualmente el sistema no envia
notificaciones por correo, no bloquea horarios ocupados ni sincroniza un
calendario externo; la solicitud queda pendiente hasta que el propietario la
gestione.

### Recorrido para probarlo

1. Inicia MongoDB y la aplicacion.
2. Inicia sesion como `admin` en `/admin/login.html`.
3. En **Propiedades**, asigna una propiedad visible y disponible a la cuenta
    `propietario`.
4. Cierra sesion e inicia como `cliente`; solicita una visita para una fecha
    futura.
5. Inicia como `propietario`, abre **Visitas** y confirma o rechaza la
    solicitud. En **Consultas** aparecen los mensajes asociados a sus
    propiedades.
6. Vuelve al portal del cliente para consultar el estado actualizado.

Si el propietario cambia la propiedad a `EN_PROCESO`, deja de aparecer en el
catalogo de solicitudes y no admite nuevos contactos ni visitas.

## 17. Configuracion y despliegue

`src/main/resources/application.properties` requiere una URI configurable:

```properties
spring.data.mongodb.uri=${MONGODB_URI}
```

Configura `MONGODB_URI` en `.env.local` para desarrollo o como secreto del
proveedor de hosting para produccion. Si la variable no existe, el arranque
falla en lugar de conectarse silenciosamente a `localhost`. No uses una URI con
credenciales en el repositorio. La base debe ser accesible desde la red del
proveedor y conservar GridFS, que almacena las imagenes de propiedades.

El frontend debe invocar la API con rutas del mismo origen, por ejemplo
`/api/propiedades/visibles`, no con URLs fijas a `localhost`: en Render,
`localhost` seria el navegador del visitante, no el servidor desplegado.

Para que VS Code use el mismo Atlas, Spring Boot importa opcionalmente
`.env.local` desde la raiz del proyecto. Crea ese archivo local con
`MONGODB_URI=<URI de Atlas>`; `.gitignore` lo excluye del repositorio. Reinicia
la sesion Java despues de cambiarlo. Si el archivo no existe, el arranque falla
en vez de usar `localhost`. No guardes la URI en `launch.json`,
`application.properties` ni en un commit.

Conectar ambas aplicaciones a Atlas no copia los documentos que ya estaban en
MongoDB local. Para migrar propiedades, testimonios, contactos e imagenes de
GridFS sin reemplazar las cuentas que ya existen en Atlas, usa MongoDB Database
Tools. Si el servidor local y Atlas tienen versiones diferentes, evita
restaurar directamente el archivo `mongodump`: Database Tools puede advertir
que esa combinacion de versiones no esta soportada. Canonical Extended JSON
conserva los tipos BSON y permite transferir los documentos entre versiones:

```powershell
$atlasUri = "mongodb+srv://USUARIO_ATLAS@CLUSTER_HOST/bienesraices?retryWrites=true&w=majority"
$respaldoAtlas = Join-Path $env:USERPROFILE 'bienesraices-atlas-before-migration.archive.gz'
mongodump --uri=$atlasUri --password --db=bienesraices --archive=$respaldoAtlas --gzip

$carpetaExport = Join-Path $env:USERPROFILE 'bienesraices-export'
New-Item -ItemType Directory -Path $carpetaExport -Force | Out-Null
$colecciones = @('propiedades', 'testimonios', 'contactos', 'imagenesPropiedades.files', 'imagenesPropiedades.chunks')
foreach ($coleccion in $colecciones) {
    $archivo = Join-Path $carpetaExport ($coleccion + '.json')
    mongoexport --uri="mongodb://localhost:27017/bienesraices" --db=bienesraices --collection=$coleccion --jsonFormat=canonical --out=$archivo
    if ($LASTEXITCODE -ne 0) { throw "Error al exportar $coleccion" }
}

foreach ($coleccion in $colecciones) {
    $archivo = Join-Path $carpetaExport ($coleccion + '.json')
    mongoimport --uri=$atlasUri --password --db=bienesraices --collection=$coleccion --mode=insert --stopOnError --maintainInsertionOrder --file=$archivo
    if ($LASTEXITCODE -ne 0) { throw "Error al importar $coleccion; detente y revisa Atlas antes de reintentar" }
}
```

Sustituye `USUARIO_ATLAS` y `CLUSTER_HOST` por los datos de Atlas, sin incluir
la contrasena. `--password` la solicita de forma interactiva en cada comando
que se conecta a Atlas. El respaldo previo y los JSON contienen datos personales:
guardalos fuera del repositorio. La exportacion incluye deliberadamente GridFS
(`imagenesPropiedades.files` y `.chunks`) y excluye `usuarios` para conservar las
cuentas existentes, y `visitas` porque guardan IDs de usuarios que podrian no
existir en Atlas. `--mode=insert` no sobrescribe documentos existentes; si hay
IDs duplicados, detente y revisa el resultado antes de reintentar. No uses
`--drop`.

Las propiedades conservan su `propietarioId` local. Despues de migrar, asigna
cada propiedad a la cuenta correcta desde **Admin → Propiedades** si su ID no
corresponde a un propietario existente en Atlas. Las visitas historicas se
pueden migrar despues de mapear sus IDs de cliente y propietario. Conserva los
respaldos hasta verificar propiedades, testimonios e imagenes en el sitio
desplegado; despues, eliminalos de forma segura.

En produccion activa `SPRING_PROFILES_ACTIVE=prod`. `DataInitializer` usa
`@Profile("demo & !prod")`, por lo que las cuentas demo solo se insertan al activar
deliberadamente ese perfil. Antes de arrancar la aplicacion, crea de manera segura una cuenta `ADMIN`
habilitada en la base de produccion con una contrasena BCrypt. Las cuentas
publicas de registro solo pueden obtener los roles `CLIENTE` y `PROPIETARIO`.

Requisitos y comprobaciones previas:

1. Java 21 y Maven (el wrapper `mvnw` esta incluido).
2. Ejecutar `.\mvnw.cmd test` en Windows o `./mvnw test` en macOS/Linux.
3. Configurar `MONGODB_URI` y `SPRING_PROFILES_ACTIVE=prod` en el panel privado
   del proveedor. `GROQ_API_KEY` es opcional; se requiere solo para el chat.
4. Construir el JAR con `.\mvnw.cmd clean package` o `./mvnw clean package` y
   ejecutar el artefacto `target/inmobiliaria-0.0.1-SNAPSHOT.jar`.
5. Probar autenticacion por rol, permisos de APIs, registro, peticiones con y
   sin token CSRF, cookies `Secure`/`HttpOnly`/`SameSite`, validacion de entradas,
   subida y lectura de imagenes, visitas, consultas y persistencia MongoDB antes
   de habilitar trafico publico.

Para detener una ejecucion local iniciada desde terminal, pulsa `Ctrl+C` en
esa terminal. En VS Code, usa el boton **Stop** de la sesion de depuracion.

## 18. Resumen mental

- Spring Boot inicia y conecta las piezas.
- Controllers exponen URLs.
- Services contienen logica reutilizable.
- Repositories hablan con MongoDB.
- Models representan documentos.
- Security decide quien puede entrar o modificar.
- Weka calcula una estimacion numerica de precio.
- La API de Groq puede generar respuestas de texto desde el backend, pero no tiene interfaz visual en el sitio.
- JavaScript consume los endpoints y actualiza la interfaz.
- HTML define la estructura y CSS define la apariencia.
