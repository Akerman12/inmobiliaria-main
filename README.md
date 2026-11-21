# Bienes Raíces

Aplicación web de gestión inmobiliaria con Java 21, Spring Boot, Spring Security y MongoDB. Incluye un sitio público, un catálogo de propiedades, portales para clientes y propietarios, un panel administrativo y un chatbot flotante con permisos por rol conectado a Groq mediante Spring AI.

## Requisitos

- JDK 21.
- URI de MongoDB configurada en `.env.local` para desarrollo local o en el entorno de despliegue.
- El chat de Groq es opcional: requiere `SPRING_AI_MODEL_CHAT=openai` y la clave `GROQ_API_KEY`. El resto de la aplicación puede iniciarse sin ambas variables.

## Ejecutar

En Windows, desde PowerShell:

```powershell
$env:SPRING_AI_MODEL_CHAT = "openai" # Opcional: activa el chat
$env:GROQ_API_KEY = "tu_clave_de_groq" # Opcional: necesaria solo para el chat
.\mvnw.cmd spring-boot:run
```

En macOS o Linux:

```bash
export SPRING_AI_MODEL_CHAT="openai" # Opcional: activa el chat
export GROQ_API_KEY="tu_clave_de_groq" # Opcional: necesaria solo para el chat
./mvnw spring-boot:run
```

Sin configurar `SPRING_AI_MODEL_CHAT=openai` y `GROQ_API_KEY`, la web y el catálogo siguen disponibles; los endpoints `/api/v2/chat` no se registran y `/api/chatbot` se desactiva con elegancia devolviendo `503`. En VS Code, selecciona un JDK 21 para el proyecto Java. La aplicación utiliza el puerto `8080`. La conexión MongoDB requiere `MONGODB_URI`; no hay una conexión de respaldo a `localhost`.

Para detener la aplicación iniciada desde la terminal, pulsa `Ctrl+C` en esa terminal. Si se ejecuta desde VS Code, usa el botón **Stop** de la sesión de depuración.

Para que VS Code use Atlas, crea un archivo local `.env.local` en la raíz (está excluido de Git) con `MONGODB_URI=<URI privada de Atlas>`. Reinicia la aplicación para que Spring Boot cargue la URI. Si falta o es inválida, la aplicación debe fallar al conectar en vez de usar MongoDB local. No subas ese archivo ni pegues la URI en el repositorio.

- Sitio público: <http://localhost:8080/>
- Catálogo: <http://localhost:8080/anuncio.html>
- Valoración: <http://localhost:8080/valoracion.html>
- Ingreso y registro de clientes/propietarios: <http://localhost:8080/ingresar.html>
- Términos y condiciones: <http://localhost:8080/terminos.html>
- Política de privacidad: <http://localhost:8080/privacidad.html>
- Acceso de administración: <http://localhost:8080/admin/login.html>

Para ejecutar las pruebas:

```powershell
.\mvnw.cmd test
```

## Funcionalidades

### Sitio público

- Búsqueda desde la página de inicio por ubicación, operación y tipo de inmueble.
- Catálogo con filtros por operación (venta o arriendo), tipo (casa, apartamento, villa u otro), ciudad, habitaciones, baños y precio máximo.
- El catálogo permite alternar entre tarjetas y mapa; muestra marcadores azules interactivos para las propiedades con coordenadas.
- Valoración de referencia con el modelo existente de predicción de precios.
- Favoritos para visitantes en el navegador y sincronizados con la cuenta al iniciar sesión.
- Tema claro inicial con selector persistente para cambiar a modo oscuro; ambos portales comparten la identidad azul del logo.
- Páginas propias de términos y condiciones y privacidad, enlazadas desde el registro y los pies de página.
- Detalle de propiedad, galería de imágenes y formulario de contacto.
- Los parámetros de búsqueda del inicio se conservan al abrir el catálogo.
- Datos públicos de contacto: teléfono `+57 321 507 3671` y correo `inmobiliariabelar@gmail.com`.

