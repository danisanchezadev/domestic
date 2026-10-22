# Casa

Aplicacion Android privada para organizar un hogar entre dos dispositivos,
sin servidor central ni cuenta en la nube. Cada instalacion mantiene una copia
local de los datos y las copias se sincronizan directamente por la red local.

Este documento describe el estado real del proyecto despues de aplicar el
ultimo prompt de desarrollo. No presenta como terminadas las funcionalidades
que siguen siendo pantallas placeholder.

## Estado actual

La aplicacion es funcional en estos bloques:

- Configuracion inicial del hogar, personas, dispositivo y red Wi-Fi.
- Inicio con resumen dinamico de los modulos implementados.
- Lista de la compra.
- Tareas domesticas.
- Agenda local.
- Menu semanal basico.
- Sincronizacion local entre dispositivos autorizados, con intercambio
  bidireccional iniciado desde un solo dispositivo.
- Emparejamiento mediante codigo, payload y QR.
- Notificaciones locales de tareas.
- Ajustes, busqueda global sobre los datos disponibles y pantalla de estado de sincronizacion.
- Migraciones Room, pruebas unitarias, pruebas instrumentales y prueba E2E entre dos instalaciones.

Siguen sin implementarse como modulos funcionales completos:

- Planes y experiencias.
- Viajes.
- Electrodomesticos y mantenimiento.
- Contactos importantes.
- Papelera unificada entre todos los modulos.
- Copia de seguridad completa de todos los modulos previstos.
- Busqueda global completa sobre esos modulos futuros.

En la compra, las listas, productos e imagenes se sincronizan. El catalogo de
productos habituales tambien tiene ya operaciones propias de sincronizacion.
Las categorias de compra, la agenda y el menu semanal siguen siendo locales en
esta version.

Esos destinos existen en la navegacion como placeholders para reservar la
estructura, pero no contienen datos simulados permanentes ni deben considerarse
entregados.

### Cobertura actual por dominio

| Dominio | Funcional en local | Sincronizado entre dispositivos |
|---|---|---|
| Hogar, personas y grupos | Si | Si |
| Tareas, asignaciones, historial y papelera | Si | Si |
| Lista de la compra, productos e imagenes | Si | Si |
| Productos habituales | Si | Si, mediante `shopping_catalog` |
| Categorias de compra | Si | No |
| Agenda | Si | No |
| Menu semanal | Si, version basica | No |
| Ajustes, identidad y credenciales | Si | No, permanecen locales por seguridad |
| Modulos futuros | No, placeholder | No |

La columna de sincronizacion se refiere a datos de dominio. El emparejamiento,
la identidad del dispositivo, el codigo, el SSID configurado y los permisos
son configuracion local y no se copian como si fueran datos de la casa.

## Objetivo tecnico

El diseño sigue estas reglas:

- Offline-first: la lectura y escritura normal no depende de Internet.
- Datos locales por dispositivo mediante Room.
- Identidades estables mediante UUID.
- Sincronizacion por operaciones, no por copiar tablas completas.
- Operaciones idempotentes y protegidas frente a duplicados.
- Borrado logico para que una eliminacion tambien pueda viajar al otro dispositivo.
- Resolucion determinista de conflictos.
- Maximo de dos dispositivos autorizados por hogar.
- Numero de personas y grupos sin limite artificial.
- Codigo y mensajes visibles para el usuario en espanol.
- Sin datos demo en la aplicacion final.

## Arquitectura

El proyecto es un unico modulo Android llamado app.

~~~text
app/src/main/java/com/majyd/casa/
  data/
    local/       Entidades Room, DAO, base de datos y migraciones
    model/       Modelos de dominio y estados de pantalla
    repository/  Interfaces y repositorios Room/DataStore
  sync/          Motor de sincronizacion, operaciones, conflictos y aplicadores
  notifications/Alarmas, receiver de arranque y reglas de notificacion
  ui/            Compose, ViewModels, navegacion y pantallas
  util/          Normalizacion compartida
~~~

La interfaz usa Jetpack Compose y Material 3. Los ViewModel exponen estados
reactivos mediante StateFlow/Flow; las pantallas no consultan Room directamente.
Hilt construye la base de datos, DAO, repositorios, motor de sincronizacion,
transporte y planificador de notificaciones.

