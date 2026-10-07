import java.util.Objects;
import java.util.Random;

/**
 * Reglas de un combate por turnos entre dos Pokemon.
 * No depende de la interfaz grafica: avisa de lo que ocurre mediante un BattleListener.
 * Cada llamada a ejecutarTurno() ejecuta exactamente un ataque, para que quien la use
 * (por ejemplo un temporizador) decida el ritmo del combate.
 */
public class Battle
{
    private static final double PROBABILIDAD_CRITICO = 0.10;
    private static final double MULTIPLICADOR_CRITICO = 1.5;

    private static final double EFECTIVIDAD_VENTAJA = 1.3;
    private static final double EFECTIVIDAD_DESVENTAJA = 0.7;
    private static final double EFECTIVIDAD_NEUTRA = 1.0;

    //variacion aleatoria del dano: un factor entre 0.85 y 1.0
    private static final double VARIACION_MINIMA = 0.85;
    private static final double VARIACION_MAXIMA = 1.0;

    private final Pokemon pokemon1;
    private final Pokemon pokemon2;
    private final BattleListener listener;
    private final Random random;

    private Pokemon atacanteActual;
    private boolean terminada = false;
    private Pokemon ganador = null;

    public Battle(Pokemon pokemon1, Pokemon pokemon2, BattleListener listener)
    {
        this(pokemon1, pokemon2, listener, new Random());
    }

    /**
     * Crea una batalla NUEVA. Al crearla, los dos Pokemon recuperan todo su HP,
     * asi que construir otra Battle con los mismos Pokemon equivale a empezar de cero.
     * La construccion no dispara eventos. El Random se recibe para poder hacer pruebas repetibles.
     */
    public Battle(Pokemon pokemon1, Pokemon pokemon2, BattleListener listener, Random random)
    {
        this.pokemon1 = Objects.requireNonNull(pokemon1, "pokemon1");
        this.pokemon2 = Objects.requireNonNull(pokemon2, "pokemon2");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.random = Objects.requireNonNull(random, "random");

        if (pokemon1 == pokemon2)
        {
            throw new IllegalArgumentException("Los dos combatientes deben ser objetos Pokemon distintos");
        }

        //nueva batalla = HP completo (esto se hace solo aqui, nunca dentro de ejecutarTurno)
        pokemon1.restaurarHp();
        pokemon2.restaurarHp();

        atacanteActual = elegirPrimerAtacante();
    }

    //empieza el de mayor Speed; si empatan, se elige al azar
    private Pokemon elegirPrimerAtacante()
    {
        if (pokemon1.getSpeed() > pokemon2.getSpeed())
        {
            return pokemon1;
        }
        if (pokemon2.getSpeed() > pokemon1.getSpeed())
        {
            return pokemon2;
        }
        return random.nextBoolean() ? pokemon1 : pokemon2;
    }

    /**
     * Ejecuta UN turno: ataca el Pokemon que tiene el turno.
     * Eventos, en este orden: onTurn, onHpChanged y, solo si el defensor quedo en 0 HP, onBattleEnded.
     * Si la batalla ya termino no hace nada (ni ataque ni eventos).
     */
    public void ejecutarTurno()
    {
        if (terminada)
        {
            return;
        }

        Pokemon atacante = atacanteActual;
        Pokemon defensor = (atacante == pokemon1) ? pokemon2 : pokemon1;

        double efectividad = calcularEfectividad(primerTipo(atacante), primerTipo(defensor));
        boolean critico = random.nextDouble() < PROBABILIDAD_CRITICO;
        double variacion = VARIACION_MINIMA + random.nextDouble() * (VARIACION_MAXIMA - VARIACION_MINIMA);
        int dano = calcularDano(atacante.getAttack(), defensor.getDefense(), efectividad, variacion, critico);

        defensor.recibirDano(dano);

        listener.onTurn(atacante.getNombre(), defensor.getNombre(), dano, critico, efectividad);
        listener.onHpChanged(defensor.getNombre(), defensor.getHpActual());

        if (defensor.estaDerrotado())
        {
            terminada = true;
            ganador = atacante;
            listener.onBattleEnded(atacante.getNombre());
        }
        else
        {
            //la batalla sigue: el proximo turno ataca el otro
            atacanteActual = defensor;
        }
    }

    /**
     * dano = max(1, round( (attack / defense * 20 + 2) * efectividad * variacion * (critico ? 1.5 : 1) ))
     * Defense 0 se trata como 1 para evitar dividir por cero.
     */
    static int calcularDano(int attack, int defense, double efectividad, double variacion, boolean critico)
    {
        double base = (double) attack / Math.max(1, defense) * 20 + 2;
        double dano = base * efectividad * variacion * (critico ? MULTIPLICADOR_CRITICO : 1.0);
        return (int) Math.max(1, Math.round(dano));
    }

    /**
     * Efectividad usando solo el primer tipo de cada Pokemon (nombres de PokeAPI, en ingles):
     * water > fire, fire > grass, grass > water  -> 1.3
     * relacion inversa                           -> 0.7
     * cualquier otro caso                        -> 1.0
     */
    static double calcularEfectividad(String tipoAtacante, String tipoDefensor)
    {
        if (leGana(tipoAtacante, tipoDefensor))
        {
            return EFECTIVIDAD_VENTAJA;
        }
        if (leGana(tipoDefensor, tipoAtacante))
        {
            return EFECTIVIDAD_DESVENTAJA;
        }
        return EFECTIVIDAD_NEUTRA;
    }

    private static boolean leGana(String tipo, String contra)
    {
        return (tipo.equals("water") && contra.equals("fire"))
                || (tipo.equals("fire") && contra.equals("grass"))
                || (tipo.equals("grass") && contra.equals("water"));
    }

    private static String primerTipo(Pokemon pokemon)
    {
        return pokemon.getTipos().isEmpty() ? "" : pokemon.getTipos().get(0);
    }

    /** Pokemon al que le toca atacar en el proximo turno (antes del primer turno, el que empieza). */
    public Pokemon getAtacanteActual()
    {
        return atacanteActual;
    }

    public boolean estaTerminada()
    {
        return terminada;
    }

    /** Ganador de la batalla, o null si todavia no termino. */
    public Pokemon getGanador()
    {
        return ganador;
    }
}
