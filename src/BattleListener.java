/**
 * Escucha los eventos de una batalla. Quien quiera enterarse de lo que ocurre
 * (por ejemplo la interfaz grafica) implementa esta interfaz.
 * Battle no sabe nada de quien la implementa.
 */
public interface BattleListener
{
    /**
     * Se produjo un ataque.
     *
     * @param attacker nombre del Pokemon que ataca
     * @param defender nombre del Pokemon que recibe el ataque
     * @param damage   dano aplicado (minimo 1)
     * @param critical true si fue golpe critico
     * @param modifier modificador de efectividad de tipos: 0.7, 1.0 o 1.3
     */
    void onTurn(String attacker, String defender, int damage, boolean critical, double modifier);

    /**
     * Cambio el HP de un Pokemon (nunca es negativo).
     *
     * @param pokemon  nombre del Pokemon que recibio dano
     * @param hpActual HP que le queda
     */
    void onHpChanged(String pokemon, int hpActual);

    /**
     * La batalla termino porque un Pokemon llego a 0 HP.
     *
     * @param winner nombre del Pokemon ganador
     */
    void onBattleEnded(String winner);
}
