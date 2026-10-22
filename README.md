# Casa

Aplicacion Android privada para organizar un hogar entre dos dispositivos sin
servidor central ni cuenta en la nube. Cada telefono conserva una copia local
en Room y comparte cambios directamente por la red Wi-Fi domestica.

Este documento describe el estado real del repositorio tras las fases de
auditoria, correccion y consolidacion. Distingue entre codigo implementado,
pruebas automaticas y validacion manual.

## Estado resumido

| Area | Implementada | Sincronizable | Validacion manual completa |
| --- | --- | --- | --- |
| Hogar, personas y grupos | Si | Si | Parcial |
| Tareas y asignaciones | Si | Si | Si, con pruebas previas |
| Lista de la compra y fotos | Si | Si | Si, con pruebas previas |
| Productos habituales | Si | Si | Parcial |
| Agenda y lugares | Si | Si | Pendiente de repetir |
| Contactos y categorias | Si | Si | Pendiente de repetir |
| Menu semanal | Si | Si | Pendiente |
| Ajustes y credenciales | Si | No, son locales | Parcial |
| Busqueda global | No, placeholder | No | No aplica |
| Economia | Tarjeta placeholder | No | No aplica |

La aplicacion no debe considerarse totalmente validada hasta repetir en dos
telefonos las pruebas de agenda, contactos y menu semanal despues de las
ultimas correcciones.

## Funcionalidades

### Inicio y ajustes

- Nombre del hogar con limite de 18 caracteres.
- Tarjetas de acceso a las funciones disponibles.
- Ajustes y sincronizacion permanecen disponibles y no se pueden ocultar.
- Personas y grupos configurables.
- Asociacion del dispositivo a una persona.
- Temas de color y modo oscuro disponibles segun la configuracion actual.
- SSID de la red domestica configurable.
- Configuracion de tarjetas resumen.

La identidad del dispositivo se guarda como UUID y se conserva entre reinicios
y actualizaciones. La configuracion local no se copia automaticamente al otro
telefono: incluye credenciales, deviceId, SSID, preferencias y tema.

### Tareas

- Alta, edicion, completado, restauracion y papelera.
- Fecha en formato `dd-MM-yyyy`.
- Rueda de dia, mes y ano.
- Hora con rueda, formato de dos digitos y desplazamiento circular.
- Hora por defecto `23:59`.
- Recordatorio por defecto de 60 minutos.
- Sin repeticion para nuevas tareas.
- Asignacion sin dueno, una persona, varias personas, un grupo o todos.
- Filtro por todas, mis tareas, persona, grupo y sin dueno.
- Registro de quien completo una tarea sin asignar.
- Historial de titulos para sugerencias.
- Notificaciones locales mediante AlarmManager.

Las tareas nuevas siempre se guardan pendientes. Al editar una tarea completa,
se crea una nueva version pendiente de la misma entidad. Las operaciones de
creacion, edicion, completado, restauracion, borrado y archivo se sincronizan.

### Lista de la compra

- Listas y productos pendientes/comprados.
- Alta rapida con nombre y boton de anadir.
- Productos habituales ordenados alfabeticamente.
- Deteccion de duplicados por nombre normalizado.
- Edicion y borrado de productos habituales.
- Imagen desde camara o galeria.
- Previsualizacion antes de guardar.
- Correccion de orientacion EXIF.
- Imagen mostrada con el ancho util de la tarjeta.
- Papelera y borrado logico.
- Sincronizacion de listas, productos, eliminaciones y fotos.
- Sincronizacion del catalogo de productos habituales.

Las eliminaciones se transmiten como operaciones y tombstones. Si una lista se
vacia y se sincroniza correctamente, el otro telefono tambien recibe los
borrados; no se reconstruye comparando solo los elementos existentes.

Las categorias de compra existen localmente, pero no tienen payload propio de
sincronizacion.

### Agenda

