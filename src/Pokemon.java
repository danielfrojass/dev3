import javax.swing.ImageIcon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelo de datos de un Pokemon. No conoce ni la red ni la interfaz:
 * solo guarda sus estadisticas y controla su HP actual.
 */
public class Pokemon
{
    private final String nombre;
    private final ImageIcon sprite;      // puede ser null si la API no trae imagen o no se pudo descargar
    private final List<String> tipos;
    private final int hpMaximo;
    private final int attack;
    private final int defense;
    private final int speed;
    private int hpActual;

    public Pokemon(String nombre, ImageIcon sprite, List<String> tipos,
                   int hpMaximo, int attack, int defense, int speed)
    {
        this.nombre = nombre;
        this.sprite = sprite;
        this.tipos = Collections.unmodifiableList(new ArrayList<>(tipos));
        this.hpMaximo = hpMaximo;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.hpActual = hpMaximo;
    }

    /**
     * Resta HP. El HP actual nunca baja de 0 (ni sube si el dano recibido es negativo).
     */
    public void recibirDano(int dano)
    {
        hpActual = Math.max(0, hpActual - Math.max(0, dano));
    }

    public boolean estaDerrotado()
    {
        return hpActual == 0;
    }

    /**
     * Devuelve el HP actual al maximo (util para empezar una nueva batalla).
     */
    public void restaurarHp()
    {
        hpActual = hpMaximo;
    }

    public String getNombre()
    {
        return nombre;
    }

    public ImageIcon getSprite()
    {
        return sprite;
    }

    public List<String> getTipos()
    {
        return tipos;
    }

    public int getHpMaximo()
    {
        return hpMaximo;
    }

    public int getAttack()
    {
        return attack;
    }

    public int getDefense()
    {
        return defense;
    }

    public int getSpeed()
    {
        return speed;
    }

    public int getHpActual()
    {
        return hpActual;
    }
}
