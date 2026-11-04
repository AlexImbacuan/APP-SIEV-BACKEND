# APP-SIEV-BACKEND

API REST del sistema SIEV. Gestiona proyectos, empleados, usuarios, roles y permisos,
con autenticación por JWT y control de acceso por roles (RBAC). Cada acción importante
queda registrada en una tabla de auditoría.

## Tecnologías

- Java 21
- Spring Boot 3.5 (Web, Data JPA, Security)
- PostgreSQL (Supabase) en producción y H2 en memoria para los tests
- JWT con la librería `jjwt` 0.12
- Apache PDFBox 3 para leer las cotizaciones en PDF de Siigo
- Maven (incluye el wrapper `mvnw`)
- Docker (build multi-etapa)

## Estructura del código

```
src/main/java/com/ejemplo/demo/
├── configuracion/   Seguridad, CORS, datos iniciales y verificación de variables de entorno
├── controlador/     Endpoints REST y manejo global de errores
├── dto/             Objetos de entrada y salida de la API
├── modelo/          Entidades JPA (Usuario, Rol, Permiso, Proyecto, Empleado, Cotizacion, Auditoria, RefreshToken)
├── repositorio/     Repositorios Spring Data JPA
├── seguridad/       JWT, filtro de autenticación y catálogo de permisos (Permisos.java)
└── servicio/        Lógica de negocio
```

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_PASSWORD` | Sí | Contraseña de la base de datos PostgreSQL |
| `JWT_SECRET` | Sí | Clave para firmar los tokens. Mínimo 32 caracteres |
| `ADMIN_EMAIL` | No | Email del usuario SUPERADMIN que se crea al arrancar |
| `ADMIN_PASSWORD` | No | Contraseña de ese SUPERADMIN |
| `SWAGGER_HABILITADO` | No | `true` para encender Swagger. Por defecto está apagado (ver sección Swagger) |
| `CORS_ORIGENES` | No | Orígenes permitidos, separados por comas. Por defecto: `http://localhost:3000,https://sievsas.com,https://www.sievsas.com` |

Si falta una variable obligatoria, o `JWT_SECRET` es demasiado corta, la aplicación no
arranca y muestra un mensaje claro indicando qué falta (`VerificadorVariablesEntorno`).

La URL y el usuario de la base de datos están en `src/main/resources/application.properties`.

## Cómo ejecutar

### Con Maven

```bash
# Linux / macOS / Git Bash
export SPRING_DATASOURCE_PASSWORD=...
export JWT_SECRET=una-clave-larga-de-al-menos-32-caracteres
./mvnw spring-boot:run
```

```powershell
# PowerShell
$env:SPRING_DATASOURCE_PASSWORD = "..."
$env:JWT_SECRET = "una-clave-larga-de-al-menos-32-caracteres"
.\mvnw.cmd spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

En IntelliJ, las variables se configuran en *Run → Edit Configurations → Environment variables*.

### Con Docker

El `Dockerfile` compila el JAR dentro del contenedor, así que no hace falta tener Maven instalado.

```bash
docker build -t siev-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e JWT_SECRET=... \
  -e ADMIN_EMAIL=admin@ejemplo.com \
  -e ADMIN_PASSWORD=... \
  siev-backend
```

### Tests

```bash
./mvnw test
```

Los tests usan H2 en memoria (`src/test/resources/application.properties`), así que no
tocan la base de datos real ni necesitan variables de entorno.

## Documentación interactiva (Swagger)

Swagger está **apagado por defecto** para que nunca quede expuesto en producción.
Para usarlo en local, define la variable de entorno `SWAGGER_HABILITADO=true`
(en IntelliJ: *Run → Edit Configurations → Environment variables*).

En producción (Render) además están definidas `SPRINGDOC_API_DOCS_ENABLED=false` y
`SPRINGDOC_SWAGGER_UI_ENABLED=false` como protección extra. No definir `SWAGGER_HABILITADO` allí.

Con la aplicación en marcha y Swagger encendido:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Especificación OpenAPI (JSON)**: `http://localhost:8080/v3/api-docs`

