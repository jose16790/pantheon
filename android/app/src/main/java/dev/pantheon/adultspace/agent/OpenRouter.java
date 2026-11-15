package dev.pantheon.adultspace.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenRouter, for two kinds of model:
 *   Jev (TypeSafe's decision model): typed decisions (choice / noul / score) with probabilities, ~0.25 s, through
 *       the Decisions API. It writes no text.
 *   A small, fast chat model (DeepSeek V4.1 Flash; Qwen 3.7 Flash if that fails): understanding free text and writing
 *       short reasons, always as JSON.
 */
final class OpenRouter {
    static final String JEV = "typesafe/jev-1.14";                          // pinned: its probabilities stay stable
    static final String CHAT = "deepseek/deepseek-v4.1-flash-1017";         // pinned snapshot
    static final String CHAT_BACKUP = "qwen/qwen3.7-flash";

    private final Context ctx;
    OpenRouter(Context c) { this.ctx = c.getApplicationContext(); }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + Keys.openRouter(ctx));
        h.put("HTTP-Referer", "https://github.com/jose16790/pantheon-tv-of-the-gods");
        h.put("X-Title", "Pantheon");
        return h;
    }

    /** Jev: state (any JSON) and questions ({name: {type, instructions, criteria}}) -> answers ({name: {...}}). */
    JSONObject decide(JSONObject state, JSONObject questions, int timeoutMs) throws Exception {
        JSONObject body = new JSONObject().put("model", JEV).put("state", state).put("questions", questions);
        return Http.post("https://openrouter.ai/api/alpha/decisions", headers(), body, timeoutMs).getJSONObject("answers");
    }

    /** The chat model, answering with one JSON object (the backup model if the first fails). */
    JSONObject json(String system, String user, int maxTokens, int timeoutMs) throws Exception {
        try { return chat(CHAT, system, user, maxTokens, timeoutMs); }
        catch (Exception first) {
            android.util.Log.w(Agent.TAG, CHAT + " failed (" + first.getMessage() + "); trying " + CHAT_BACKUP);
            return chat(CHAT_BACKUP, system, user, maxTokens, timeoutMs);
        }
    }

    /**
     * A spoken answer in plain text. With web on, OpenRouter searches the web first (its "web" plugin: ~4 results, about
     * a cent) and the model answers from what it found. Links and markdown are taken out: this is for the ear.
     */
    String talk(String system, String user, boolean web, int maxTokens, int timeoutMs) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", CHAT).put("max_tokens", maxTokens).put("temperature", 0.6)
                .put("reasoning", new JSONObject().put("enabled", false))
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", system))
                        .put(new JSONObject().put("role", "user").put("content", user)));
        if (web) body.put("plugins", new JSONArray().put(new JSONObject().put("id", "web").put("max_results", 4)));
        JSONObject r = Http.post("https://openrouter.ai/api/v1/chat/completions", headers(), body, timeoutMs);
        return clean(r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", ""));
    }

    /** A chat answer made fit to speak: citations, links and markdown out; performance cues ([chuckles]) kept. */
    static String clean(String t) {
        return t.replaceAll(",?\\s*\\[[^\\]]*\\]\\((?:https?://)[^)]*\\)", "")       // [source](https://…) citations
                .replaceAll("\\(?https?://\\S+\\)?", "")
                .replaceAll("(?i)[,;]?\\s+(?:per|via|according to|from|source:?)\\s*(?=[.,;!?]|$)", "")   // "per [link]" leftovers
                .replaceAll("[*_#`]", "").replaceAll("[\\s\\p{Z}\\u200B]+([.,!?;:])", "$1").replaceAll("\\s{2,}", " ").trim();
    }

    private JSONObject chat(String model, String system, String user, int maxTokens, int timeoutMs) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("temperature", 0.4)
                .put("response_format", new JSONObject().put("type", "json_object"))
                .put("reasoning", new JSONObject().put("enabled", false))               // speed over deliberation
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", system))
                        .put(new JSONObject().put("role", "user").put("content", user)));
        JSONObject r = Http.post("https://openrouter.ai/api/v1/chat/completions", headers(), body, timeoutMs);
        String text = r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "").trim();
        if (text.startsWith("```")) text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        return new JSONObject(text);
    }
}