### Panel administrativo

- Acceso con Spring Security y rol `ADMIN`; las contraseñas se protegen con BCrypt.
- Alta y edición de propiedades con operación, tipo, precio, características, ubicación e imágenes.
- Gestión de visibilidad y administración de contactos.
- Las tarjetas de contacto ofrecen una acción según el medio preferido: llamar, responder por correo o abrir WhatsApp con un mensaje preparado.
- Los números de WhatsApp sin indicativo reciben el prefijo colombiano `57`; si falta el dato requerido, la acción aparece deshabilitada.
- Las tarjetas conservan un ancho mínimo y ajustan textos largos y acciones para que permanezcan dentro de sus límites.

### Portales de propietario y cliente

La administración usa `/admin/login.html`, cuyo formulario solo acepta cuentas con rol `ADMIN`. Los clientes y propietarios inician sesión o se registran en `/ingresar.html`; el registro guarda las contraseñas con BCrypt y permite elegir el rol `CLIENTE` o `PROPIETARIO`. Los formularios de acceso distinguen su intención y el servidor invalida la sesión si se autentica un rol distinto al permitido. Se puede iniciar sesión con el correo o el nombre de usuario. Después de autenticar, Spring Security redirige cada cuenta a su portal:

- `ADMIN`: `/admin/index.html`.
- `PROPIETARIO`: `/propietario/index.html`.
- `CLIENTE`: `/cliente/index.html`.

El panel del propietario muestra su cartera en tarjetas, permite buscar por nombre o ciudad, filtrar por disponibilidad y cambiar el estado comercial entre `DISPONIBLE` y `EN_PROCESO`. También incluye consultas y solicitudes de visita asociadas a sus propiedades. Los propietarios pueden publicar y editar inmuebles; los anuncios quedan ocultos hasta la revisión administrativa. Ambos portales incluyen edición de perfil y cambio de contraseña. El portal del cliente permite explorar propiedades disponibles, guardar favoritos vinculados a su cuenta, solicitar una visita indicando fecha y hora, y consultar el estado de sus solicitudes. Los favoritos guardados antes de ingresar se pueden migrar a la cuenta al iniciar sesión. Los datos de registro y perfil se validan en el servidor. El nombre y la contraseña pueden repetirse; cada correo y celular debe ser único, y la interfaz indica cuál dato ya está en uso.

Ambos portales usan el mismo tema azul y ofrecen un selector persistente de modo claro/oscuro. El registro y el formulario de contacto enlazan a los términos y a la política de privacidad del proyecto; valoración también incluye estos enlaces.

#### Asignar una propiedad

La asignación se administra desde **Admin → Propiedades**. Selecciona una cuenta habilitada con rol `PROPIETARIO` en la columna **Propietario** y guarda el cambio. La propiedad almacena el ID de la cuenta en `propietarioId`; los mensajes y las solicitudes de visita se filtran con esa relación. También se puede elegir **Sin asignar**. Las propiedades creadas por el administrador quedan sin propietario hasta que se asignen desde esta pantalla.

#### Consultas

Los mensajes enviados desde el formulario público se guardan en MongoDB. En el portal del propietario, **Consultas** muestra solo los mensajes que hacen referencia a propiedades realmente asignadas a esa cuenta. Permite buscar por cliente, inmueble o texto del mensaje, filtrar entre pendientes y atendidas, y responder por correo o teléfono.

#### Solicitudes de visita

Desde el portal del cliente, **Solicitar visita** abre un formulario para elegir fecha y hora futuras y añadir un mensaje opcional. La solicitud se guarda en la colección `visitas` con la propiedad, el cliente y el propietario correspondiente. Las nuevas solicitudes quedan `SOLICITADA`; el propietario puede cambiarlas a `CONFIRMADA` o `RECHAZADA`, y marcar una visita confirmada como `COMPLETADA`. El cliente puede consultar el estado desde **Mis visitas**.

