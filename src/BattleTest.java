import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Prueba TEMPORAL de Battle (sin frameworks): se ejecuta con su main y se puede borrar sin afectar al proyecto.
 */
public class BattleTest
{
    private static int pruebas = 0;
    private static int fallos = 0;

    private static void check(String descripcion, boolean ok)
    {
        pruebas++;
        if (!ok)
        {
            fallos++;
        }
        System.out.println((ok ? "  [OK]    " : "  [FALLO] ") + descripcion);
    }

    //listener de prueba: guarda todo lo que recibe
    static class Registro implements BattleListener
    {
        final List<String> orden = new ArrayList<>();      // "T" = onTurn, "H" = onHpChanged, "E" = onBattleEnded
        final List<String> atacantes = new ArrayList<>();
        final List<String> defensores = new ArrayList<>();
        final List<Integer> danos = new ArrayList<>();
        final List<Boolean> criticos = new ArrayList<>();
        final List<Double> modificadores = new ArrayList<>();
        final List<String> hpNombres = new ArrayList<>();
        final List<Integer> hpValores = new ArrayList<>();
        final List<String> ganadores = new ArrayList<>();

        @Override
        public void onTurn(String attacker, String defender, int damage, boolean critical, double modifier)
        {
            orden.add("T");
            atacantes.add(attacker);
            defensores.add(defender);
            danos.add(damage);
            criticos.add(critical);
            modificadores.add(modifier);
        }

        @Override
        public void onHpChanged(String pokemon, int hpActual)
        {
            orden.add("H");
            hpNombres.add(pokemon);
            hpValores.add(hpActual);
        }

        @Override
        public void onBattleEnded(String winner)
        {
            orden.add("E");
            ganadores.add(winner);
        }

        int totalEventos()
        {
            return orden.size();
        }
    }

    private static Pokemon pokemon(String nombre, int hp, int attack, int defense, int speed, String... tipos)
    {
        return new Pokemon(nombre, null, Arrays.asList(tipos), hp, attack, defense, speed);
    }

