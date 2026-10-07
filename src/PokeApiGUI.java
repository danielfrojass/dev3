import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

public class PokeApiGUI implements BattleListener
{
    private JPanel mainPanel;
    private FichaPokemon fichaIzquierda;
    private FichaPokemon fichaDerecha;
    private JButton botonFight;
    private JTextArea areaLog;

    //estado de la batalla (todo se toca solo desde el hilo de Swing)
    private static final int INTERVALO_TURNO_MS = 800;
    private javax.swing.Timer timerBatalla;
    private Battle batalla;
    private boolean batallaEnCurso = false;
    private FichaPokemon fichaDefensoraDelTurno;   // quien recibe el ataque del turno actual (ver onTurn / onHpChanged)

    //PokeAPI tiene pokemon con id del 1 al 1025
    private static final int ID_MAXIMO_POKEMON = 1025;

    private final PokeApiClient cliente = new PokeApiClient();

    /**
     * Componentes de UNO de los dos lados de la ventana. Los dos lados son identicos,
     * por eso se agrupan en una clase interna en vez de duplicar los componentes.
     * Cada ficha tiene sus propios campos, botones y su propio Pokemon: son independientes.
     */
    private static class FichaPokemon
    {
        private final String titulo;
        private final JPanel panel;

        //entrada
        private final JTextField campoNombre = new JTextField(10);
        private final JButton botonLoad = new JButton("Load");
        private final JButton botonRandom = new JButton("Random");

        //datos del pokemon cargado
        private final JLabel etiquetaSprite = new JLabel("Sin Pokemon", SwingConstants.CENTER);
        private final JLabel valorNombre = new JLabel("-");
        private final JLabel valorTipos = new JLabel("-");
        private final JLabel valorHpMaximo = new JLabel("-");
        private final JLabel valorHpActual = new JLabel("-");
        private final JLabel valorAttack = new JLabel("-");
        private final JLabel valorDefense = new JLabel("-");
        private final JLabel valorSpeed = new JLabel("-");

        //pokemon cargado en este lado (null hasta que se cargue uno)
        private Pokemon pokemon;

        //true mientras hay una consulta (Load o Random) en curso en este lado
        private boolean cargando = false;