Solo se admiten visitas para propiedades visibles, asignadas a un propietario y que no estén en `EN_PROCESO`. Ese estado también quita la propiedad del catálogo de clientes y deshabilita el contacto desde su ficha pública.

#### Planificación matemática de visitas

En **Propietario → Visitas**, cada propietario registra bloques disponibles de una hora, en horas exactas y hasta con 90 días de anticipación. El sistema solo considera las solicitudes de sus inmuebles y propone horarios iguales o posteriores a la fecha solicitada por el cliente. La propuesta no se guarda hasta que el propietario la confirma; al confirmarla, las visitas quedan programadas y confirmadas, y los bloques utilizados se reservan.

Para cada solicitud `i` y horario `j`, `xᵢⱼ = 1` cuando se asigna la solicitud a ese horario y `0` en otro caso. El modelo maximiza primero la cantidad de solicitudes atendidas y, entre soluciones con la misma cantidad, minimiza los minutos de espera desde la solicitud hasta la visita:

```text
Maximizar M × Σᵢⱼ xᵢⱼ − Σᵢⱼ esperaᵢⱼ × xᵢⱼ
Sujeto a:
  Σⱼ xᵢⱼ ≤ 1 para cada solicitud
  Σᵢ xᵢⱼ ≤ 1 para cada horario (bloques de una hora, sin cruces)
  xᵢⱼ = 0 si el horario es anterior a la fecha solicitada
  xᵢⱼ ∈ {0, 1}
```

`M` se elige mayor que la máxima espera acumulada posible, para que atender una solicitud adicional tenga prioridad sobre cualquier reducción de espera. La implementación resuelve el modelo de asignación con Simplex (Apache Commons Math); la estructura de emparejamiento solicitud-horario produce soluciones enteras para este modelo. La propuesta muestra cada fecha asignada, la espera por solicitud, el total de solicitudes atendidas y las que quedan sin horario compatible.

La interfaz web se comunica con la API autenticada del propietario: `POST /api/propietario/visitas/disponibilidad` registra un bloque; `POST /api/propietario/visitas/planificar` calcula sin guardar cambios; y `POST /api/propietario/visitas/planificar/confirmar` valida de nuevo la propuesta óptima, programa las solicitudes y reserva los bloques en MongoDB. La API permite hasta 50 solicitudes y 50 horarios por cálculo. El cliente consulta la fecha programada desde **Mis visitas**.

#### Cuentas de demostración

Por defecto no se crean cuentas de demostración. `DataInitializer` solo se activa si se solicita explícitamente el perfil `demo` (y no `prod`); no actives ese perfil al conectarte a Atlas ni en producción. Si alguna ejecución anterior ya creó cuentas demo, eliminarlas de Atlas requiere una acción manual después de verificar que no tengan propiedades o visitas asociadas. Antes de desplegar, provisiona de forma segura las cuentas reales necesarias con contraseñas BCrypt.

## Despliegue

La aplicación requiere Java 21 y MongoDB. Para publicarla, conecta el repositorio a un proveedor que ejecute servicios Java (por ejemplo, Render) y configura las variables de entorno desde el panel privado del proveedor:

| Variable | Requerida | Uso |
| --- | --- | --- |
| `MONGODB_URI` | Sí | URI de MongoDB Atlas o de otra base accesible desde el servidor. |
| `SPRING_PROFILES_ACTIVE` | Sí | Usa `prod`; las cuentas demo solo se crean con el perfil explícito `demo`. |
| `SPRING_AI_MODEL_CHAT` | No | Usa `openai` para activar el modelo de chat; por defecto está desactivado (`none`). |
| `GROQ_API_KEY` | No | Clave de Groq; necesaria junto con `SPRING_AI_MODEL_CHAT=openai` para registrar la API de chat. |

