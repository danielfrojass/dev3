import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.ImageIcon;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cliente de PokeAPI. Se encarga de la peticion HTTP y del parseo del JSON,
 * y entrega un objeto Pokemon listo para usar. No conoce la interfaz grafica.
 */
public class PokeApiClient
{
    private static final String URL_POKEMON = "https://pokeapi.co/api/v2/pokemon/";

    //un solo cliente http reutilizado para todas las peticiones
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Consulta un Pokemon por nombre.
     *
     * @return el Pokemon, o Optional.empty() si la API responde 404 (no existe)
     * @throws IllegalArgumentException si el nombre esta vacio
     * @throws IOException              error de red, respuesta inesperada o JSON invalido
     */
    public Optional<Pokemon> obtenerPokemon(String nombre) throws IOException
    {
        //normalizamos: sin espacios alrededor y en minusculas
        String nombreNormalizado = nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);

        if (nombreNormalizado.isEmpty())
        {
            throw new IllegalArgumentException("Ingrese el nombre de un pokemon");
        }

        //se crea un objeto de tipo request para realizar la peticion
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_POKEMON + URLEncoder.encode(nombreNormalizado, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response;
        try
        {
            //ejecutamos la solicitud
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("La consulta fue interrumpida");
        }

        //404 = el pokemon no existe (no es un error de red)
        if (response.statusCode() == 404)
        {
            return Optional.empty();
        }

        //cualquier otro codigo distinto de 200 es una falla del servicio
        if (response.statusCode() != 200)
        {
            throw new IOException("PokeAPI respondio con el codigo HTTP " + response.statusCode());
        }

        try
        {
            return Optional.of(construirPokemon(new JSONObject(response.body())));
        }
        catch (JSONException e)
        {
            throw new IOException("Respuesta invalida de PokeAPI: " + e.getMessage(), e);
        }
    }

    //convierte el JSON de la API en un objeto Pokemon
    private Pokemon construirPokemon(JSONObject json)
    {
        String nombre = json.getString("name");

        //estadisticas: se buscan por nombre dentro del array stats
        int hp = 0, attack = 0, defense = 0, speed = 0;
        JSONArray stats = json.getJSONArray("stats");
        for (int i = 0; i < stats.length(); i++)
        {
            JSONObject statJson = stats.getJSONObject(i);
            String nombreStat = statJson.getJSONObject("stat").getString("name");
            int valor = statJson.getInt("base_stat");

            if (nombreStat.equals("hp"))
                hp = valor;
            else if (nombreStat.equals("attack"))
                attack = valor;
            else if (nombreStat.equals("defense"))
                defense = valor;
            else if (nombreStat.equals("speed"))
                speed = valor;
        }

        //tipos: types[i].type.name
        List<String> tipos = new ArrayList<>();
        JSONArray typesJson = json.getJSONArray("types");
        for (int i = 0; i < typesJson.length(); i++)
        {
            tipos.add(typesJson.getJSONObject(i).getJSONObject("type").getString("name"));
        }

        //sprite frontal: puede venir null en el JSON, en ese caso queda sin imagen
        JSONObject spritesJson = json.optJSONObject("sprites");
        String urlSprite = spritesJson == null ? null : spritesJson.optString("front_default", null);

        return new Pokemon(nombre, descargarSprite(urlSprite), tipos, hp, attack, defense, speed);
    }

    //descarga la imagen con el mismo HttpClient; si algo falla devuelve null (el pokemon se carga igual)
    private ImageIcon descargarSprite(String urlSprite)
    {
        if (urlSprite == null || urlSprite.isEmpty())
        {
            return null;
        }

        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlSprite))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200)
            {
                return new ImageIcon(response.body());
            }
        }
        catch (IOException | IllegalArgumentException e)
        {
            //sin sprite: no se considera un error fatal
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        return null;
    }
}
