# Pokémon Stadium Lite

Realizada por: Kevin Steven Posso Sanchez Cod.2080636-2724 y Daniel Rojas Cod.2569189-2724
Mini-aplicación de escritorio desarrollada en **Java Swing** que simula un combate por turnos entre dos Pokémon obtenidos en vivo desde **PokeAPI**. La aplicación permite cargar un Pokémon por nombre, seleccionar uno aleatoriamente, visualizar sus datos principales y ejecutar una batalla con registro de eventos. El proyecto fue desarrollado siguiendo los requisitos del laboratorio de Desarrollo de Software III.

El diseño separa la consulta de la API, el modelo de datos y las reglas del combate. `PokeApiClient` se encarga de realizar las peticiones HTTP y transformar el JSON de PokeAPI en objetos `Pokemon`; `Battle` contiene las reglas del combate y notifica los eventos mediante `BattleListener`; y `PokeApiGUI` controla la interfaz Swing. Para evitar bloquear la interfaz, las consultas de red se ejecutan con `SwingWorker` y los turnos de la batalla se ejecutan mediante `javax.swing.Timer`.

## Requisitos

- **Java 11 o superior**.
- **IntelliJ IDEA** o un entorno compatible con proyectos Java.
- Conexión a Internet para consultar PokeAPI y descargar los sprites.
- Librería **org.json** incluida en `lib/json-20230227.jar`.
- No se utilizan frameworks pesados como Spring o Retrofit.

## Estructura del proyecto

```text
Laboratorio PokeApi/
├── lib/
│   └── json-20230227.jar
├── src/
│   ├── Battle.java
│   ├── BattleListener.java
│   ├── BattleTest.java
│   ├── PokeApiClient.java
│   ├── PokeApiGUI.java
│   ├── Pokemon.java
│   └── assets/
│       ├── logo.png
│       └── pokeball.png
├── DS352.iml
├── .gitignore
└── README.md
```

### Responsabilidad de las clases

- **`Pokemon`**: modelo con nombre, sprite, tipos, HP máximo/actual, Attack, Defense y Speed. También controla el daño y la restauración del HP.
- **`PokeApiClient`**: realiza las consultas al endpoint de PokeAPI, valida la respuesta y convierte el JSON en objetos `Pokemon`.
- **`Battle`**: contiene las reglas del combate por turnos. No depende de Swing.
- **`BattleListener`**: comunica a la interfaz los eventos de la batalla mediante `onTurn`, `onHpChanged` y `onBattleEnded`.
- **`PokeApiGUI`**: construye la interfaz, gestiona Load/Random/Fight!, muestra los datos y conecta la batalla con la interfaz.
- **`BattleTest`**: prueba las reglas principales del combate y sus eventos.

## Cómo ejecutar en IntelliJ IDEA

1. Abrir la carpeta del proyecto en IntelliJ IDEA.
2. Configurar un **JDK 11 o superior**.
3. Usar un nivel de lenguaje estable de Java 11 o superior, sin `Preview`.
4. Agregar `lib/json-20230227.jar` como librería del proyecto si IntelliJ no la reconoce automáticamente.
5. Marcar `src` como carpeta de código fuente si fuera necesario.
6. Ejecutar la clase `PokeApiGUI`.

La aplicación muestra primero una pantalla de bienvenida. El botón **INICIAR** lleva a la interfaz principal de selección y combate.

## Cómo ejecutar desde la terminal

Desde la carpeta raíz del proyecto, en Windows:

```bash
javac -cp lib/json-20230227.jar -d out src/*.java
java -cp "out;lib/json-20230227.jar" PokeApiGUI
```

Para ejecutar las pruebas del combate:

```bash
java -cp "out;lib/json-20230227.jar" BattleTest
```

> La carpeta `out/` es una carpeta de salida generada durante la compilación y no es necesaria para la entrega del código fuente.

## Uso de la aplicación

### Pantalla de bienvenida

Al iniciar el programa se muestra una pantalla de presentación con una estética inspirada en Pokémon y los assets incluidos en el proyecto. El botón **INICIAR** permite entrar a la pantalla principal.

### Selección de Pokémon

Cada lado de la interfaz representa un jugador y tiene sus propios controles:

- **Load**: consulta un Pokémon por nombre mediante PokeAPI.
- **Random**: selecciona aleatoriamente un Pokémon mediante su ID.

Cada ficha muestra:

- Sprite frontal.
- Nombre.
- Tipos.
- HP máximo.
- HP actual.
- Attack.
- Defense.
- Speed.

Las consultas a PokeAPI se realizan de forma asíncrona para que la interfaz no se congele mientras se espera la respuesta.

### Combate

El botón **Fight!** solo está disponible cuando los dos lados tienen un Pokémon cargado correctamente y no existe otra batalla en curso.

Durante la batalla, los controles de selección quedan deshabilitados para impedir que los Pokémon cambien mientras el combate está ejecutándose.

## Reglas del combate

### Orden de turnos