En MongoDB Atlas, crea una base de producción separada, crea un usuario de base de datos con permisos mínimos y permite conexiones entrantes desde el proveedor de hosting. No uses `localhost` como URI de MongoDB desplegada: apuntaría al propio servidor. La URI contiene credenciales; guárdala solo como secreto del proveedor.

El perfil `prod` activa `Secure` para la cookie de sesión. Las sesiones usan además `HttpOnly` y `SameSite=Lax`; las operaciones que modifican datos requieren token CSRF. La cookie `XSRF-TOKEN` no autentica al usuario y debe ser legible por el navegador para que el frontend pueda enviar ese token.

### Seguridad y validaciones

- Spring Security protege los portales y APIs por rol: `ADMIN`, `PROPIETARIO` y `CLIENTE`. Las lecturas administrativas de propiedades y contactos requieren `ADMIN`; el catálogo público solo entrega propiedades visibles y las fichas ocultas responden como no encontradas.
- Las solicitudes que modifican datos requieren CSRF. El endpoint `GET /api/csrf` inicializa el token; `/js/csrf.js` lo añade a los `fetch` de escritura y a los formularios del mismo origen. No desactives CSRF para endpoints públicos como el chat o el formulario de contacto.
- Los datos recibidos en contactos, testimonios, propiedades, mensajes de visitas y chat tienen límites y validaciones en el servidor. Las imágenes se limitan a 10 MB por archivo y solo se sirven como JPEG, PNG o WEBP.
- Los valores dinámicos de la base se escapan al insertarse en HTML. `/js/html-seguro.js` centraliza el escape y valida las URLs de medios; no insertes datos de usuario con `innerHTML` sin escape contextual.
- Las rutas CORS abiertas (`*`) se eliminaron. El frontend debe consumir las APIs del mismo origen.

Antes de habilitar tráfico público:

1. Provisiona una cuenta `ADMIN` segura en la base de producción; el perfil `prod` no crea usuarios automáticamente.
2. Ejecuta las pruebas (`.\mvnw.cmd test` en Windows o `./mvnw test` en macOS/Linux).
3. Configura el proveedor para compilar con Maven usando Java 21 y ejecutar el JAR Spring Boot generado en `target/`.
4. Comprueba login por rol, registro, permisos de APIs, envío de formularios con CSRF, cookies `Secure`/`HttpOnly`/`SameSite`, subida/lectura de imágenes en GridFS y conexión con MongoDB.

Las imágenes subidas se guardan en GridFS, por lo que permanecen en MongoDB y no dependen del disco efímero del servidor.

### Imágenes

Las imágenes nuevas se guardan en GridFS dentro de la misma base MongoDB, en las colecciones `imagenesPropiedades.files` e `imagenesPropiedades.chunks`, no en el disco del servidor. GridFS crea un documento en `.files` por archivo y uno o más bloques en `.chunks`; por eso varias imágenes producen varios documentos. Cada archivo incluye en `metadata` el ID y título de la propiedad, su número de imagen y el nombre original; el filename también identifica la propiedad. Las imágenes JPG y PNG se redimensionan a un máximo de 1600 px y se comprimen como JPEG; WEBP se conserva en su formato. Las URLs usan `/api/imagenes/{id}` y se sirven con caché HTTP. El bucket anterior `fs` sigue disponible para leer y eliminar imágenes ya guardadas allí; las rutas antiguas `/uploads/...` siguen disponibles mientras sus archivos locales existan.

Las fotos que ya faltaban del disco no se pueden reconstruir desde sus rutas guardadas en MongoDB. Hay que recuperarlas desde una copia anterior y volver a publicarlas.

### Compatibilidad con propiedades existentes

Las propiedades guardadas antes de incorporar estos campos no contienen `operacion` ni `tipo`. Para no perderlas ni requerir una migración, la aplicación las presenta como `venta` y `otro`, respectivamente. Se pueden clasificar editándolas desde el panel administrativo. Las propiedades nuevas requieren seleccionar ambos campos.

