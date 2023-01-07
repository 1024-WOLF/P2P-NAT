package org.github.$1024wolf.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description:
 * 包装类型转换工具类
 * @author zwl
 * @version 1.0
 * @date 2021/2/21 23:35
 */
public class LangUtil {

    private static Logger logger = LoggerFactory.getLogger(LangUtil.class);

    public static Boolean parseBoolean(Object value) {
        if (value != null) {
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                return Boolean.valueOf((String) value);
            }
        }
        return null;
    }


    public static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value != null) {
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                try {
                    return Boolean.parseBoolean((String) value);
                } catch (Exception e) {
                    logger.warn("parse boolean value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /**
     * int解析方法，可传入Integer或String值
     *
     * @param value Integer或String值
     * @return Integer 返回类型
     */
    public static Integer parseInt(Object value) {
        if (value != null) {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                return Integer.valueOf((String) value);
            }
        }
        return null;
    }

    public static Integer parseInt(Object value, Integer defaultValue) {
        if (value != null) {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                try {
                    return Integer.valueOf((String) value);
                } catch (Exception e) {
                    logger.warn("parse Integer value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /***
     * long解析方法，可传入Long或String值
     *
     * @param value Integer或String值
     * @return Long 返回类型
     */
    public static Long parseLong(Object value) {
        if (value != null) {
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof String) {
                return Long.valueOf((String) value);
            }
        }
        return null;
    }

    public static Long parseLong(Object value, Long defaultValue) {
        if (value != null) {
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof String) {
                try {
                    return Long.valueOf((String) value);
                } catch (NumberFormatException e) {
                    logger.warn("parse Long value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /**
     * Double解析方法，可传入Double或String值
     *
     * @param value Double或String值
     * @return Double 返回类型
     */
    public static Double parseDouble(Object value) {
        if (value != null) {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof String) {
                return Double.valueOf((String) value);
            }
        }
        return null;
    }

    /**
     * Double解析方法，可传入Double或String值
     *
     * @param value Double或String值
     * @return Double 返回类型
     */
    public static Double parseDouble(Object value, Double defaultValue) {
        if (value != null) {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof String) {
                try {
                    return Double.valueOf((String) value);
                } catch (NumberFormatException e) {
                    logger.warn("parse Double value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

}