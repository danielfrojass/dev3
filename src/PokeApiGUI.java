import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
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

    //paleta inspirada en Pokemon
    private static final Color AZUL = new Color(0x3B4CCA);
    private static final Color AZUL_OSCURO = new Color(0x1D2C5E);
    private static final Color ROJO = new Color(0xDC0A2D);
    private static final Color AMARILLO = new Color(0xFFCB05);
    private static final Color FONDO = new Color(0xEAF0FB);
    private static final Color TEXTO = new Color(0x22304A);
    private static final Color TEXTO_SUAVE = new Color(0x5A6478);
    private static final Color GRIS_INACTIVO = new Color(0xD3D8E3);

    //medidas fijas: el ancho de una ficha NO depende del nombre del pokemon cargado
    private static final int ANCHO_FICHA = 260;
    private static final int ANCHO_ZONA_CENTRAL = 140;

    //imagenes de la interfaz (se leen del classpath: carpeta src/assets). Si no existen, simplemente no se muestran.
    private static final String RUTA_LOGO = "/assets/logo.png";
    private static final String RUTA_POKEBALL = "/assets/pokeball.png";

    /**
     * Componentes de UNO de los dos lados de la ventana. Los dos lados son identicos,
     * por eso se agrupan en una clase interna en vez de duplicar los componentes.
     * Cada ficha tiene sus propios campos, botones y su propio Pokemon: son independientes.
     * El ancho de la ficha es fijo (ANCHO_FICHA): un nombre largo se recorta con "..." en vez de agrandarla.
     */
    private static class FichaPokemon
    {
        private final String titulo;
        private final JPanel panel;

        //entrada
        private final JTextField campoNombre = new JTextField(10);
        private final JButton botonLoad;
        private final JButton botonRandom;

        //datos del pokemon cargado
        private final JLabel etiquetaSprite = new JLabel("Sin Pokemon", SwingConstants.CENTER);
        private final JLabel valorNombre = new JLabel("-", SwingConstants.CENTER);
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

        FichaPokemon(String titulo, Color acento)
        {
            this.titulo = titulo;

            botonLoad = new BotonJuego("Load", acento, Color.WHITE);
            botonRandom = new BotonJuego("Random", AMARILLO, AZUL_OSCURO);

            //parte superior: campo de nombre y botones
            JLabel etiquetaNombre = new JLabel("Nombre:");
            etiquetaNombre.setForeground(TEXTO_SUAVE);

            JPanel filaNombre = new JPanel(new BorderLayout(6, 0));
            filaNombre.setOpaque(false);
            filaNombre.add(etiquetaNombre, BorderLayout.WEST);
            filaNombre.add(campoNombre, BorderLayout.CENTER);

            JPanel filaBotones = new JPanel(new GridLayout(1, 2, 6, 0));
            filaBotones.setOpaque(false);
            filaBotones.add(botonLoad);
            filaBotones.add(botonRandom);

            JPanel entrada = new JPanel(new GridLayout(2, 1, 0, 6));
            entrada.setOpaque(false);
            entrada.add(filaNombre);
            entrada.add(filaBotones);

            //sprite
            etiquetaSprite.setPreferredSize(new Dimension(130, 120));
            etiquetaSprite.setOpaque(true);
            etiquetaSprite.setBackground(new Color(0xF1F5FD));
            etiquetaSprite.setForeground(TEXTO_SUAVE);
            etiquetaSprite.setBorder(BorderFactory.createLineBorder(new Color(0xC9D3EA), 1, true));

            //nombre como titular de la ficha (JLabel recorta con "..." si no cabe; el nombre completo va en el tooltip)
            valorNombre.setFont(valorNombre.getFont().deriveFont(Font.BOLD, 16f));
            valorNombre.setForeground(acento);

            JPanel datos = new JPanel(new GridLayout(6, 1, 0, 3));
            datos.setOpaque(false);
            agregarFila(datos, "Tipos:", valorTipos);
            agregarFila(datos, "HP maximo:", valorHpMaximo);
            agregarFila(datos, "HP actual:", valorHpActual);
            agregarFila(datos, "Attack:", valorAttack);
            agregarFila(datos, "Defense:", valorDefense);
            agregarFila(datos, "Speed:", valorSpeed);

            JPanel centro = new JPanel(new BorderLayout(0, 6));
            centro.setOpaque(false);
            centro.add(valorNombre, BorderLayout.NORTH);
            centro.add(datos, BorderLayout.CENTER);

            JPanel cuerpo = new JPanel(new BorderLayout(0, 8));
            cuerpo.setOpaque(false);
            cuerpo.add(etiquetaSprite, BorderLayout.NORTH);
            cuerpo.add(centro, BorderLayout.CENTER);

            //el relleno blanco va en un panel interior para que no asome por fuera del borde redondeado
            JPanel interior = new JPanel(new BorderLayout(0, 10));
            interior.setBackground(Color.WHITE);
            interior.setBorder(new EmptyBorder(6, 10, 10, 10));
            interior.add(entrada, BorderLayout.NORTH);
            interior.add(cuerpo, BorderLayout.CENTER);

            panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(acento, 2, true), titulo,
                    TitledBorder.CENTER, TitledBorder.TOP, new Font(Font.SANS_SERIF, Font.BOLD, 14), acento));
            panel.add(interior, BorderLayout.CENTER);

            //tamano FIJO: el alto sale del contenido y el ancho es siempre el mismo
            panel.setPreferredSize(new Dimension(ANCHO_FICHA, panel.getPreferredSize().height));
        }

        private static void agregarFila(JPanel destino, String texto, JLabel valor)
        {
            JLabel etiqueta = new JLabel(texto);
            etiqueta.setForeground(TEXTO_SUAVE);
            etiqueta.setPreferredSize(new Dimension(86, 18));

            valor.setFont(valor.getFont().deriveFont(Font.BOLD));
            valor.setForeground(TEXTO);

            JPanel fila = new JPanel(new BorderLayout(6, 0));
            fila.setOpaque(false);
            fila.add(etiqueta, BorderLayout.WEST);
            fila.add(valor, BorderLayout.CENTER);
            destino.add(fila);
        }
    }

    //boton redondeado con colores propios; se ve distinto cuando esta deshabilitado
    private static class BotonJuego extends JButton
    {
        private static final long serialVersionUID = 1L;

        private final Color colorFondo;

        BotonJuego(String texto, Color colorFondo, Color colorTexto)
        {
            super(texto);
            this.colorFondo = colorFondo;
            setForeground(colorTexto);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color color = colorFondo;
            if (!isEnabled())
            {
                color = GRIS_INACTIVO;
            }
            else if (getModel().isPressed())
            {
                color = colorFondo.darker();
            }
            else if (getModel().isRollover())
            {
                color = colorFondo.brighter();
            }

            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();

            super.paintComponent(g);   //dibuja el texto
        }
    }

    //panel con fondo degradado y, opcionalmente, una imagen decorativa translucida en la esquina inferior derecha
    private static class PanelFondo extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private final Color arriba;
        private final Color abajo;
        private final transient BufferedImage decoracion;   //puede ser null

        PanelFondo(Color arriba, Color abajo, BufferedImage decoracion)
        {
            super(new GridBagLayout());
            this.arriba = arriba;
            this.abajo = abajo;
            this.decoracion = decoracion;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, arriba, 0, getHeight(), abajo));
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (decoracion != null)
            {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                g2.drawImage(decoracion,
                        getWidth() - decoracion.getWidth() * 2 / 3,
                        getHeight() - decoracion.getHeight() * 2 / 3, null);
            }
            g2.dispose();
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

    //arma toda la ventana: cabecera, dos fichas arriba, boton Fight! en el centro y el log abajo
    private void construirInterfaz()
    {
        fichaIzquierda = new FichaPokemon("Pokemon 1", AZUL);
        fichaDerecha = new FichaPokemon("Pokemon 2", ROJO);

        //Fight! empieza deshabilitado; actualizarEstadoBotones() decide cuando se habilita
        botonFight = new BotonJuego("Fight!", ROJO, Color.WHITE);
        botonFight.setFont(botonFight.getFont().deriveFont(Font.BOLD, 20f));
        botonFight.setPreferredSize(new Dimension(120, 52));
        botonFight.setEnabled(false);

        JLabel etiquetaVs = new JLabel("VS", SwingConstants.CENTER);
        etiquetaVs.setFont(etiquetaVs.getFont().deriveFont(Font.BOLD, 28f));
        etiquetaVs.setForeground(AZUL_OSCURO);

        //zona central de ancho fijo: Fight! siempre tiene sitio y no se tapa
        JPanel zonaCentral = new JPanel(new GridBagLayout());
        zonaCentral.setOpaque(false);
        zonaCentral.setPreferredSize(new Dimension(ANCHO_ZONA_CENTRAL, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(5, 0, 5, 0);
        c.gridy = 0;
        zonaCentral.add(etiquetaVs, c);
        c.gridy = 1;
        zonaCentral.add(botonFight, c);

        JPanel zonaCombatientes = new JPanel(new BorderLayout(10, 0));
        zonaCombatientes.setOpaque(false);
        zonaCombatientes.add(fichaIzquierda.panel, BorderLayout.WEST);
        zonaCombatientes.add(zonaCentral, BorderLayout.CENTER);
        zonaCombatientes.add(fichaDerecha.panel, BorderLayout.EAST);

        //log de batalla: empieza vacio y es solo de lectura
        areaLog = new JTextArea(9, 50);
        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setWrapStyleWord(true);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        areaLog.setMargin(new Insets(6, 8, 6, 8));
        areaLog.setBackground(new Color(0xFFFDF3));
        areaLog.setForeground(TEXTO);

        JScrollPane scrollLog = new JScrollPane(areaLog);
        scrollLog.setBackground(FONDO);
        scrollLog.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(AZUL_OSCURO, 2, true),
                "Log de batalla", TitledBorder.LEFT, TitledBorder.TOP, new Font(Font.SANS_SERIF, Font.BOLD, 13), AZUL_OSCURO));

        JPanel contenido = new JPanel(new BorderLayout(0, 10));
        contenido.setOpaque(false);
        contenido.setBorder(new EmptyBorder(12, 12, 12, 12));
        contenido.add(zonaCombatientes, BorderLayout.CENTER);
        contenido.add(scrollLog, BorderLayout.SOUTH);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(FONDO);
        mainPanel.add(crearCabecera(), BorderLayout.NORTH);
        mainPanel.add(contenido, BorderLayout.CENTER);
    }

    //franja de titulo; si existe la imagen de la pokeball, se usa como icono pequeno
    private static JPanel crearCabecera()
    {
        JLabel titulo = new JLabel("POK\u00C9 BATTLE");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        titulo.setForeground(AMARILLO);

        BufferedImage pokeball = escalar(cargarImagen(RUTA_POKEBALL), 30, 30);
        if (pokeball != null)
        {
            titulo.setIcon(new ImageIcon(pokeball));
            titulo.setIconTextGap(10);
        }

        JPanel cabecera = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        cabecera.setBackground(AZUL_OSCURO);
        cabecera.add(titulo);
        return cabecera;
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
        ficha.campoNombre.setCaretPosition(0);   //si el nombre es largo, el campo muestra su comienzo
        ficha.valorNombre.setText(pokemon.getNombre());
        ficha.valorTipos.setText(String.join(", ", pokemon.getTipos()));

        //si el nombre no cabe en la ficha se recorta al dibujarlo; el nombre completo se ve en el tooltip
        ficha.valorNombre.setToolTipText(pokemon.getNombre());
        ficha.campoNombre.setToolTipText(pokemon.getNombre());
        ficha.valorTipos.setToolTipText(String.join(", ", pokemon.getTipos()));
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

    //--- pantalla de bienvenida e imagenes ---

    //lee una imagen del classpath; devuelve null si no existe o no se puede leer (la interfaz funciona igual)
    private static BufferedImage cargarImagen(String ruta)
    {
        try
        {
            URL url = PokeApiGUI.class.getResource(ruta);
            return url == null ? null : ImageIO.read(url);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    //reduce la imagen para que quepa en maxAncho x maxAlto SIN deformarla (misma proporcion); nunca la agranda
    private static BufferedImage escalar(BufferedImage original, int maxAncho, int maxAlto)
    {
        if (original == null)
        {
            return null;
        }

        double factor = Math.min((double) maxAncho / original.getWidth(), (double) maxAlto / original.getHeight());
        if (factor >= 1.0)
        {
            return original;
        }

        int ancho = Math.max(1, (int) Math.round(original.getWidth() * factor));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * factor));

        //se reduce por pasos (como maximo a la mitad cada vez) para que la imagen quede nitida
        BufferedImage actual = original;
        while (actual.getWidth() / 2 > ancho)
        {
            actual = redimensionar(actual, actual.getWidth() / 2, Math.max(1, actual.getHeight() / 2));
        }
        return redimensionar(actual, ancho, alto);
    }

    private static BufferedImage redimensionar(BufferedImage origen, int ancho, int alto)
    {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = destino.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(origen, 0, 0, ancho, alto, null);
        g2.dispose();
        return destino;
    }

    //pantalla inicial: fondo azul, logo (si existe), titulo, pokeball decorativa (si existe) y boton INICIAR
    private static JPanel crearPantallaBienvenida(final Runnable alIniciar)
    {
        BufferedImage logo = escalar(cargarImagen(RUTA_LOGO), 460, 290);
        BufferedImage pokeball = escalar(cargarImagen(RUTA_POKEBALL), 400, 400);

        PanelFondo fondo = new PanelFondo(AZUL, AZUL_OSCURO, pokeball);
        fondo.setPreferredSize(new Dimension(680, 600));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(8, 20, 8, 20);
        int fila = 0;

        if (logo != null)
        {
            c.gridy = fila++;
            fondo.add(new JLabel(new ImageIcon(logo)), c);
        }

        JLabel titulo = new JLabel("POK\u00C9 BATTLE");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 34f));
        titulo.setForeground(AMARILLO);
        c.gridy = fila++;
        fondo.add(titulo, c);

        JLabel subtitulo = new JLabel("Elige dos Pok\u00E9mon y que comience el combate");
        subtitulo.setFont(subtitulo.getFont().deriveFont(Font.PLAIN, 15f));
        subtitulo.setForeground(Color.WHITE);
        c.gridy = fila++;
        fondo.add(subtitulo, c);

        JButton botonIniciar = new BotonJuego("INICIAR", AMARILLO, AZUL_OSCURO);
        botonIniciar.setFont(botonIniciar.getFont().deriveFont(Font.BOLD, 26f));
        botonIniciar.setPreferredSize(new Dimension(260, 68));
        botonIniciar.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                alIniciar.run();
            }
        });
        c.gridy = fila++;
        c.insets = new Insets(26, 20, 8, 20);
        fondo.add(botonIniciar, c);

        return fondo;
    }

    //cambia el contenido de la MISMA ventana: de la bienvenida a la interfaz de seleccion y batalla
    private static void mostrarJuego(JFrame frame)
    {
        frame.setContentPane(new PokeApiGUI().mainPanel);
        frame.revalidate();
        frame.pack();
        frame.setMinimumSize(frame.getSize());   //no se puede achicar tanto como para tapar Fight!
        frame.setLocationRelativeTo(null);
    }

    public static void main(String[] args)
    {
        //la ventana se crea en el hilo de Swing
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                final JFrame frame = new JFrame("PokeApi");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                //primero aparece la bienvenida; INICIAR reutiliza esta misma ventana
                frame.setContentPane(crearPantallaBienvenida(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mostrarJuego(frame);
                    }
                }));
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