### Stack

- Kotlin 2.2.20.
- Android Gradle Plugin 8.7.3.
- Gradle Wrapper.
- Compile/target SDK 35.
- Min SDK 26.
- Java/Kotlin JVM target 17.
- Jetpack Compose y Material 3.
- Room 2.8.4.
- Hilt 2.57.1.
- Kotlin Coroutines 1.11.0.
- DataStore Preferences.
- WorkManager 2.11.2.
- Kotlin Serialization JSON.
- ZXing para generacion y lectura de QR.

## Arranque y configuracion inicial

En una instalacion nueva, AppBootstrapViewModel detecta que no existe la
identidad local y muestra el asistente inicial. El asistente permite:

1. Introducir el nombre del hogar.
2. Crear la primera persona.
3. Asociar esa persona al dispositivo actual.
4. Crear personas adicionales.
5. Configurar opcionalmente el SSID domestico.
6. Finalizar y entrar en Inicio.

El SSID es un filtro adicional para los intentos automaticos, manuales y
periodicos. No identifica el hogar, no sustituye al householdId y no es un
mecanismo de autenticacion.

El dispositivo recibe un deviceId UUID permanente. El UUID se conserva en Room
y se reutiliza tras reinicios o actualizaciones de la aplicacion.

## Funcionalidades implementadas

### Inicio

La pantalla de Inicio observa el perfil del hogar y los contadores disponibles.
Muestra tarjetas navegables para compra, tareas, agenda, menu, sincronizacion,
ajustes, busqueda y los destinos futuros. Tambien muestra el estado de la
ultima sincronizacion correcta.

No se insertan tareas, productos ni eventos de ejemplo al iniciar.

### Personas y grupos

Las personas pertenecen a un hogar y se almacenan en Room. Los grupos tienen
miembros mediante una tabla de relacion. El dispositivo puede asociarse a una
persona para decidir que avisos personales debe recibir.

La asociacion del dispositivo no limita el numero de personas ni la lectura de
datos del hogar.

### Lista de la compra

El modulo conserva y amplia la funcionalidad de compra existente:

- Varias listas.
- Categorias.
- Productos pendientes y comprados.
- Alta, edicion, marcado como comprado y borrado logico.
- Catalogo historico reutilizable.
- Productos habituales ordenados alfabeticamente.
- Alta manual de habituales, edicion, eliminacion logica y restauracion de
  entradas antiguas.
- Un habitual puede pulsarse para anadirlo a la lista semanal; si ya existe,
  no se duplica y se muestra en un verde mas palido.
- Las imagenes se pueden seleccionar desde galeria o camara, se previsualizan
  antes de anadir y se muestran ocupando el ancho util de la tarjeta.
- La orientacion EXIF de las fotos se corrige antes de guardarlas para evitar
  que las fotos verticales aparezcan tumbadas.
- Nombre normalizado para busqueda y deduplicacion.
- Cantidad y unidad.
- Flujos reactivos desde Room.

La normalizacion ignora diferencias de mayusculas, tildes y espacios para
comparar nombres. Los identificadores de lista, producto y catalogo son
estables y se incluyen en las operaciones sincronizables. Las categorias tienen
identificadores estables en Room, pero todavia no tienen payload propio de
sincronizacion.

### Tareas

La pantalla de tareas tiene un formulario intermedio y una lista reactiva con
contadores. Una tarea incluye:

- Titulo y notas.
- Fecha limite obligatoria para tareas nuevas, introducida y mostrada como dd-MM-yyyy.
- Hora limite opcional.
- Aviso opcional antes del vencimiento.
- Estado completada/no completada.
- Fecha y persona que la completo.
- Borrado logico.
- Version, fechas y dispositivos de creacion/actualizacion.

Las asignaciones se normalizan en relaciones Room y soportan:

- Sin asignar.
- Una persona.
- Varias personas.
- Un grupo.
- Todas las personas.

La lista muestra los nombres derivados de esas relaciones. Los datos legacy de
asignacion se conservan como fallback de lectura cuando corresponde.

