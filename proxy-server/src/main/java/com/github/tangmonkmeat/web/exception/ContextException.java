package com.github.tangmonkmeat.web.exception;

/**
 * Description:
 * 内容异常
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午8:51
 */
public class ContextException extends RuntimeException{

    /**
     * 异常状态码
     */
    private int code;

    public ContextException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ContextException(int code) {
        super();
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
