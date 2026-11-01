package app.musicplayer.online;

import app.musicplayer.util.JsonSupport;

/**
 * 来源搜索响应的结构校验。区分接口失败、缺字段与合法空数组，保证错误响应不会作为成功搜索进入缓存。 A malformed or rejected HTTP-200 response is a
 * source failure, not a cacheable empty search.
 */
final class SearchResponse {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private SearchResponse() {}

    /** 取得并校验响应对象，缺少必要结构时报告解析错误。 */
    static void requireObject(String json) {
        requireObject(json, '"');
    }

    /** 取得并校验响应对象，缺少必要结构时报告解析错误。 */
    static void requireObject(String json, char quotationMark) {
        if (!JsonSupport.isCompleteObject(json, quotationMark))
            throw new IllegalStateException("Incomplete search response");
    }

    /** 检查接口或 HTTP 状态是否表示成功，错误响应交给调用方处理。 */
    static void requireSuccess(String json, String field, String expected) {
        String status = JsonSupport.numberValue(json, field);
        if (status != null && !expected.equals(status))
            throw new IllegalStateException("Source rejected search: " + field + "=" + status);
    }

    /** 读取数组并验证其结构，允许接口约定的合法空数组。 */
    static String array(String json, String field) {
        String array = JsonSupport.arrayValue(json, field);
        if (array == null) throw new IllegalStateException("Missing search results: " + field);
        return array;
    }
}