Las tareas no tienen repeticion: cada alta es independiente y debe introducirse
de nuevo cuando corresponda. Las columnas de recurrencia antiguas se conservan
solo para compatibilidad con bases de datos y payloads previos, pero ya no se
editan ni generan nuevas tareas.

Las tareas antiguas sin fecha se pueden leer, pero no generan avisos. Cuando no
hay hora, el vencimiento se interpreta como el final del dia local.

### Agenda

La agenda local permite gestionar eventos con:

- Titulo, notas y ubicacion.
- Inicio y fin.
- Eventos de dia completo.
- Aviso previo opcional.
- Edicion y borrado logico.
- Ordenacion temporal.

La vista de calendario y el repositorio mantienen los eventos en Room. La
agenda todavia no genera operaciones de sincronizacion, por lo que sus cambios
son locales al dispositivo.

### Menu semanal

El modulo implementado es deliberadamente basico:

- Vista semanal de lunes a domingo.
- Entradas por dia.
- Franja de comida: desayuno, comida, cena y otras franjas modeladas.
- Titulo y notas.
- Alta, edicion y borrado logico.

El catalogo culinario avanzado, ingredientes y generacion de compra desde el
menu permanecen fuera de esta iteracion.

Las entradas del menu semanal tambien son locales por ahora; el modelo de
datos existe, pero no hay payload ni aplicador de sincronizacion para ellas.

### Notificaciones

Las tareas se programan mediante AlarmManager y notificaciones locales.

- Se programa como maximo un aviso por vencimiento.
- Se recalcula al editar una tarea.
- BootReceiver restaura la programacion tras reinicio o actualizacion.
- La politica es personal: el dispositivo recibe tareas directas, de varias
  personas, de sus grupos y para todos.
- No recibe tareas sin asignar ni tareas exclusivas de otra persona.
- POST_NOTIFICATIONS se declara para versiones que lo requieren.

WorkManager tambien ejecuta un trabajo periodico de sincronizacion cuando hay
red disponible. El listener TCP no se mantiene indefinidamente en segundo
plano.

## Sincronizacion local

### Componentes

- SyncOperationEntity y SyncOperationDao: cola e historial de operaciones.
- RoomSyncOperationRepository: persistencia de operaciones pendientes y ACK.
- DefaultSyncEngine: coordinacion de envio y recepcion.
- SyncCoordinator: integracion con ciclo de vida, descubrimiento y autorizacion.
- HouseholdSyncApplier: valida el hogar y enruta payloads.
- ShoppingSyncApplier: aplica listas, productos y catalogo de habituales.
- TaskSyncApplier: aplica tareas, historial y asignaciones.
- SyncSession: abre una sesion de transporte y resume estadisticas.
- SyncWorker: intento periodico con WorkManager.

### Estado observable del intento manual

`SyncViewModel` expone `SyncUiState` mediante `StateFlow`. El estado distingue
`IDLE`, `DISCOVERING`, `CONNECTING`, `AUTHENTICATING`, `SYNCHRONIZING`,
`SUCCESS`, `NO_CHANGES` y `FAILED`. Compose solo consume ese estado; no inicia
red ni consulta Room directamente.

El resultado manual conserva en memoria el inicio, fin, duracion aproximada,
dispositivo remoto, enviados, confirmados, recibidos y aplicados. La hora de
la ultima sincronizacion manual correcta se guarda en preferencias separada de
la hora de cualquier sincronizacion automatica. Un estado activo no se
persiste, por lo que un reinicio no puede mostrar una sesion fantasma.

Los errores se representan con `SyncError` y se traducen a mensajes seguros:
red no disponible, dispositivo no encontrado, descubrimiento fallido, timeout
de conexion o lectura, autorizacion, credenciales, hogar incompatible,
protocolo, transporte, aplicacion en Room, cancelacion y error desconocido.
La causa tecnica se conserva como causa de la excepcion para diagnostico, pero
no se muestra al usuario.

### Modelo de operacion

Cada operacion contiene:

~~~text
operationId
householdId
entityType
entityId
operationType
payload
version
createdAt
updatedAt
deviceId
deleted
applied
~~~

Los tipos de operacion soportados son CREATE, UPDATE, DELETE, COMPLETE,
RESTORE y ARCHIVE. Para compra se usan actualmente operaciones de listas,
productos y catalogo de habituales.

