package com.aicourse.service;

import com.aicourse.common.PagedResponse;
import com.aicourse.vo.BannedAuthorVO;
import com.aicourse.vo.ModerationPostVO;
import com.aicourse.vo.ModerationResourceVO;

import java.util.List;
import java.util.Map;

/**
 * 内容审核与违规处理服务（角色矩阵 ③）。
 *
 * <p>覆盖两处内容面：**讨论回复**（{@code discussion_posts}）与**平台全部资源**（{@code resources}）。
 * 处置动作：HIDE（隐藏，读路径过滤、可恢复）/ RESTORE（恢复）/ DELETE（物理删，不可恢复）；
 * 并可同时**封禁作者**。</p>
 *
 * <p>⚠️ 三条口径（与页面、手册一致）：
 * ① 封禁 = 复用 {@code users.is_active=0}（同一停用开关，不另造第二套封禁语义），并用
 *    {@code banned_at} 留痕，"经审核封禁"与"其它原因停用"在列表里天然分开；
 * ② DELETE 是**物理删**（讨论回复按其父级外键会级联删除楼中楼），不可恢复——原因只进操作日志；
 * ③ 处置动作走既有 {@code OperationLogAspect} 自动留痕，不另建审核动作表。</p>
 */
public interface ModerationService {

    /** 讨论回复列表（可按审核状态与关键词过滤）。 */
    PagedResponse<ModerationPostVO> listPosts(int page, int size, String status, String keyword);

    /** 教学资源列表（同上；"平台全部资源"口径）。 */
    PagedResponse<ModerationResourceVO> listResources(int page, int size, String status, String keyword);

    /** 对讨论回复执行处置（HIDE / RESTORE / DELETE），可选同时封禁作者。 */
    Map<String, Object> applyPostAction(Long postId, String action, String reason, boolean banAuthor, Long adminId);

    /** 对资源执行处置（同上）。 */
    Map<String, Object> applyResourceAction(Long resourceId, String action, String reason, boolean banAuthor, Long adminId);

    /** 经审核封禁的作者列表（{@code banned_at IS NOT NULL}）。 */
    List<BannedAuthorVO> listBannedAuthors();

    /** 封禁作者（复用 is_active=0 并留痕）；返回处置结果。 */
    Map<String, Object> banAuthor(Long userId, String reason, Long adminId);

    /** 解除封禁（is_active=1 并清除留痕）；返回是否真的解除了一个经审核封禁的账号。 */
    boolean unbanAuthor(Long userId);
}
