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

El campo `Usuario.rol` contiene uno de estos valores: `ADMIN`, `PROPIETARIO` o
`CLIENTE`. `CustomUserDetailsService` los transforma en las autoridades
`ROLE_ADMIN`, `ROLE_PROPIETARIO` y `ROLE_CLIENTE`.

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

Los tres roles usan el mismo formulario `/admin/login.html`; no comparten el
mismo panel. Spring procesa las credenciales en `/login` y el `successHandler`
de `SecurityConfig` redirige segun el rol autenticado.

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

La pagina de login es compartida. El rol se carga desde MongoDB por
`CustomUserDetailsService`; por eso la ruta de destino depende del valor real de
`Usuario.rol`, no del nombre escrito en el formulario.

`DataInitializer` crea las siguientes cuentas solo si no existen:

| Rol | Usuario | Contrasena de demostracion |
| --- | --- | --- |
| `ADMIN` | `admin` | `admin123` |
| `PROPIETARIO` | `propietario` | `propietario123` |
| `CLIENTE` | `cliente` | `cliente123` |

Estas credenciales son exclusivamente para desarrollo. Antes de publicar la
aplicacion hay que sustituirlas y no habilitar cuentas con contrasenas
conocidas.

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

## 17. Resumen mental

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