Las operaciones pendientes se conservan hasta recibir confirmacion. Una
operacion repetida se reconoce por operationId, devuelve ACK y no vuelve a
mutar los datos.

Las operaciones recibidas se aplican dentro de transacciones Room. Antes de
aplicar un payload se valida que pertenece al mismo hogar. Las eliminaciones
son estados logicos y no se reactivan por una modificacion antigua.

El flujo normal es offline-first: una escritura se aplica primero en la copia
local y genera una operacion en la outbox. Cuando el otro movil esta visible,
una sola sesion intercambia datos en las dos direcciones. El cliente envia sus
operaciones pendientes; el servidor las aplica y devuelve ACK; despues el
servidor envia su historial al cliente. No es necesario pulsar el boton en los
dos moviles.

Si los moviles no coinciden en ese momento, las operaciones quedan pendientes
en Room y se reintentan cuando vuelven a compartir la red Wi-Fi permitida. La
sincronizacion es eventual, no instantanea ni dependiente de Internet. El
catalogo de habituales sigue este mismo modelo desde que se incorporo como
entityType `shopping_catalog`.

Una eliminacion no se interpreta como ausencia de un registro. Se guarda como
tombstone y su operacion viaja al otro dispositivo. Por eso, si un movil vacia
la lista de la compra y sincroniza correctamente, el otro movil recibe las
acciones de borrado y tambien queda vacio; no se vuelve a rellenar comparando
los elementos que sigan existiendo en una de las copias.

### Conflictos

La resolucion es determinista y no depende del orden de llegada:

1. Gana la version mayor.
2. Si empatan, gana updatedAt mayor.
3. Si vuelven a empatar, gana el deviceId mayor en orden lexicografico.

Restaurar es una operacion explicita que incrementa la version. Esto impide
que una escritura antigua haga reaparecer accidentalmente un registro borrado.

### Transporte TCP

El intercambio de texto usa lineas delimitadas. El protocolo interno es:

~~~text
Cliente -> CASA_HELLO:<deviceId>:<clientNonce>
Servidor -> CASA_CHALLENGE:<serverNonce>
Cliente -> CASA_RESPONSE:<HMAC(clientNonce + serverNonce)>
Servidor -> CASA_AUTH_OK o CASA_AUTH_FAILED
Ambos lados intercambian mensajes AES-GCM
Fin -> __CASA_SYNC_END__
~~~

Hay limites de tiempo de conexion, lectura y aceptacion, ademas de un limite
maximo por linea. Los mensajes JSON se cifran antes de enviarse.

### Descubrimiento NSD

NsdDeviceDiscovery anuncia el servicio _casa._tcp. y publica:

- Nombre del servicio.
- Puerto TCP.
- householdId como atributo de descubrimiento.
- deviceId como atributo de descubrimiento.

La busqueda resuelve los servicios encontrados y expone una lista reactiva de
dispositivos cercanos. Se usa un WifiManager.MulticastLock durante el
descubrimiento/anuncio y se libera al terminar.

El descubrimiento automatico permanece activo desde el arranque del hogar.
La actividad principal no inicia una segunda busqueda al reanudarse, porque
dos descubrimientos NSD simultaneos provocaban esperas y fallos intermitentes.
`SyncCoordinator` mantiene una unica busqueda activa y publica los dispositivos
por `StateFlow`; la pantalla de sincronizacion, la accion manual y WorkManager
reutilizan esa fuente. Las sesiones manuales y automaticas pasan por el mismo
mutex, por lo que no pueden intercambiar datos simultaneamente.

El filtro de hogar y la autorizacion real se vuelven a comprobar durante la
conexion; NSD por si solo nunca concede acceso.

### Seguridad actual

- El codigo de emparejamiento no se envia como credencial en claro durante el intercambio.
- La clave de sesion se deriva mediante SHA-256 del codigo.
- La autenticacion del desafio usa HMAC-SHA-256.
- Los mensajes se cifran con AES-GCM y nonce aleatorio.
- Las comparaciones de pruebas HMAC son constant-time.
- Solo se aceptan dispositivos autorizados del mismo hogar.
- La politica limita a dos dispositivos activos por hogar.
- Se pueden revocar dispositivos y regenerar el codigo.