## Chat de Groq

La integración usa Spring AI con la API compatible con OpenAI de Groq. Configure `SPRING_AI_MODEL_CHAT=openai` y `GROQ_API_KEY` en el entorno antes de iniciar la aplicación. El modelo configurado es `openai/gpt-oss-20b` y la URL base es `https://api.groq.com/openai`.

En Windows, desde PowerShell en la raíz del proyecto:

```powershell
$env:SPRING_AI_MODEL_CHAT = "openai"
$env:GROQ_API_KEY = "tu_clave_de_groq"
.\mvnw.cmd spring-boot:run
```

La clave no se guarda en el repositorio. Para listar los modelos disponibles para esa clave y elegir uno distinto:

```powershell
$headers = @{ Authorization = "Bearer $env:GROQ_API_KEY" }
(Invoke-RestMethod -Uri "https://api.groq.com/openai/v1/models" -Headers $headers).data |
  Select-Object -ExpandProperty id
```

Configura el ID elegido en `spring.ai.openai.chat.options.model` de `src/main/resources/application.properties` y reinicia la aplicación. Si Groq responde `model_not_found`, el modelo no existe o la cuenta no tiene acceso.

Endpoints disponibles cuando la clave está configurada:

- `GET /api/v2/chat/salud`: estado del servicio y modelo.
- `POST /api/v2/chat`: recibe JSON con `pregunta` y, opcionalmente, `dominio`.
- `POST /api/v2/chat/rapido?pregunta=...&dominio=...`: alternativa con parámetros de consulta.

Para probar el chat en Thunder Client, usa `POST http://localhost:8080/api/v2/chat`, selecciona **Body → JSON** y envía:

```json
{
  "pregunta": "¿Qué debo revisar antes de comprar una vivienda?",
  "dominio": "bienes raíces en Colombia"
}
```

La respuesta exitosa incluye `respuesta`, `modelo` y `dominio`. Los errores del proveedor se devuelven como JSON `502`; el mensaje diferencia entre API key rechazada, modelo no disponible y límite de solicitudes.

Ejemplo para `POST /api/v2/chat`:

```json
{
  "pregunta": "¿Qué debo revisar antes de comprar una vivienda?",
  "dominio": "bienes raíces en Colombia"
}
```

La clave no se guarda en el repositorio. Además de `/api/v2/chat`, el sitio incluye un **widget de chatbot flotante** (`static/js/chatbot.js` y estilos en `css/app.css`) en las páginas públicas y en los paneles `/cliente`, `/propietario` y `/admin`, conectado a `GET /api/chatbot/estado` y `POST /api/chatbot` con filtrado estricto por rol en `ChatContextService` (`SecurityContext`), límite de 500 caracteres, rate limit (`ChatbotRateLimiter`), escape HTML contra XSS y desactivación elegante (`503`) si falta `GROQ_API_KEY`. Consulta [docs/GUIA-CODIGO.md](docs/GUIA-CODIGO.md) para el detalle técnico completo.

## Predicción de precios

El proyecto utiliza Weka `3.8.6` mediante Maven y carga el modelo entrenado desde `src/main/resources/modelo_precio.model`. Los datos de entrenamiento están en `src/main/resources/propiedades_colombia.arff`.

No es necesario instalar Weka para ejecutar la aplicación: Maven descarga la dependencia automáticamente. Para analizar el modelo manualmente, se puede instalar Weka 3.8.x para Windows y abrir el archivo ARFF desde **Explorer → Preprocess → Open file...**. Después, en **Classify**, se puede cargar `modelo_precio.model` desde las opciones del clasificador.

## Tecnologías

- Java 21 y Spring Boot 3.4.
- Spring Web, Spring Security, Spring Data MongoDB y Spring AI.
- Weka 3.8.6 para el modelo de predicción de precios.
- MongoDB.
- HTML, CSS y JavaScript.
- Maven Wrapper.