        FichaPokemon(String titulo)
        {
            this.titulo = titulo;

            //parte superior: campo de nombre y botones
            JPanel filaNombre = new JPanel(new BorderLayout(5, 0));
            filaNombre.add(new JLabel("Nombre:"), BorderLayout.WEST);
            filaNombre.add(campoNombre, BorderLayout.CENTER);

            JPanel filaBotones = new JPanel(new GridLayout(1, 2, 5, 0));
            filaBotones.add(botonLoad);
            filaBotones.add(botonRandom);

            JPanel entrada = new JPanel(new GridLayout(2, 1, 0, 5));
            entrada.add(filaNombre);
            entrada.add(filaBotones);

            //parte central: sprite y tabla de datos
            etiquetaSprite.setPreferredSize(new Dimension(130, 130));

            JPanel datos = new JPanel(new GridLayout(7, 2, 8, 4));
            agregarFila(datos, "Nombre:", valorNombre);
            agregarFila(datos, "Tipos:", valorTipos);
            agregarFila(datos, "HP maximo:", valorHpMaximo);
            agregarFila(datos, "HP actual:", valorHpActual);
            agregarFila(datos, "Attack:", valorAttack);
            agregarFila(datos, "Defense:", valorDefense);
            agregarFila(datos, "Speed:", valorSpeed);

            JPanel cuerpo = new JPanel(new BorderLayout(0, 8));
            cuerpo.add(etiquetaSprite, BorderLayout.NORTH);
            cuerpo.add(datos, BorderLayout.CENTER);

            panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(titulo),
                    new EmptyBorder(5, 8, 8, 8)));
            panel.add(entrada, BorderLayout.NORTH);
            panel.add(cuerpo, BorderLayout.CENTER);
        }

        private static void agregarFila(JPanel destino, String texto, JLabel valor)
        {
            destino.add(new JLabel(texto));
            destino.add(valor);
        }
    }

    public PokeApiGUI()
    {
        construirInterfaz();

        //cada lado tiene su propio Load y su propio Random, con su propia ficha
        configurarLoad(fichaIzquierda);
        configurarLoad(fichaDerecha);
        configurarRandom(fichaIzquierda);
        configurarRandom(fichaDerecha);
        configurarFight();

        actualizarEstadoBotones();
    }

    //arma toda la ventana: dos fichas arriba, boton Fight! en el centro y el log abajo
    private void construirInterfaz()
    {
        fichaIzquierda = new FichaPokemon("Pokemon 1");
        fichaDerecha = new FichaPokemon("Pokemon 2");

        //Fight! empieza deshabilitado; actualizarEstadoBotones() decide cuando se habilita
        botonFight = new JButton("Fight!");
        botonFight.setEnabled(false);

        JPanel zonaCentral = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(5, 10, 5, 10);
        c.gridy = 0;
        zonaCentral.add(new JLabel("VS"), c);
        c.gridy = 1;
        zonaCentral.add(botonFight, c);

        JPanel zonaCombatientes = new JPanel(new BorderLayout(10, 0));
        zonaCombatientes.add(fichaIzquierda.panel, BorderLayout.WEST);
        zonaCombatientes.add(zonaCentral, BorderLayout.CENTER);
        zonaCombatientes.add(fichaDerecha.panel, BorderLayout.EAST);

        //log de batalla: empieza vacio y es solo de lectura
        areaLog = new JTextArea(10, 50);
        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setWrapStyleWord(true);

        JScrollPane scrollLog = new JScrollPane(areaLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log de batalla"));

        mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.add(zonaCombatientes, BorderLayout.CENTER);
        mainPanel.add(scrollLog, BorderLayout.SOUTH);
    }

    private void configurarLoad(final FichaPokemon ficha)
    {
        ficha.botonLoad.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                //Load consulta por el nombre escrito en el campo
                consultarPokemon(ficha, ficha.campoNombre.getText(), ficha.botonLoad);
            }
        });
    }

    private void configurarRandom(final FichaPokemon ficha)
    {
        ficha.botonRandom.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                //Random consulta por un id aleatorio; el endpoint /pokemon/{id} es el mismo que /pokemon/{name}
                int id = ThreadLocalRandom.current().nextInt(1, ID_MAXIMO_POKEMON + 1);
                consultarPokemon(ficha, String.valueOf(id), ficha.botonRandom);
            }
        });
    }

    /**
     * Consulta un Pokemon en segundo plano y lo muestra en la ficha indicada.
     * Lo usan Load (nombre escrito) y Random (id aleatorio): la logica es la misma.
     *
     * @param botonActivo el boton que se pulso; es el que muestra "Cargando..."
     */
    private void consultarPokemon(final FichaPokemon ficha, final String nombrePokemon, final JButton botonActivo)
    {
        //se ejecuta en el hilo de Swing (EDT): aqui solo se lanza el trabajo en segundo plano
        final String textoOriginalBoton = botonActivo.getText();

        //mientras se consulta, Load y Random de ESTE lado quedan deshabilitados
        //(evita dos consultas simultaneas en la misma ficha); el otro lado no se afecta.
        //Fight! tambien se deshabilita: no se puede pelear mientras un combatiente esta cambiando
        ficha.cargando = true;
        actualizarEstadoBotones();
        botonActivo.setText("Cargando...");

        SwingWorker<Optional<Pokemon>, Void> worker = new SwingWorker<Optional<Pokemon>, Void>()
        {
            //hilo secundario: SOLO red y JSON, nunca se toca ningun componente Swing
            @Override
            protected Optional<Pokemon> doInBackground() throws Exception
            {
                return cliente.obtenerPokemon(nombrePokemon);
            }

            //SwingWorker llama a done() de vuelta en el EDT, aqui ya es seguro usar Swing
            @Override
            protected void done()
            {
                try
                {
                    Optional<Pokemon> resultado = get();

                    if (resultado.isPresent())
                    {
                        mostrarPokemon(ficha, resultado.get());
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(mainPanel, "El pokemon no existe",
                                ficha.titulo, JOptionPane.INFORMATION_MESSAGE);
                    }
                }
                catch (ExecutionException e)
                {
                    //get() envuelve la excepcion lanzada en doInBackground(); la real esta en getCause()
                    Throwable causa = e.getCause();

                    if (causa instanceof IllegalArgumentException)
                    {
                        //nombre vacio
                        JOptionPane.showMessageDialog(mainPanel, causa.getMessage(),
                                ficha.titulo, JOptionPane.WARNING_MESSAGE);
                    }
                    else if (causa instanceof IOException)
                    {
                        JOptionPane.showMessageDialog(mainPanel,
                                "No se pudo consultar PokeAPI (error de red o del servicio):\n" + causa.getMessage(),
                                ficha.titulo, JOptionPane.ERROR_MESSAGE);
                    }
                    else
                    {
                        //cualquier otra excepcion inesperada tambien se muestra, no se pierde
                        JOptionPane.showMessageDialog(mainPanel,
                                "Error inesperado: " + causa,
                                ficha.titulo, JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    //pase lo que pase, los botones vuelven a su estado correcto
                    botonActivo.setText(textoOriginalBoton);
                    ficha.cargando = false;
                    actualizarEstadoBotones();
                }
            }
        };

        worker.execute();
    }

    //vuelca los datos del Pokemon en la ficha indicada (solo modifica ese lado)
    private void mostrarPokemon(FichaPokemon ficha, Pokemon pokemon)
    {
        ficha.pokemon = pokemon;

        ficha.campoNombre.setText(pokemon.getNombre());
        ficha.valorNombre.setText(pokemon.getNombre());
        ficha.valorTipos.setText(String.join(", ", pokemon.getTipos()));
        ficha.valorHpMaximo.setText(String.valueOf(pokemon.getHpMaximo()));
        ficha.valorHpActual.setText(String.valueOf(pokemon.getHpActual()));
        ficha.valorAttack.setText(String.valueOf(pokemon.getAttack()));
        ficha.valorDefense.setText(String.valueOf(pokemon.getDefense()));
        ficha.valorSpeed.setText(String.valueOf(pokemon.getSpeed()));

        if (pokemon.getSprite() != null)
        {
            ficha.etiquetaSprite.setText("");
            ficha.etiquetaSprite.setIcon(pokemon.getSprite());
        }
        else
        {
            ficha.etiquetaSprite.setIcon(null);
            ficha.etiquetaSprite.setText("Sin imagen");
        }
    }

    //decide que botones estan habilitados segun el estado actual (carga en curso, batalla en curso, pokemon cargados)
    private void actualizarEstadoBotones()
    {
        FichaPokemon[] fichas = {fichaIzquierda, fichaDerecha};
        for (FichaPokemon ficha : fichas)
        {
            //Load y Random solo se pueden usar si ese lado no esta cargando y no hay batalla
            boolean libre = !batallaEnCurso && !ficha.cargando;
            ficha.botonLoad.setEnabled(libre);
            ficha.botonRandom.setEnabled(libre);
        }
        botonFight.setEnabled(puedePelear());
    }

    //Fight! solo es posible con los dos pokemon cargados, ninguna carga pendiente y sin batalla en curso
    private boolean puedePelear()
    {
        return fichaIzquierda.pokemon != null
                && fichaDerecha.pokemon != null
                && !fichaIzquierda.cargando
                && !fichaDerecha.cargando
                && !batallaEnCurso;
    }

    private void configurarFight()
    {
        //un solo turno por tick del Timer: no hay ningun bucle de combate
        timerBatalla = new javax.swing.Timer(INTERVALO_TURNO_MS, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                batalla.ejecutarTurno();
            }
        });

        botonFight.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                iniciarBatalla();
            }
        });
    }

    private void iniciarBatalla()
    {
        //doble seguridad: aunque el boton este deshabilitado, no se inicia sin las condiciones
        if (!puedePelear())
        {
            return;
        }

        batallaEnCurso = true;
        actualizarEstadoBotones();

        //siempre una Battle NUEVA: su constructor restaura el HP de los dos pokemon
        batalla = new Battle(fichaIzquierda.pokemon, fichaDerecha.pokemon, this);

        //Battle no emite eventos al crearse; por eso, una sola vez y antes del primer turno, se muestra el HP
        //ya restaurado. Durante el combate el HP mostrado viene solo de onHpChanged.
        fichaIzquierda.valorHpActual.setText(String.valueOf(fichaIzquierda.pokemon.getHpActual()));
        fichaDerecha.valorHpActual.setText(String.valueOf(fichaDerecha.pokemon.getHpActual()));

        FichaPokemon primera = fichaDe(batalla.getAtacanteActual());
        escribirLog("=== Nueva batalla: " + fichaIzquierda.pokemon.getNombre() + " (" + fichaIzquierda.titulo + ") vs "
                + fichaDerecha.pokemon.getNombre() + " (" + fichaDerecha.titulo + ") ===");
        escribirLog("Empieza " + primera.pokemon.getNombre() + " (" + primera.titulo + ")");

        timerBatalla.start();
    }

    //busca la ficha de un Pokemon comparando el OBJETO (no el nombre: los dos lados pueden tener el mismo nombre)
    private FichaPokemon fichaDe(Pokemon pokemon)
    {
        return pokemon == fichaIzquierda.pokemon ? fichaIzquierda : fichaDerecha;
    }

    private void escribirLog(String linea)
    {
        areaLog.append(linea + "\n");
        areaLog.setCaretPosition(areaLog.getDocument().getLength());   // mantiene visible la ultima linea
    }

    //--- BattleListener: Battle los invoca desde ejecutarTurno(), que corre en el Timer (hilo de Swing) ---

    @Override
    public void onTurn(String attacker, String defender, int damage, boolean critical, double modifier)
    {
        //Battle cambia de turno DESPUES de notificar, asi que aqui getAtacanteActual() sigue siendo quien ataco.
        //Con eso se sabe cual ficha es la defensora aunque los dos pokemon tengan el mismo nombre.
        FichaPokemon fichaAtacante = fichaDe(batalla.getAtacanteActual());
        fichaDefensoraDelTurno = (fichaAtacante == fichaIzquierda) ? fichaDerecha : fichaIzquierda;

        String linea = attacker + " (" + fichaAtacante.titulo + ") ataca a "
                + defender + " (" + fichaDefensoraDelTurno.titulo + ") y le causa " + damage + " de dano";
        if (critical)
        {
            linea += " - GOLPE CRITICO!";
        }
        linea += " - efectividad x" + modifier;

        escribirLog(linea);   //el HP no se actualiza aqui, lo hace onHpChanged
    }

    @Override
    public void onHpChanged(String pokemon, int hpActual)
    {
        //se actualiza la ficha guardada en onTurn, NO se busca por nombre (seria ambiguo con pokemon repetidos)
        fichaDefensoraDelTurno.valorHpActual.setText(String.valueOf(hpActual));
    }

    @Override
    public void onBattleEnded(String winner)
    {
        timerBatalla.stop();

        //getGanador() ya esta asignado cuando Battle notifica el final
        FichaPokemon fichaGanadora = fichaDe(batalla.getGanador());
        escribirLog("*** Gana " + winner + " (" + fichaGanadora.titulo + ") ***");

        batallaEnCurso = false;
        fichaDefensoraDelTurno = null;
        actualizarEstadoBotones();   // Load y Random vuelven; Fight! solo si los dos siguen cargados
    }

    public static void main(String[] args)
    {
        //la ventana se crea en el hilo de Swing
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                JFrame frame = new JFrame("PokeApi");
                frame.setContentPane(new PokeApiGUI().mainPanel);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