La seguridad actual protege el transporte local y evita que un tercero sin el
codigo se conecte accidentalmente. No equivale a un sistema de identidad
publica ni a una PKI. Las mejoras recomendadas aparecen al final.

## Emparejamiento

En el dispositivo principal:

1. Abrir Sincronizacion.
2. Conservar el codigo de seis digitos o mostrar el QR.
3. Mantener abierta la aplicacion mientras se realiza la primera conexion.

En el segundo dispositivo:

1. Completar su configuracion local.
2. Introducir el payload/codigo del primer dispositivo o escanear el QR.
3. Asociar el dispositivo a una persona.
4. Estar en la misma red Wi-Fi.
5. Seleccionar el dispositivo descubierto y sincronizar.

La camara solo se usa al escanear un QR y el permiso se solicita de forma
contextual. Revocar un dispositivo obliga a repetir el emparejamiento.

## Backup actual

La pantalla de sincronizacion permite preparar un backup cifrado de
credenciales y operaciones sincronizables implementadas. El formato usa
AES-GCM y el codigo de emparejamiento como material de derivacion.

Actualmente contiene householdId, codigo y operaciones disponibles, incluyendo
listas, productos y catalogo de habituales, no una
exportacion completa de todos los dominios futuros. Todavia no existe el flujo
completo de fusion/reemplazo de toda la base de datos con resumen previo y
deduplicacion entre todos los modulos solicitados originalmente.

## Navegacion actual

Destinos funcionales:

- Inicio.
- Compra.
- Tareas.
- Agenda.
- Menu.
- Sincronizacion.
- Ajustes.
- Busqueda.

Destinos reservados como placeholder:

- Planes.
- Viajes.
- Hogar/electrodomesticos.
- Contactos.
- Papelera global.

La navegacion conserva esos destinos para no tener que rediseñar el grafo al
implementar las fases futuras.

## Base de datos y migraciones

CasaDatabase usa Room version 17 y exporta los esquemas en:

~~~text
app/schemas/com.majyd.casa.data.local.database.CasaDatabase/
~~~

Entidades principales:

- Identidad del dispositivo y perfil del hogar.
- Personas, grupos y miembros de grupo.
- Listas, categorias, productos y catalogo de compra.
- Tareas, historial y relaciones de asignacion.
- Eventos de agenda.
- Entradas de menu semanal.
- Operaciones de sincronizacion.
- Dispositivos autorizados.

Las migraciones cubren de la version 1 a la 17. Las ultimas ampliaciones
incorporan operaciones, dispositivos autorizados, historial de tareas,
imagenes de compra y campos de compatibilidad de versiones anteriores.

No se usa fallbackToDestructiveMigration; las actualizaciones deben pasar por
migraciones declaradas y probadas.

## Permisos Android

Declarados en app/src/main/AndroidManifest.xml:

| Permiso | Motivo |
| --- | --- |
| INTERNET | Abrir el socket TCP local. No implica uso de nube. |
| ACCESS_NETWORK_STATE | Comprobar disponibilidad de red para sincronizacion. |
| ACCESS_WIFI_STATE | Consultar estado y SSID cuando Android lo permite. |
| ACCESS_COARSE_LOCATION | Se declara junto a FINE para cumplir el modelo de permisos de Android 12+. |
| ACCESS_FINE_LOCATION | Requisito de algunas versiones de Android para leer SSID/descubrimiento Wi-Fi. |
| CHANGE_WIFI_MULTICAST_STATE | Permitir multicast NSD en la red local. |
| CAMERA | Escanear el QR de emparejamiento; se usa solo bajo accion del usuario. |
| POST_NOTIFICATIONS | Mostrar avisos locales en Android moderno. |
| RECEIVE_BOOT_COMPLETED | Restaurar alarmas tras reinicio o actualizacion. |

android.hardware.camera es opcional: el dispositivo puede usar codigo o
payload sin camara.

La aplicacion no declara acceso a contactos del telefono, almacenamiento
general, llamadas, correo ni cuentas externas.

## Pruebas

### Unitarias

Las pruebas unitarias cubren:

