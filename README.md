# Bienes Raíces

Aplicación web de gestión inmobiliaria con Java 21, Spring Boot, Spring Security y MongoDB. Incluye un sitio público, un catálogo de propiedades, un panel administrativo y una API opcional de chat con Groq.

## Requisitos

- JDK 21.
- MongoDB activo en `localhost:27017`, con la base `bienesraices`.
- La clave `GROQ_API_KEY` solo es necesaria para activar el chat de Groq. El resto de la aplicación puede iniciarse sin ella.

## Ejecutar

En Windows, desde PowerShell:

```powershell
$env:GROQ_API_KEY = "tu_clave_de_groq" # Opcional: necesaria solo para el chat
.\mvnw.cmd spring-boot:run
```

En macOS o Linux:

```bash
export GROQ_API_KEY="tu_clave_de_groq" # Opcional: necesaria solo para el chat
./mvnw spring-boot:run
```

Sin configurar la clave, la web y el catálogo siguen disponibles; los endpoints de chat no se registran. La aplicación utiliza el puerto `8080` y MongoDB local según `src/main/resources/application.properties`.

- Sitio público: <http://localhost:8080/>
- Catálogo: <http://localhost:8080/anuncio.html>
- Acceso de administración: <http://localhost:8080/admin/login.html>

Para ejecutar las pruebas:

```powershell
.\mvnw.cmd test
```

## Funcionalidades

### Sitio público

- Búsqueda desde la página de inicio por ubicación, operación y tipo de inmueble.
- Catálogo con filtros por operación (venta o arriendo), tipo (casa, apartamento, villa u otro), ciudad, habitaciones, baños y precio máximo.
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

### Imágenes

Las imágenes nuevas se guardan en GridFS dentro de la misma base MongoDB, en las colecciones `imagenesPropiedades.files` e `imagenesPropiedades.chunks`, no en el disco del servidor. GridFS crea un documento en `.files` por archivo y uno o más bloques en `.chunks`; por eso varias imágenes producen varios documentos. Cada archivo incluye en `metadata` el ID y título de la propiedad, su número de imagen y el nombre original; el filename también identifica la propiedad. Las imágenes JPG y PNG se redimensionan a un máximo de 1600 px y se comprimen como JPEG; WEBP se conserva en su formato. Las URLs usan `/api/imagenes/{id}` y se sirven con caché HTTP. El bucket anterior `fs` sigue disponible para leer y eliminar imágenes ya guardadas allí; las rutas antiguas `/uploads/...` siguen disponibles mientras sus archivos locales existan.

Las fotos que ya faltaban del disco no se pueden reconstruir desde sus rutas guardadas en MongoDB. Hay que recuperarlas desde una copia anterior y volver a publicarlas.

### Compatibilidad con propiedades existentes

Las propiedades guardadas antes de incorporar estos campos no contienen `operacion` ni `tipo`. Para no perderlas ni requerir una migración, la aplicación las presenta como `venta` y `otro`, respectivamente. Se pueden clasificar editándolas desde el panel administrativo. Las propiedades nuevas requieren seleccionar ambos campos.

## Chat de Groq

La integración usa Spring AI con la API compatible con OpenAI de Groq. Configure `GROQ_API_KEY` en el entorno antes de iniciar la aplicación. El modelo configurado es `openai/gpt-oss-20b` y la URL base es `https://api.groq.com/openai`.

En Windows, desde PowerShell en la raíz del proyecto:

```powershell
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

La clave no se guarda en el repositorio. La integración expone endpoints REST; no incluye por sí misma un widget de chat en las páginas.

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