- Eventos con titulo, notas, lugar, inicio y fin.
- Fecha y hora mediante ruedas.
- Fin nunca anterior al inicio.
- Eventos de todo el dia.
- Recordatorio en dias antes.
- Lugares guardados y seleccionables desde el formulario.
- Alta automatica de lugares usados en eventos.
- Edicion, papelera, restauracion y borrado permanente.
- Operaciones de eventos y lugares sincronizables.

### Contactos

- Contactos orientados a hogar, seguros, emergencias y servicios.
- Categorias configurables: alta, edicion y borrado.
- Categoria seleccionable mediante menu desplegable.
- Nombre del servicio y telefono principal.
- Llamada mediante aplicacion de telefono o llamada directa con permiso.
- Borrado de un telefono dentro de edicion y con confirmacion.
- Al renombrar una categoria, sus contactos se actualizan en la misma
  transaccion y se generan operaciones para sincronizarlos.
- Contactos y categorias tienen operaciones de sincronizacion.

### Menu semanal

- Entradas por dia de lunes a domingo.
- Franja de comida.
- Titulo y notas.
- Edicion, borrado logico, restauracion y archivado.
- Operaciones de sincronizacion para las entradas.

El menu no genera automaticamente una lista de la compra.

### Busqueda y economia

La busqueda global es actualmente una pantalla placeholder. La tarjeta de
economia tambien es un placeholder y no contiene logica de ingresos o gastos.

## Sincronizacion local

### Modelo

La sincronizacion es offline-first y basada en operaciones, no en copiar tablas.
Cada cambio local:

1. Se escribe en Room.
2. Registra una operacion en la outbox dentro de la misma transaccion.
3. Se conserva pendiente hasta recibir confirmacion.
4. Se reenvia si la conexion se interrumpe.

Las operaciones duplicadas se identifican por `operationId`. Las operaciones
recibidas se aplican y registran atomicamente. El hogar se valida antes de
aplicar cada payload.

### Flujo entre dos telefonos

1. Ambos telefonos se conectan a la misma Wi-Fi permitida.
2. NSD descubre el dispositivo remoto mediante `_casa._tcp`.
3. Un solo telefono inicia la sincronizacion manual o automatica.
4. Se autentica el dispositivo remoto.
5. El cliente envia sus operaciones pendientes.
6. El receptor aplica, confirma y devuelve su historial al cliente.
7. Ambos lados terminan con sus operaciones aplicadas o pendientes de reintento.

No es necesario pulsar sincronizar en los dos telefonos. Si no coinciden en
ese momento, los cambios permanecen en Room y se reintentan al volver a
compartir la Wi-Fi permitida.

### Tipos de datos sincronizados

- Personas y grupos.
- Tareas, asignaciones e historial relevante.
- Listas, productos, fotos y catalogo de habituales.
- Eventos y lugares de agenda.
- Contactos y categorias.
- Entradas del menu semanal.

No se sincronizan como preferencias remotas el tema visual, el SSID, el codigo,
la identidad del dispositivo ni el perfil local de configuracion.

### Conflictos

La resolucion es determinista:

1. Gana la version mayor.
2. Si empatan, gana `updatedAt` mayor.
3. Si siguen empatados, gana el `deviceId` mayor lexicograficamente.

Las eliminaciones logicas se mantienen como tombstones y una operacion antigua
no puede hacer reaparecer un registro nuevo.

### Seguridad

- Handshake con desafio y respuesta HMAC-SHA-256.
- Nonces aleatorios por sesion.
- Mensajes cifrados con AES-GCM.
- Comparacion constant-time de pruebas HMAC.
- Validacion de hogar y dispositivo autorizado.
- Maximo de dos dispositivos activos por hogar.
- Revocacion de dispositivos.
- El SSID filtra la red, pero no autentica.

El codigo de emparejamiento es de seis digitos y no equivale a una identidad
publica ni a una PKI.

## Emparejamiento