- Normalizacion de nombres.
- Preferencias y codigo de emparejamiento.
- Criptografia AES-GCM/HMAC.
- Payload y QR de emparejamiento.
- Politica de notificaciones.
- Resolucion determinista de conflictos.
- Sesion de sincronizacion y ACK.
- Transporte TCP.
- Codificacion/decodificacion del backup.

### Instrumentales

Las pruebas Android cubren:

- DAO de dispositivos.
- Migraciones Room de versiones antiguas a la 15.
- Repositorios de compra, hogar, calendario y tareas.
- Asignaciones PERSON, MULTIPLE_PEOPLE, GROUP, EVERYONE y NONE.
- Aplicacion de datos en Room.
- Relaciones normalizadas de tareas.

### E2E entre instalaciones

Existen dos pruebas opcionales:

- RealLocalWifiSyncSeederTest crea una operacion real en la app servidor y
  limpia registros autorizados obsoletos solo dentro del fixture de prueba.
- RealLocalWifiSyncE2eTest ejecuta el cliente real contra el listener real,
  recibe y aplica una operacion.

La prueba E2E se ejecuto con dos AVD y termino con OK (1 test). Esto valida el
listener real, HMAC, AES-GCM, autorizacion, ACK y aplicacion de la operacion.

La suite instrumental normal se ejecuto en ambos AVD y completo 19 pruebas por
dispositivo. Las dos pruebas E2E se omiten en la suite normal si no se pasan
sus flags explicitos.

## Comandos de desarrollo

Desde la raiz del proyecto en Windows PowerShell:

~~~powershell
.\\gradlew.bat assembleDebug
.\\gradlew.bat testDebugUnitTest
.\\gradlew.bat lintDebug
.\\gradlew.bat assembleDebugAndroidTest
.\\gradlew.bat connectedDebugAndroidTest
.\\gradlew.bat verifyStringEncoding
~~~

La validacion completa usada para la entrega fue:

~~~powershell
.\\gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest verifyStringEncoding connectedDebugAndroidTest --no-parallel --no-problems-report --console=plain
~~~

El resultado fue BUILD SUCCESSFUL.

El APK de debug se genera en:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

El APK de pruebas se genera en:

~~~text
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
~~~

Instalacion manual:

~~~powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\\Sdk\\platform-tools\\adb.exe'
& $adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk
~~~

La herramienta auxiliar tools/SyncE2eClient.java permite probar el listener TCP
desde un proceso Java separado en un entorno debug:

~~~powershell
javac tools\\SyncE2eClient.java
& $adb -s <serial> forward tcp:45679 tcp:45678
java -cp tools SyncE2eClient 127.0.0.1 45679 <codigo> <deviceId-autorizado>
~~~

La salida esperada para credenciales validas incluye CASA_AUTH_OK. El codigo
incorrecto debe ser rechazado.

## Control de encoding y strings

La tarea Gradle verifyStringEncoding se incorporo para que cualquier cambio
futuro compruebe automaticamente:

- Todos los ficheros de res, main, test, androidTest y docs como UTF-8.
- Ausencia de secuencias conocidas de mojibake producidas por una doble decodificacion UTF-8.
- Parseo XML de los recursos Android.

Ademas de ejecutar esa tarea, antes de cerrar esta fase se revisaron los
mensajes de produccion y se corrigieron textos sin tildes o mal codificados en
Inicio, Sincronizacion, tareas, calendario, compra, preferencias, transporte,
criptografia y pruebas.

## Cambios realizados desde el ultimo prompt

El trabajo no rehizo el proyecto desde cero. Se inspecciono la base existente y
se continuo sobre ella. Los cambios principales fueron:

