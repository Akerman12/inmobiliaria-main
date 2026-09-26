# Bienes Raíces

Aplicación web de gestión inmobiliaria con Java 17, Spring Boot, Spring Security y MongoDB. Incluye un sitio público, un catálogo de propiedades, un panel administrativo y una API opcional de chat con Groq.

## Requisitos

- Java 17.
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

### Panel administrativo

- Acceso con Spring Security y rol `ADMIN`; las contraseñas se protegen con BCrypt.
- Alta y edición de propiedades con operación, tipo, precio, características, ubicación e imágenes.
- Gestión de visibilidad y administración de contactos.

### Compatibilidad con propiedades existentes

Las propiedades guardadas antes de incorporar estos campos no contienen `operacion` ni `tipo`. Para no perderlas ni requerir una migración, la aplicación las presenta como `venta` y `otro`, respectivamente. Se pueden clasificar editándolas desde el panel administrativo. Las propiedades nuevas requieren seleccionar ambos campos.

## Chat de Groq

La integración usa Spring AI con la API compatible con OpenAI de Groq. Configure `GROQ_API_KEY` en el entorno antes de iniciar la aplicación. El modelo predeterminado es `llama-3.3-70b-versatile` y la URL base es `https://api.groq.com/openai`.

Endpoints disponibles cuando la clave está configurada:

- `GET /api/v2/chat/salud`: estado del servicio y modelo.
- `POST /api/v2/chat`: recibe JSON con `pregunta` y, opcionalmente, `dominio`.
- `POST /api/v2/chat/rapido?pregunta=...&dominio=...`: alternativa con parámetros de consulta.

Ejemplo para `POST /api/v2/chat`:

```json
{
  "pregunta": "¿Qué debo revisar antes de comprar una vivienda?",
  "dominio": "bienes raíces en Colombia"
}
```

La clave no se guarda en el repositorio. La integración expone endpoints REST; no incluye por sí misma un widget de chat en las páginas.

## Tecnologías

- Java 17 y Spring Boot 3.4.
- Spring Web, Spring Security, Spring Data MongoDB y Spring AI.
- MongoDB.
- HTML, CSS y JavaScript.
- Maven Wrapper.
