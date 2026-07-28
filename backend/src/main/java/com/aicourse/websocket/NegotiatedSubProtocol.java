package com.aicourse.websocket;

/**
 * 握手段协议暂存器。
 *
 * <p>RFC 6455 要求：若客户端在握手时声明了 {@code Sec-WebSocket-Protocol}，服务端必须在响应中
 * 回显<strong>同一个值</strong>，否则浏览器会直接关闭连接。Spring 在握手升级阶段通过
 * {@code WebSocketHandler.getSubProtocols()} 决定回显值，而我们通过 JWT 校验出的令牌位于
 * {@code HandshakeInterceptor.beforeHandshake} 中——二者处于同一次请求、但不同的调用点。</p>
 *
 * <p>此处用 ThreadLocal 在同线程内传递协商出的子协议值，供 {@code getSubProtocols()} 回显。
 * 读取后立即清除，避免容器线程池复用导致的值泄漏。</p>
 */
final class NegotiatedSubProtocol {

    private static final ThreadLocal<String> PROTOCOL = new ThreadLocal<>();

    private NegotiatedSubProtocol() {
    }

    static void set(String protocol) {
        PROTOCOL.set(protocol);
    }

    static String get() {
        return PROTOCOL.get();
    }

    static void clear() {
        PROTOCOL.remove();
    }
}