1. Se consolido el asistente de primera instalacion y la identidad estable del dispositivo.
2. Se incorporaron hogar, personas, grupos y dispositivos autorizados.
3. Se anadieron asignaciones normalizadas para tareas y soporte de todos los tipos pedidos.
4. Se hizo obligatoria la fecha limite de las tareas nuevas y se añadieron historial, avisos, completado por persona y papelera logica; la recurrencia quedo desactivada.
5. Se integro la agenda local y el menu semanal basico sin insertar datos demo.
6. Se construyo el modelo de operaciones, cola, ACK, aplicadores, conflictos, idempotencia y sincronizacion reactiva/periodica.
7. Se incorporo descubrimiento NSD, TCP local, HMAC, AES-GCM, pairing por codigo/QR, revocacion y limite de dos dispositivos.
8. Se anadieron backups cifrados de credenciales y operaciones disponibles.
9. Se agregaron pruebas de Room, repositorios, migraciones, transporte, criptografia y una prueba E2E de dos instalaciones reales.
10. Se corrigio la visualizacion de asignaciones usando las relaciones normalizadas en vez del campo legacy.
11. Se corrigieron textos con acentos, se elimino diagnostico temporal y se valido el encoding de todo el codigo relevante.
12. Se corrigio el contexto de aplicacion usado por las pruebas E2E para que accedieran a la base de datos y preferencias reales de cada instalacion.
13. Se limpio el fixture E2E para retirar dispositivos autorizados obsoletos sin debilitar la politica de produccion de maximo dos dispositivos.
14. Se implemento el catalogo de productos habituales con altas, ediciones,
    eliminaciones, restauraciones, deduplicacion y sincronizacion entre
    dispositivos.
15. Se corrigio la reactivacion de registros antiguos eliminados que podian
    bloquear nombres como Leche o Leche entera por el indice unico de Room.
16. Se corrigio la orientacion EXIF y la captura de camara a resolucion
    completa, incluyendo previsualizacion y mantenimiento del formulario al
    girar el dispositivo.
17. Se elimino la busqueda NSD duplicada al abrir la actividad para reducir
    esperas y sincronizaciones intermitentes.

## Limitaciones conocidas

- NSD multicast no se ha validado de forma exhaustiva con dos telefonos
  fisicos; la prueba E2E realizada usa dos AVD con conexion TCP reenviada.
- Los AVD no demuestran por si solos que todos los routers Wi-Fi gestionen multicast, aislamiento de clientes o resolucion mDNS correctamente.
- El listener TCP no permanece activo indefinidamente cuando la aplicacion esta en segundo plano.
- El codigo de emparejamiento se guarda actualmente en preferencias de la app; no hay almacenamiento dedicado en Android Keystore.
- La sincronizacion de categorias de compra, agenda y menu semanal todavia no
  esta implementada.
- El backup disponible no es todavia una exportacion completa de todos los
  modulos previstos ni ofrece fusion/reemplazo global.
- Los modulos futuros muestran placeholder y no contienen logica de negocio.
- No hay backend de notificaciones push; los avisos son locales.
- El protocolo usa un framing sencillo por lineas y no tiene negociacion formal de version entre aplicaciones.
- El filtrado por SSID depende de las restricciones y permisos de cada version de Android y puede no devolver el nombre de red.
- La interfaz contiene aun algunos textos escritos directamente en Compose en vez de centralizados en strings.xml.

## Mejoras tecnicas recomendadas

Estas mejoras no cambian la logica de negocio, pero aumentarian la robustez,
seguridad y mantenibilidad.

### Prioridad alta

1. **Keystore para secretos**. Guardar una clave aleatoria en Android Keystore y
   usarla para proteger el codigo/token local. El codigo podria seguir siendo el
   secreto que conoce el usuario, pero no deberia ser el unico material
   persistido en claro dentro de DataStore.
2. **Versionar el protocolo**. Anadir version de protocolo, capacidades y
   motivos de incompatibilidad al handshake para que una futura version no
   falle con un mensaje generico de handshake.
3. **Autenticacion con contexto de hogar**. Incluir householdId y un
   identificador de sesion en la prueba HMAC para reducir riesgo de reutilizar
   mensajes entre hogares.
4. **Proteccion anti-replay**. Registrar nonces/challenges recientes o usar una
   ventana de sesion y expiracion de credenciales. AES-GCM autentica el mensaje,
   pero no sustituye por si solo a una politica anti-repeticion de sesion.
5. **Rate limiting**. Limitar intentos de pairing por dispositivo y aplicar
   backoff al recibir credenciales incorrectas.
6. **Prueba fisica documentada**. Crear una matriz con dos telefonos Android
   reales: misma Wi-Fi, aislamiento de clientes activo, cambio de red, perdida
   de multicast, reconexion y revocacion.