El Pokémon con mayor **Speed** comienza. Si ambos tienen la misma Speed, el primer atacante se selecciona aleatoriamente.

### Daño

Se utiliza una fórmula simple basada en Attack y Defense:

```text
damage = round((Attack / max(1, Defense) * 20 + 2)
               * efectividad
               * variación
               * crítico)
```

El daño mínimo aplicado es **1**.

La variación aleatoria está entre **0.85 y 1.0**.

### Golpe crítico

Existe una probabilidad aproximada del **10 %** de realizar un golpe crítico. Un golpe crítico utiliza un multiplicador de **x1.5**.

### Efectividad de tipos

La efectividad se calcula únicamente usando el **primer tipo** de cada Pokémon y sigue la regla simplificada solicitada por el laboratorio:

| Ataque | Defensor | Modificador |
|---|---|---:|
| Water | Fire | x1.3 |
| Fire | Grass | x1.3 |
| Grass | Water | x1.3 |
| Fire | Water | x0.7 |
| Grass | Fire | x0.7 |
| Water | Grass | x0.7 |
| Otros casos | Otros casos | x1.0 |

El HP nunca puede ser menor que **0**. Cuando un Pokémon llega a 0 HP, la batalla termina y se anuncia el ganador.

## Arquitectura y eventos

El flujo principal de consulta es:

```text
PokeApiGUI
     │
     └── SwingWorker
            │
            └── PokeApiClient
                    │
                    └── PokeAPI (HTTP + JSON)
```

El flujo de la batalla es:

```text
javax.swing.Timer
        │
        └── Battle.ejecutarTurno()
                  │
                  └── BattleListener
                         └── PokeApiGUI
```

`BattleListener` notifica tres eventos principales:

```java
void onTurn(String attacker, String defender, int damage,
            boolean critical, double modifier);

void onHpChanged(String pokemon, int hpActual);

void onBattleEnded(String winner);
```

Durante el combate, estos eventos permiten actualizar el log, el HP mostrado y el estado de la batalla sin acoplar la lógica de `Battle` a Swing.

El caso en que ambos jugadores carguen Pokémon con el mismo nombre también está contemplado: la interfaz identifica la ficha correspondiente al objeto Pokémon que participa en el turno y no depende únicamente del texto del nombre.

## Manejo de errores

La aplicación muestra mensajes visibles cuando ocurre alguna de estas situaciones:

- Nombre vacío.
- Pokémon no encontrado (HTTP 404).
- Error de red o respuesta inesperada de PokeAPI.
- JSON inválido.

Si la descarga del sprite falla, el Pokémon puede cargarse y la ficha muestra que no hay imagen disponible.

## Validaciones y pruebas

El proyecto incluye `BattleTest`, que valida las reglas principales del motor de combate, incluyendo:

- Orden por Speed.
- Desempate aleatorio.
- Un turno por llamada.
- Parámetros de los eventos.
- Actualización coherente del HP.
- Efectividad de tipos.
- Fórmula de daño.
- Golpes críticos.
- Variación del daño.
- Finalización de la batalla.
- Ausencia de turnos después del final.
- Creación de una nueva batalla.
- Validaciones de los combatientes.

Resultado de las pruebas del motor de combate: **51/51**.

También se realizaron pruebas de integración de la interfaz y del Timer, incluyendo el caso de dos Pokémon con el mismo nombre y el bloqueo de controles durante la batalla: **44/44**.

## Limitaciones conocidas

- La consulta depende de la disponibilidad de PokeAPI y de una conexión a Internet.
- Los Pokémon aleatorios utilizan IDs entre **1 y 1025**.
- Los nombres deben utilizar el formato reconocido por PokeAPI, incluyendo los nombres de formas que utilizan guiones.
- La efectividad implementada es la versión simplificada solicitada por el laboratorio y usa únicamente el primer tipo.
- No se implementan todas las reglas del sistema de combate oficial de Pokémon.

## Capturas de pantalla

El enunciado del laboratorio solicita incluir capturas de pantalla en el README. Para la entrega final se deben agregar las capturas reales de la aplicación ejecutada, por ejemplo:

1. **Pantalla de bienvenida** con el botón `INICIAR`.
2. **Pantalla principal** con dos Pokémon cargados.
3. **Combate en curso** mostrando el log y el HP actualizado.
4. **Final de combate** mostrando el ganador en el log.

Ejemplo de estructura para agregarlas al repositorio:

```text
screenshots/
├── bienvenida.png
├── seleccion.png
├── combate.png
└── ganador.png
```

Y luego referenciarlas desde esta sección con Markdown, por ejemplo:

```md
![Pantalla de bienvenida](screenshots/bienvenida.png)
![Selección de Pokémon](screenshots/seleccion.png)
![Combate](screenshots/combate.png)
![Fin de combate](screenshots/ganador.png)
```

## Entrega

El proyecto se entrega como proyecto IntelliJ con el código fuente, la librería `org.json`, los assets utilizados por la interfaz y este README con las instrucciones de ejecución, explicación del diseño, reglas del combate y evidencias de la aplicación.
