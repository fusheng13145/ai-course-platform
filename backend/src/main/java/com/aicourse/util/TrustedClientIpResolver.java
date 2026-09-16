package com.aicourse.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 安全决策用的「客户端真实 IP」解析器。
 *
 * <p><b>职责边界</b>：本类只服务于<b>安全决策</b>（登录失败节流键、限流键等），必须从<b>可信来源</b>
 * 取 IP，绝不能让客户端可控的请求头污染计数键——否则攻击者可逐请求换头制造「全新键」从而绕过限流
 * （本仓库已确证的 🔴 上线阻断级缺陷：{@code auth:login:fail:<IP>:<account>} 的 IP 若可被伪造，
 * 计数永远到不了阈值，可无限密码爆破）。<b>审计日志</b>用到的 IP 仍走 {@link IpUtil}（多层代理真实 IP），
 * 二者用途不同，刻意区分。</p>
 *
 * <p><b>信任链（本部署形态下的知识沉淀）</b>：</p>
 * <ol>
 *   <li>本部署为<b>单跳 nginx</b> 反代；nginx 以 {@code proxy_set_header X-Real-IP $remote_addr;}
 *       与 {@code proxy_set_header X-Forwarded-For $remote_addr;} 进行<b>覆写</b>语义设置，
 *       即反代会用 TCP 对端地址<b>覆盖</b>该头，因此二者在本部署中均为可信。</li>
 *   <li>应用侧配置了 {@code server.forward-headers-strategy: framework}（Spring 6.2.x 行为已反编译核实）：
 *       {@code ForwardedHeaderFilter} 会<b>移除</b> {@code X-Forwarded-For} 等转发头，使应用内
 *       {@code request.getHeader("X-Forwarded-For")} 读不到；同时该过滤器以 XFF 的<b>最左端</b>
 *       覆写 {@code request.getRemoteAddr()}（即 remoteAddr 也被客户端可控值劫持）。</li>
 *   <li>关键点：{@code X-Real-IP} <b>不在</b> {@code ForwardedHeaderFilter} 的移除名单中，而 nginx 又
 *       是以<b>覆写</b>语义设置它 → 因此 {@code X-Real-IP} 是本部署中<b>唯一既可信（反代覆写）、
 *       又能穿透 Spring 过滤器被应用读到</b>的客户端 IP 来源。</li>
 * </ol>
 *
 * <p><b>取值策略（{@link #resolve}）</b>：</p>
 * <ul>
 *   <li>当 {@code trust-proxy-header=false}（<b>默认，直连 / 本地开发</b>）：不信任任何代理头，
 *       直接以 {@code request.getRemoteAddr()} 为准——此时 remoteAddr 就是真实的 TCP 对端。</li>
 *   <li>当 {@code trust-proxy-header=true}（<b>生产，置于可信 nginx 之后</b>）：优先取 {@code X-Real-IP}；
 *       仅当该头缺失 / 空白 / {@code unknown} / 超长（>45，超 IPv6 上限）时才回退 {@code getRemoteAddr()}。</li>
 * </ul>
 *
 * <p><b>⚠ 安全警示</b>：{@code trust-proxy-header} 开关<b>禁止</b>在无受信反代（直连暴露 / 客户端可直接
 * 触达应用）时开启。否则攻击者可直接伪造 {@code X-Real-IP} 头，等于把限流键的 IP 维度重新交还攻击者，
 * 缺陷原样复活。生产固定置于可信 nginx 之后，参见 {@code frontend/nginx.conf}。</p>
 */
@Component
public class TrustedClientIpResolver {

    /** 是否信任反代覆写的 X-Real-IP 头（生产置于可信反代之后时开启）。默认 false = 以 TCP 对端地址为准。 */
    @Value("${app.security.trust-proxy-header:false}")
    private boolean trustProxyHeader;

    /** IPv6 文本表示上限长度（含分组冒号），超长视为异常输入直接回退。 */
    private static final int MAX_IP_LENGTH = 45;

    /**
     * 解析用于安全决策的客户端 IP。
     * @param request 当前 HTTP 请求
     * @return 可信客户端 IP 字符串
     */
    public String resolve(HttpServletRequest request) {
        // 仅当显式信任受信反代、且反代覆写的 X-Real-IP 可用时，才采用该头；否则一律回退 TCP 对端地址。
        if (trustProxyHeader) {
            String realIp = request.getHeader("X-Real-IP");
            if (isUsable(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 判定反代头值是否「可用」：非空、非空白、非 {@code unknown}（不区分大小写）、长度 ≤ 45。
     * 防御异常/恶意输入（空串、unknown、超长伪造串），任一不达标即视为不可用。
     */
    private boolean isUsable(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("unknown")) {
            return false;
        }
        return trimmed.length() <= MAX_IP_LENGTH;
    }
}