    public static void main(String[] args)
    {
        probarQuienEmpieza();
        probarUnTurnoPorLlamada();
        probarEfectividad();
        probarFormulaDeDano();
        probarFinDeBatalla();
        probarCriticoYVariacion();
        probarReinicioYValidaciones();

        System.out.println("\nResultado: " + (pruebas - fallos) + "/" + pruebas + " comprobaciones correctas");
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static void probarQuienEmpieza()
    {
        System.out.println("\n== Quien empieza ==");
        Pokemon rapido = pokemon("rapido", 100, 50, 50, 100, "normal");
        Pokemon lento = pokemon("lento", 100, 50, 50, 10, "normal");

        Registro r = new Registro();
        Battle b = new Battle(lento, rapido, r, new Random(1));   // el rapido va como segundo argumento
        check("mayor Speed es el atacante inicial (rapido como pokemon2)", b.getAtacanteActual() == rapido);
        check("crear la batalla no dispara eventos", r.totalEventos() == 0);
        b.ejecutarTurno();
        check("el primer onTurn lo ataca el de mayor Speed", r.atacantes.get(0).equals("rapido"));

        Registro r2 = new Registro();
        Battle b2 = new Battle(rapido, lento, r2, new Random(1));   // y ahora como primer argumento
        b2.ejecutarTurno();
        check("tambien empieza el de mayor Speed (rapido como pokemon1)", r2.atacantes.get(0).equals("rapido"));

        //empate: debe salir cada uno alrededor de la mitad de las veces
        Pokemon a = pokemon("a", 100, 50, 50, 70, "normal");
        Pokemon c = pokemon("c", 100, 50, 50, 70, "normal");
        Random azar = new Random(7);
        int empiezaA = 0;
        for (int i = 0; i < 1000; i++)
        {
            if (new Battle(a, c, new Registro(), azar).getAtacanteActual() == a)
            {
                empiezaA++;
            }
        }
        check("empate de Speed: elige al azar (a empezo " + empiezaA + "/1000 veces)", empiezaA > 400 && empiezaA < 600);
    }

    private static void probarUnTurnoPorLlamada()
    {
        System.out.println("\n== Un turno por llamada, parametros y HP ==");
        Pokemon a = pokemon("a", 1000, 60, 50, 90, "normal");
        Pokemon b = pokemon("b", 1000, 55, 45, 10, "normal");
        Registro r = new Registro();
        Battle batalla = new Battle(a, b, r, new Random(3));

        boolean unSoloAtaquePorLlamada = true, alterna = true, hpCoherente = true, hpEventoDelDefensor = true;
        boolean parametrosValidos = true, hpNuncaSube = true;
        String esperadoAtacante = "a";   // a tiene mayor Speed
        for (int i = 0; i < 8; i++)
        {
            Pokemon defensor = esperadoAtacante.equals("a") ? b : a;
            int hpAntes = defensor.getHpActual();

            batalla.ejecutarTurno();

            unSoloAtaquePorLlamada &= (r.atacantes.size() == i + 1) && (r.hpValores.size() == i + 1);
            alterna &= r.atacantes.get(i).equals(esperadoAtacante);
            hpEventoDelDefensor &= r.hpNombres.get(i).equals(defensor.getNombre()) && r.defensores.get(i).equals(defensor.getNombre());
            hpCoherente &= r.hpValores.get(i) == Math.max(0, hpAntes - r.danos.get(i)) && r.hpValores.get(i) == defensor.getHpActual();
            hpNuncaSube &= defensor.getHpActual() <= hpAntes;
            double m = r.modificadores.get(i);
            parametrosValidos &= r.danos.get(i) >= 1 && (m == 0.7 || m == 1.0 || m == 1.3);

            esperadoAtacante = esperadoAtacante.equals("a") ? "b" : "a";
        }
        check("cada ejecutarTurno() produce exactamente un onTurn y un onHpChanged", unSoloAtaquePorLlamada);
        check("los atacantes se alternan (a, b, a, b, ...)", alterna);
        check("onTurn informa atacante/defensor; onHpChanged nombra al defensor", hpEventoDelDefensor);
        check("onHpChanged = HP anterior - dano, igual al HP real del Pokemon", hpCoherente);
        check("dano >= 1 y modifier en {0.7, 1.0, 1.3}", parametrosValidos);
        check("el HP solo baja entre turnos (no se restaura dentro de ejecutarTurno)", hpNuncaSube);
        check("la batalla no termina mientras ambos tengan HP", !batalla.estaTerminada() && batalla.getGanador() == null);
    }

    private static void probarEfectividad()
    {
        System.out.println("\n== Efectividad (solo primer tipo) ==");
        check("water > fire = 1.3", Battle.calcularEfectividad("water", "fire") == 1.3);
        check("fire > grass = 1.3", Battle.calcularEfectividad("fire", "grass") == 1.3);
        check("grass > water = 1.3", Battle.calcularEfectividad("grass", "water") == 1.3);
        check("fire vs water = 0.7", Battle.calcularEfectividad("fire", "water") == 0.7);
        check("grass vs fire = 0.7", Battle.calcularEfectividad("grass", "fire") == 0.7);
        check("water vs grass = 0.7", Battle.calcularEfectividad("water", "grass") == 0.7);
        check("mismo tipo = 1.0", Battle.calcularEfectividad("fire", "fire") == 1.0);
        check("tipo fuera de la regla = 1.0", Battle.calcularEfectividad("electric", "water") == 1.0);
        check("sin tipo = 1.0", Battle.calcularEfectividad("", "fire") == 1.0);

        //a traves de Battle: el modifier reportado es solo la efectividad
        Pokemon agua = pokemon("agua", 1000, 50, 50, 90, "water");
        Pokemon fuego = pokemon("fuego", 1000, 50, 50, 10, "fire");
        Registro r = new Registro();
        Battle b = new Battle(agua, fuego, r, new Random(5));
        b.ejecutarTurno();
        b.ejecutarTurno();
        check("onTurn agua->fuego informa modifier 1.3", r.modificadores.get(0) == 1.3);
        check("onTurn fuego->agua informa modifier 0.7", r.modificadores.get(1) == 0.7);

        //solo cuenta el PRIMER tipo: [normal, water] contra fire es neutro
        Pokemon mixto = pokemon("mixto", 1000, 50, 50, 90, "normal", "water");
        Pokemon fuego2 = pokemon("fuego2", 1000, 50, 50, 10, "fire");
        Registro r2 = new Registro();
        new Battle(mixto, fuego2, r2, new Random(5)).ejecutarTurno();
        check("el segundo tipo se ignora ([normal, water] vs fire = 1.0)", r2.modificadores.get(0) == 1.0);
    }

    private static void probarFormulaDeDano()
    {
        System.out.println("\n== Formula de dano ==");
        check("attack 50 / defense 50, sin extras = 22", Battle.calcularDano(50, 50, 1.0, 1.0, false) == 22);
        check("attack 100 / defense 50 = 42", Battle.calcularDano(100, 50, 1.0, 1.0, false) == 42);
        check("critico x1.5: 22 * 1.5 = 33", Battle.calcularDano(50, 50, 1.0, 1.0, true) == 33);
        check("efectividad 1.3: 22 * 1.3 = 28.6 -> 29", Battle.calcularDano(50, 50, 1.3, 1.0, false) == 29);
        check("efectividad 0.7: 22 * 0.7 = 15.4 -> 15", Battle.calcularDano(50, 50, 0.7, 1.0, false) == 15);
        check("variacion 0.85: 22 * 0.85 = 18.7 -> 19", Battle.calcularDano(50, 50, 1.0, 0.85, false) == 19);
        check("minimo 1 aunque attack sea muy bajo", Battle.calcularDano(1, 1000, 0.7, 0.85, false) >= 1);
        check("defense 0 no rompe la formula", Battle.calcularDano(50, 0, 1.0, 1.0, false) >= 1);
    }

    private static void probarFinDeBatalla()
    {
        System.out.println("\n== Fin de batalla ==");
        Pokemon a = pokemon("a", 30, 60, 50, 90, "normal");
        Pokemon b = pokemon("b", 30, 60, 50, 10, "normal");
        Registro r = new Registro();
        Battle batalla = new Battle(a, b, r, new Random(11));

        int turnos = 0;
        while (!batalla.estaTerminada() && turnos < 100)
        {
            batalla.ejecutarTurno();
            turnos++;
        }
        check("la batalla termino (en " + turnos + " turnos)", batalla.estaTerminada() && turnos < 100);

        String ultimoAtacante = r.atacantes.get(r.atacantes.size() - 1);
        String ultimoDefensor = r.defensores.get(r.defensores.size() - 1);
        Pokemon perdedor = ultimoDefensor.equals("a") ? a : b;
        check("el perdedor (el que recibio el ultimo golpe) quedo en 0 HP", perdedor.getHpActual() == 0);
        check("el ultimo onHpChanged reporta 0 para el perdedor",
                r.hpValores.get(r.hpValores.size() - 1) == 0 && r.hpNombres.get(r.hpNombres.size() - 1).equals(ultimoDefensor));
        check("onBattleEnded se llamo una sola vez, con el ganador = ultimo atacante",
                r.ganadores.size() == 1 && r.ganadores.get(0).equals(ultimoAtacante));
        check("getGanador() coincide con el ganador notificado", batalla.getGanador().getNombre().equals(r.ganadores.get(0)));

        boolean hpNoNegativo = true;
        for (int hp : r.hpValores)
        {
            hpNoNegativo &= hp >= 0;
        }
        check("ningun onHpChanged reporto HP negativo", hpNoNegativo && a.getHpActual() >= 0 && b.getHpActual() >= 0);

        StringBuilder secuencia = new StringBuilder();
        for (String e : r.orden)
        {
            secuencia.append(e);
        }
        check("orden de eventos: (onTurn, onHpChanged)* y al final onBattleEnded -> " + secuencia,
                secuencia.toString().matches("(TH)+E"));

        //despues de terminar: nada mas
        int eventosAntes = r.totalEventos();
        int hpAAntes = a.getHpActual(), hpBAntes = b.getHpActual();
        for (int i = 0; i < 5; i++)
        {
            batalla.ejecutarTurno();
        }
        check("ejecutarTurno() despues de terminar no produce ataques ni eventos",
                r.totalEventos() == eventosAntes && r.ganadores.size() == 1);
        check("ni cambia el HP de nadie", a.getHpActual() == hpAAntes && b.getHpActual() == hpBAntes);
    }

    private static void probarCriticoYVariacion()
    {
        System.out.println("\n== Critico (~10%) y variacion (0.85-1.0) ==");
        Pokemon a = pokemon("a", 100_000_000, 50, 50, 90, "normal");
        Pokemon b = pokemon("b", 100_000_000, 50, 50, 10, "normal");
        Registro r = new Registro();
        Battle batalla = new Battle(a, b, r, new Random(2024));

        int total = 20000;
        for (int i = 0; i < total; i++)
        {
            batalla.ejecutarTurno();
        }

        int criticos = 0;
        boolean sinCriticoEnRango = true, criticoEnRango = true;
        for (int i = 0; i < total; i++)
        {
            int dano = r.danos.get(i);
            if (r.criticos.get(i))
            {
                criticos++;
                criticoEnRango &= dano >= 28 && dano <= 33;     // 22 * 0.85 * 1.5 .. 22 * 1.5
            }
            else
            {
                sinCriticoEnRango &= dano >= 19 && dano <= 22;  // 22 * 0.85 .. 22
            }
        }
        double porcentaje = 100.0 * criticos / total;
        check("frecuencia de criticos ~10% (" + String.format("%.2f", porcentaje) + "% en " + total + " turnos)",
                porcentaje > 9.0 && porcentaje < 11.0);
        check("dano sin critico entre 19 y 22 (variacion 0.85-1.0)", sinCriticoEnRango);
        check("dano critico entre 28 y 33 (variacion y x1.5)", criticoEnRango);
    }

    private static void probarReinicioYValidaciones()
    {
        System.out.println("\n== Reinicio y validaciones ==");
        Pokemon a = pokemon("a", 30, 60, 50, 90, "normal");
        Pokemon b = pokemon("b", 30, 60, 50, 10, "normal");

        Battle primera = new Battle(a, b, new Registro(), new Random(1));
        while (!primera.estaTerminada())
        {
            primera.ejecutarTurno();
        }
        check("antes de reiniciar, un Pokemon quedo en 0 HP", a.getHpActual() == 0 || b.getHpActual() == 0);

        Registro r = new Registro();
        Battle segunda = new Battle(a, b, r, new Random(2));
        check("nueva Battle con los mismos Pokemon restaura el HP de ambos",
                a.getHpActual() == a.getHpMaximo() && b.getHpActual() == b.getHpMaximo());
        check("la nueva Battle no esta terminada y no disparo eventos", !segunda.estaTerminada() && r.totalEventos() == 0);

        while (!segunda.estaTerminada())
        {
            segunda.ejecutarTurno();
        }
        check("la nueva batalla se juega completa y termina una sola vez", r.ganadores.size() == 1);

        int eventos = r.totalEventos();
        primera.ejecutarTurno();
        check("la Battle anterior sigue terminada y no genera eventos", r.totalEventos() == eventos);

        boolean npe = false, iae = false;
        try
        {
            new Battle(a, b, null);
        }
        catch (NullPointerException e)
        {
            npe = true;
        }
        try
        {
            new Battle(a, a, new Registro());
        }
        catch (IllegalArgumentException e)
        {
            iae = true;
        }
        check("listener null lanza NullPointerException", npe);
        check("el mismo objeto Pokemon dos veces lanza IllegalArgumentException", iae);
    }
}