El dispositivo que crea el hogar muestra un codigo de seis digitos y un QR con
el payload `CASA|householdId|codigo`. El segundo dispositivo puede introducir
el codigo/payload o leer el QR. Despues se asocia a un perfil ya existente o se
crea uno nuevo.

La politica Wi-Fi requiere estar conectado a la red activa y, cuando Android
permite leerlo, que el SSID coincida con el configurado. Android puede devolver
`<unknown ssid>` si falta permiso o por restricciones del sistema.

Revocar un dispositivo impide reconectarlo con el codigo actual. Regenerar el
codigo es la accion explicita que permite volver a autorizar dispositivos
revocados.

## Copias de seguridad

Los backups contienen el hogar, el codigo y las operaciones disponibles en el
historial local. No son una exportacion completa de preferencias ni un backup
universal de todos los datos futuros.

- `CASA_BACKUP_V1`: se puede leer para mantener compatibilidad.
- `CASA_BACKUP_V2`: formato de escritura actual.
- `V2` usa AES-GCM, salt aleatorio y PBKDF2-HMAC-SHA256 con 120.000 iteraciones.

## Arquitectura

```text
app/src/main/java/com/majyd/casa/
  data/local/       Room, entidades, DAO y migraciones
  data/model/       Modelos de dominio
  data/repository/  Repositorios Room y DataStore
  sync/             Outbox, transporte, conflictos y appliers
  notifications/    Alarmas y restauracion tras reinicio
  ui/               Compose, ViewModels y navegacion
```

Stack principal:

- Kotlin 2.2.20.
- AGP 8.7.3 y Gradle Wrapper 9.3.0.
- Compile/target SDK 35, min SDK 26.
- JVM 17.
- Jetpack Compose y Material 3.
- Room 2.8.4.
- Hilt 2.57.1.
- Coroutines 1.11.0.
- DataStore Preferences.
- WorkManager 2.11.2.
- Kotlin Serialization JSON.
- ZXing para QR.

## Base de datos

`CasaDatabase` esta actualmente en version **24**. Las migraciones estan en
`app/src/main/java/com/majyd/casa/data/local/database/DatabaseMigrations.kt` y
los esquemas exportados en:

```text
app/schemas/com.majyd.casa.data.local.database.CasaDatabase/
```

No se usa `fallbackToDestructiveMigration()`. La migracion 23 a 24 incorpora
`updatedByDevice` en listas de compra, catalogo, personas y grupos para hacer
deterministas los empates de sincronizacion.

## Pruebas y comandos

Desde la raiz del proyecto:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat assembleDebug
.\gradlew.bat verifyStringEncoding
```

Tests instrumentales con dispositivo conectado:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

La prueba E2E de sincronizacion entre dos instalaciones es opcional y requiere
la configuracion indicada en su propio comentario. Sin dispositivos conectados,
la tarea instrumental falla con `No connected devices`; eso no significa que
los tests hayan fallado por codigo.

## Integracion continua

`.github/workflows/android.yml` ejecuta en cada push a `main`/`master` y en cada
pull request:

- Tests unitarios.
- Compilacion de tests instrumentales.
- Verificacion de codificacion y recursos.
- APK debug.

No ejecuta tests fisicos ni publica artefactos automaticamente.

## Limitaciones conocidas y siguientes pasos

- La busqueda global no esta implementada.
- Las categorias de compra no se sincronizan como entidades independientes.
- Ajustes, credenciales, SSID e identidad son locales por diseno.
- Agenda, contactos y menu semanal necesitan repeticion de pruebas manuales
  en dos telefonos.
- Los tests instrumentales compilan, pero no se han ejecutado en este entorno
  por falta de dispositivo conectado.
- Persisten warnings de APIs deprecated y algunos casts no verificados.

El siguiente trabajo previsto es ejecutar el workflow en GitHub y validar en
dos telefonos los modulos menos probados antes de ampliar funcionalidades.