### Prioridad media

1. **Framing binario o length-prefix**. Sustituir el protocolo por lineas por
   mensajes con longitud y version.
2. **Ciclo de vida NSD mas robusto**. Anadir reintentos con backoff, estados
   observables de registro/resolucion y manejo explicito de callbacks tardios.
3. **Cancelacion y cierre uniforme**. Centralizar la liberacion de sockets,
   listener y multicast lock en una unica capa de sesion con CoroutineScope
   controlado por ciclo de vida.
4. **Sincronizacion incremental real**. Enviar solo operaciones pendientes o
   desde un cursor remoto, compactar operaciones antiguas y conservar un
   historial minimo para bootstrap.
5. **Outbox transaccional completa**. Asegurar que cada mutacion de dominio y su
   operacion de sync se escriban en una unica transaccion Room.
6. **Reloj inyectable**. Inyectar Clock y generadores de UUID/nonce en casos de
   uso para que conflictos y notificaciones sean deterministas en
   pruebas.
7. **Errores tipados**. Sustituir mensajes genericos y runCatching amplios por
   errores diferenciados de red, timeout, hogar, autorizacion y payload.
8. **Strings centralizados**. Mover textos Compose y errores visibles a
   res/values/strings.xml, usar plurales y mantener verifyStringEncoding en CI.
9. **CI reproducible**. Ejecutar en cada cambio encoding, lint, unit tests,
   compilacion y tests instrumentales en un emulador limpio.
10. **Calidad estatica**. Anadir Ktlint/Detekt, comprobacion de dependencias
    vulnerables y un limite de warnings permitido.

### Prioridad operativa

1. Añadir diagnostico local opt-in sin payloads, codigos ni UUIDs completos.
2. Añadir una pantalla de diagnostico exportable con versiones, permisos, red,
   ultimo error y contadores no sensibles.
3. Mostrar una razon concreta cuando Android no permite leer el SSID o NSD esta
   bloqueado por el router.
4. Definir retencion para operaciones aplicadas, historial y backups temporales.
5. Verificar alarmas en cambios de zona horaria, DST y restricciones de bateria.

### Trabajo de producto pendiente

1. Sincronizar categorias de compra, eventos de agenda y entradas del menu
   semanal con payloads, aplicadores, migraciones si fueran necesarias y
   pruebas de conflicto.
2. Definir si las preferencias de cada dispositivo deben seguir siendo locales
   o si algunas preferencias del hogar necesitan un modelo sincronizable.
3. Revisar el bootstrap del catalogo: las entradas habituales antiguas creadas
   antes de existir `shopping_catalog` no tienen necesariamente una operacion
   historica que permita exportarlas retroactivamente.
4. Completar papelera, backup y busqueda global cuando se implementen los
   modulos que ahora son placeholders.

## Convenciones para continuar

- No añadir datos de prueba dentro de la app de produccion.
- Cada entidad sincronizable debe tener identificador estable, version,
  timestamps, dispositivo de origen y borrado logico cuando aplique.
- Cada nueva migracion Room debe incluir esquema exportado y prueba de
  MigrationTestHelper.
- Cada cambio visible debe revisar strings.xml, encoding UTF-8 y textos con tildes.
- Toda nueva operacion de sincronizacion debe ser idempotente y probarse dos
  veces, incluido el caso de recepcion simultanea.
- No hacer consultas Room ni trabajo de red en el hilo principal.
- Mantener la politica de dos dispositivos autorizados; no eliminarla para
  simplificar pruebas.
- No introducir servicios cloud, cuentas ni permisos no justificados sin una
  decision tecnica documentada.
- Antes de entregar cambios ejecutar compilacion, lint, unit tests,
  instrumentales y verifyStringEncoding.

## Siguiente trabajo tecnico recomendado

La prioridad inmediata no deberia ser otra refactorizacion de UI. Primero hay
que validar NSD y sincronizacion en dos telefonos fisicos, especialmente con
la aplicacion en segundo plano. En paralelo, conviene completar la cobertura
de datos pendientes (categorias, agenda y menu), endurecer el canal local con
Keystore, version de protocolo, anti-replay y reintentos NSD, y automatizar
estas comprobaciones en CI.