Se genera automáticamente a partir de los controladores con
[springdoc-openapi](https://springdoc.org/), así que siempre está al día con el código.

Para probar endpoints protegidos desde Swagger UI:

1. Ejecutar `POST /api/auth/login` y copiar el `accessToken` de la respuesta.
2. Pulsar el botón **Authorize** y pegar el token (sin escribir `Bearer`).
3. Desde ese momento, todas las peticiones se envían con el token.

La configuración está en `configuracion/OpenApiConfiguracion.java`. Las rutas de Swagger
son públicas (ver `SeguridadConfiguracion`).

## Autenticación

1. Iniciar sesión con `POST /api/auth/login`:

   ```json
   { "email": "admin@ejemplo.com", "password": "..." }
   ```

   Respuesta:

   ```json
   { "accessToken": "eyJ...", "refreshToken": "..." }
   ```

2. Enviar el token en cada petición:

   ```
   Authorization: Bearer eyJ...
   ```

El `accessToken` expira a los **15 minutos** y el `refreshToken` a los **7 días**.

3. Cuando el `accessToken` expira (la API responde **401**), pedir uno nuevo con
   `POST /api/auth/refresh`:

   ```json
   { "refreshToken": "..." }
   ```

   Devuelve un `accessToken` y un `refreshToken` **nuevos** (misma forma que el login).
   Hay que guardar los dos: el refresh token anterior deja de servir (rotación).
   Si el refresh también responde 401 (expirado, ya usado, sesión cerrada o usuario
   desactivado), hay que volver al login.

4. Al cerrar sesión, llamar a `POST /api/auth/logout` con `{ "refreshToken": "..." }`
   (responde 204) y borrar ambos tokens del frontend.

Cada usuario tiene **una sola sesión renovable**: si inicia sesión en otro dispositivo,
el refresh token del anterior deja de servir.

`POST /api/auth/register` es público y crea un usuario activo **sin rol**: no puede hacer
nada hasta que un SUPERADMIN le asigne uno.

Las rutas de autenticación responden tanto en `/auth/*` como en `/api/auth/*`.

## Roles y permisos (RBAC)

Todas las rutas requieren token salvo `login`, `register`, `refresh` y `logout`. Además, cada endpoint exige un
permiso concreto mediante `@PreAuthorize`.

Al arrancar, `InicializadorDatos` crea los permisos y roles que falten (nunca duplica):

| Rol | Permisos por defecto |
|---|---|
| `SUPERADMIN` | Todos (se sincroniza en cada arranque) |
| `SUPERVISOR` | `proyectos:*`, `empleados:*`, `cotizaciones:*`, `ventas:ver` |
| `TECNICO` | `proyectos:ver`, `empleados:ver`, `cotizaciones:ver`, `trabajador:ver` |

Catálogo de permisos (definido en `seguridad/Permisos.java`):

| Permiso | Descripción |
|---|---|
| `proyectos:ver` / `crear` / `editar` / `eliminar` | CRUD de proyectos |
| `empleados:ver` / `crear` / `editar` / `eliminar` | CRUD de empleados |
| `usuarios:gestionar` | Crear, editar, activar/desactivar usuarios y asignarles rol |
| `roles:gestionar` | Crear roles y asignarles permisos |
| `auditoria:ver` | Consultar el registro de auditoría |
| `cotizaciones:ver` | Ver cotizaciones y la lista de responsables |
| `cotizaciones:crear` | Crear cotizaciones (a mano o leyendo el PDF de Siigo) |
| `cotizaciones:editar` | Editar datos, materiales, notas y número de factura |
| `cotizaciones:aprobar` | Cambiar el estado (pendiente / aprobada / no aprobada) |
| `cotizaciones:eliminar` | Eliminar cotizaciones |
| `administracion:ver` | Ver el módulo de administración |
| `ventas:ver` | Ver el módulo de ventas |
| `trabajador:ver` | Ver el módulo de trabajador |

El SUPERADMIN puede cambiar los permisos de cualquier rol desde la API; esos cambios se
respetan en los siguientes arranques.

### Agregar un módulo nuevo

1. Añadir sus permisos a `Permisos.CATALOGO` (y, si corresponde, a `POR_DEFECTO_SUPERVISOR` o `POR_DEFECTO_TECNICO`).
2. Proteger cada endpoint con `@PreAuthorize("hasAuthority('modulo:accion')")`.
3. Al reiniciar, el permiso se crea en la BD y se asigna a los roles que lo tengan por defecto.

## Endpoints

### Autenticación — `/api/auth`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | Público | Inicia sesión y devuelve los tokens |
| POST | `/api/auth/refresh` | Público | Cambia el refresh token por tokens nuevos |
| POST | `/api/auth/logout` | Público | Invalida el refresh token |
| POST | `/api/auth/register` | Público | Registra un usuario sin rol |
| GET | `/api/auth/me` | Autenticado | Usuario actual con su rol y permisos |

### Usuarios — `/api/usuarios` (requiere `usuarios:gestionar`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/usuarios` | Listar usuarios |
| GET | `/api/usuarios/{id}` | Obtener un usuario |
| POST | `/api/usuarios` | Crear: `{ "nombre", "email", "password", "rolId" }` |
| PUT | `/api/usuarios/{id}` | Editar: `{ "nombre", "email", "password" }` |
| PATCH | `/api/usuarios/{id}/activo` | Activar o desactivar: `{ "activo": false }` |
| PUT | `/api/usuarios/{id}/rol` | Asignar rol: `{ "rolId": 2 }` (`null` lo quita) |

### Roles y permisos — `/api` (requiere `roles:gestionar`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/roles` | Listar roles. También permitido con `usuarios:gestionar` (para el selector de rol) |
| POST | `/api/roles` | Crear: `{ "nombre", "descripcion", "permisos": [...] }` |
| PUT | `/api/roles/{id}/permisos` | Reemplazar permisos: `{ "permisos": ["proyectos:ver"] }` |
| GET | `/api/permisos` | Listar todos los permisos |

### Proyectos — `/api/proyectos`

Campos: `serial` (ID), `descripcionGlobal`, `valorTotal`, `factura`, `pago`, `observacion`.

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/proyectos` | `proyectos:ver` |
| GET | `/api/proyectos/{serial}` | `proyectos:ver` |
| POST | `/api/proyectos` | `proyectos:crear` |
| PUT | `/api/proyectos/{serial}` | `proyectos:editar` |
| DELETE | `/api/proyectos/{serial}` | `proyectos:eliminar` |

### Empleados — `/api/empleados`

Campos: `idEmpleado`, `nombre`, `departamento`, `sueldo`.

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/empleados` | `empleados:ver` |
| GET | `/api/empleados/{id}` | `empleados:ver` |
| GET | `/api/empleados/departamento/{departamento}` | `empleados:ver` |
| GET | `/api/empleados/sueldo-mayor-a/{sueldo}` | `empleados:ver` |
| POST | `/api/empleados` | `empleados:crear` |
| PUT | `/api/empleados/{id}` | `empleados:editar` |
| DELETE | `/api/empleados/{id}` | `empleados:eliminar` |

### Cotizaciones — `/api/cotizaciones`

Cotizaciones a clientes. Se crean a mano o leyendo el PDF exportado de **Siigo**
(el PDF **nunca se guarda**: solo se lee para llenar el formulario).

Campos: `numero` (único, ej. `C-1-2562`), `cliente`, `ingSolicitante`, `estado`
(`PENDIENTE` / `APROBADA` / `NO_APROBADA`), `motivoRechazo`, `responsable` (usuario del
sistema), `items` (materiales: `descripcion`, `cantidad`, `valorUnitario`, `valorBruto`),
`subtotal`, `iva`, `total`, `numeroFactura`, `facturada` (calculado: tiene número de
factura), `notas`, `avisos` (valores que no cuadran; son advertencias, no errores).

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/cotizaciones` | `cotizaciones:ver` | Lista paginada. Filtros: `estado`, `responsableId`, `facturada`, `texto` (número, cliente o ingeniero), `pagina`, `tamano` |
| GET | `/api/cotizaciones/{id}` | `cotizaciones:ver` | Detalle con materiales y avisos |
| GET | `/api/cotizaciones/responsables` | `cotizaciones:ver` | Usuarios activos con rol, para escoger el responsable |
| POST | `/api/cotizaciones/leer-pdf` | `cotizaciones:crear` | `multipart/form-data` con el campo `archivo` (máx. 10 MB). Devuelve los datos leídos, avisos e `idExistente` si el número ya existe. **No guarda nada** |
| POST | `/api/cotizaciones` | `cotizaciones:crear` | Crear (queda `PENDIENTE`). `subtotal`, `iva`, `total` y `valorBruto` son opcionales: si no vienen se calculan (IVA 19 %) |
| PUT | `/api/cotizaciones/{id}` | `cotizaciones:editar` | Reemplaza todos los datos, incluidos los materiales. No cambia el estado |
| PATCH | `/api/cotizaciones/{id}/estado` | `cotizaciones:aprobar` | `{ "estado": "NO_APROBADA", "motivoRechazo": "..." }` (el motivo es obligatorio solo para `NO_APROBADA`) |
| DELETE | `/api/cotizaciones/{id}` | `cotizaciones:eliminar` | Eliminar |

El `numeroFactura` es texto libre por ahora; cuando exista el módulo de Facturación pasará
a enlazar con la factura.

### Auditoría — `/api/auditoria` (requiere `auditoria:ver`)

Solo lectura. Filtros opcionales: `entidad`, `entidadId`, `actor`, `accion`, `desde`,
`hasta` (fechas `AAAA-MM-DD`), `pagina` (por defecto 0) y `tamano` (por defecto 20).

```
GET /api/auditoria?entidad=USUARIO&entidadId=5
GET /api/auditoria?actor=admin&desde=2026-11-01&hasta=2026-11-30&pagina=0&tamano=20
```

### Módulos del frontend

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/administracion` | `administracion:ver` |
| GET | `/api/ventas` | `ventas:ver` |
| GET | `/api/trabajador` | `trabajador:ver` |

Por ahora estos endpoints solo devuelven el contenido base del módulo. Los endpoints
reales de cada módulo se irán agregando en su controlador.

## Errores

Todos los errores se devuelven en JSON (`ManejadorErrores` y `ManejadorSeguridadJson`):

- **401**: no hay token, o el token es inválido o expiró.
- **403**: el usuario no tiene el permiso que exige el endpoint.
