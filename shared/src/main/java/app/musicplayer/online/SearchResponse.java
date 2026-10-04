package app.musicplayer.online;

import app.musicplayer.util.JsonSupport;

/** A malformed or rejected HTTP-200 response is a source failure, not a cacheable empty search. */
final class SearchResponse {
    private SearchResponse() { }
    static void requireObject(String json) { requireObject(json, '"'); }
    static void requireObject(String json, char quotationMark) {
        if (!JsonSupport.isCompleteObject(json, quotationMark)) throw new IllegalStateException("Incomplete search response");
    }
    static void requireSuccess(String json, String field, String expected) {
        String status = JsonSupport.numberValue(json, field);
        if (status != null && !expected.equals(status)) throw new IllegalStateException("Source rejected search: " + field + "=" + status);
    }
    static String array(String json, String field) {
        String array = JsonSupport.arrayValue(json, field);
        if (array == null) throw new IllegalStateException("Missing search results: " + field);
        return array;
    }
}
