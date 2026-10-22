# Politica de sincronizacion y tareas

## Sincronizacion local

Cada instalacion genera un `deviceId` UUID permanente. El hogar se identifica
con `householdId`. La comunicacion se realiza por TCP dentro de la red local,
con descubrimiento NSD y mensajes JSON cifrados con AES-GCM.
Si se configura un SSID domestico, los caminos automatico, manual y periodico
solo intentan sincronizar cuando Android puede leer ese SSID y coincide con el
valor guardado. Si el SSID no esta configurado, se mantiene compatibilidad con
instalaciones anteriores; el SSID nunca sustituye al householdId ni a la
autenticacion del dispositivo.

El emparejamiento usa el codigo del hogar para una prueba HMAC de desafio y
respuesta. El codigo no se envia como credencial en claro durante el
intercambio. El registro local limita el hogar a dos dispositivos activos.

Cada operacion contiene `operationId`, `entityId`, `entityType`,
`operationType`, `payload`, `createdAt`, `updatedAt`, `deviceId`, `version`,
`deleted` y su estado de aplicacion. Las operaciones pendientes se conservan
hasta recibir un ACK. El emisor tambien puede enviar el historial completo del
hogar para inicializar un dispositivo recien emparejado. Repetir una operacion
ya registrada devuelve ACK sin volver a mutar la base de datos.

Las operaciones soportadas son `CREATE`, `UPDATE`, `DELETE`, `COMPLETE`,
`RESTORE` y `ARCHIVE`. Las operaciones recibidas se aplican dentro de una
transaccion Room y solo se aceptan si pertenecen al mismo hogar.
Tras una sincronizacion completada se guarda `lastSyncAt` en DataStore y se
muestra en la pantalla de sincronizacion y en la tarjeta correspondiente de
la pantalla principal. Un intento fallido no actualiza ese valor.
Mientras la aplicacion esta abierta, `SyncCoordinator` mantiene el listener y
la sincronizacion reactiva. Como respaldo, WorkManager intenta descubrir y
sincronizar un dispositivo autorizado cada 15 minutos cuando hay red; el
listener TCP no se mantiene indefinidamente en segundo plano.

## Conflictos

La resolucion es determinista: gana la version mayor; si coincide, gana el
`updatedAt` mayor; si vuelve a coincidir, gana el `deviceId` mayor en orden
lexicografico. El borrado es un estado logico y no reactiva un registro por
una modificacion antigua. Restaurar siempre es una operacion explicita que
incrementa la version.

## Tareas

En el primer arranque el asistente configura el nombre del hogar, la persona
asociada al dispositivo, personas adicionales y el SSID domestico opcional.
Las personas se almacenan en Room y no estan limitadas artificialmente a dos;
la asociacion del dispositivo solo personaliza tareas y avisos, no restringe
la consulta de datos.

Una tarea multiusuario, de grupo o para todos sigue siendo una sola entidad.
Las personas y grupos se guardan mediante relaciones Room normalizadas.
Cuando se completa una tarea se guardan `completedAt` y
`completedByPersonId`. La repeticion no forma parte de la interfaz ni del flujo
actual: cada tarea se crea de forma independiente. Los campos legacy de
recurrencia se mantienen en Room y en los payloads para compatibilidad con
instalaciones anteriores, pero no se editan ni generan nuevas apariciones.

La fecha limite es obligatoria en el formulario. Las tareas antiguas sin fecha
se muestran como tales y no generan avisos. Sin hora, el vencimiento se
considera el final del dia local (23:59:59). La fecha se introduce y muestra
en formato `dd-MM-yyyy`.

Las notificaciones son locales y personales: un dispositivo recibe tareas
directas, multiasignadas, de sus grupos y para todos. No recibe tareas sin
asignar ni tareas exclusivas de otra persona. El aviso de vencimiento se
programa una sola vez; no existe repeticion automatica.

## Validacion en dos telefonos

1. En el primer telefono, crear el hogar y conservar el codigo/QR.
2. En el segundo, introducir el codigo o payload y asociar el dispositivo a
   una persona.
   Tambien se puede leer el QR desde la camara; el permiso solo se solicita al usar esa funcion.
3. Con ambos en la misma Wi-Fi, comprobar que aparecen en NSD.
4. Crear, editar, completar, eliminar y restaurar una tarea y un producto en
   cada telefono.
5. Comprobar que el segundo telefono recibe los cambios y que una segunda
   sincronizacion no duplica entidades.
6. Desvincular el segundo dispositivo y comprobar que una nueva conexion es
   rechazada hasta repetir el emparejamiento.

Antes de desvincular un dispositivo, la pantalla obliga a preparar y exportar
el backup cifrado. Incluye `householdId`, el codigo de emparejamiento y el
historial de operaciones sincronizables de los modulos implementados. Al
importarlo, esas operaciones pasan por el motor idempotente existente.

## Limitacion de validacion local

Dos AVD ejecutados en el mismo equipo no equivalen a dos telefonos: pueden
compartir la IP interna del emulador y no enrutar multicast o TCP entre si.
La validacion final de NSD y TCP debe hacerse con dos dispositivos fisicos en
la misma Wi-Fi.

## Diagnostico del listener real

La herramienta `tools/SyncE2eClient.java` permite comprobar el listener TCP de
una instalacion debug sin modificar la aplicacion. Con el APK instalado y la
aplicacion abierta en un emulador, se puede ejecutar:

```text
javac tools\\SyncE2eClient.java
adb -s <serial> forward tcp:45679 tcp:45678
java -cp tools SyncE2eClient 127.0.0.1 45679 <codigo> <deviceId-autorizado>
```

La salida debe mostrar `auth=CASA_AUTH_OK` y las operaciones cifradas de
bootstrap. Un codigo incorrecto debe devolver `CASA_AUTH_FAILED`. Esta prueba
valida el servidor real, HMAC, cifrado AES-GCM, autorizacion y bootstrap, pero
no sustituye la prueba de descubrimiento NSD entre dos telefonos fisicos.
