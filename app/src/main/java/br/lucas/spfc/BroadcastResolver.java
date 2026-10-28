package br.lucas.spfc;

import org.json.JSONObject;

import java.util.List;

/** De onde sai o canal de TV de uma partida. A ESPN muitas vezes manda a lista vazia. */
interface BroadcastResolver {
    List<String> canais(JSONObject competicao);
}
