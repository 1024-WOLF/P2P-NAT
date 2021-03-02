package com.github.tangmonkmeat.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * Description:
 * JSON与POJO转换工具类.
 * @author zwl
 * @version 1.0
 * @date 2021/2/21 23:23
 */
public class JsonUtil {

    /**
     * json 字符串 转化为 指定类型的 object
     *
     * @param json json 字符串
     * @param typeToken {@link TypeToken}
     * @param <T> object 的具体类型
     * @return 如果 json字符串的语法有问题，返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T json2Object(String json, TypeToken<T> typeToken){
        try {
            Gson gson = new Gson();
            return (T) gson.fromJson(json, typeToken.getType());
        } catch (JsonSyntaxException ignored) {
        }
        return null;
    }

    /**
     * java 对象转化为 json字符串
     *
     * @param obj java对象
     * @return json字符串
     */
    public static String object2Json(Object obj){
        Gson gson = new Gson();
        return gson.toJson(obj);
    }

}
