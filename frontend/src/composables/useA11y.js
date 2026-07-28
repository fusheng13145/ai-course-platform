/**
 * 无障碍（a11y）辅助组合式函数。
 *
 * 项目早期页面几乎未使用 ARIA 属性，导致读屏软件与键盘导航体验较差。
 * 本组合函数提供一组语义化绑定生成器，供各视图低门槛补全 a11y 属性。
 */

/**
 * 生成表单输入控件的 ARIA 属性。
 *
 * @param {string} label 控件可读标签
 * @param {boolean} [required=false] 是否必填
 * @param {string} [errorId] 关联的错误提示元素 id
 * @returns {Record<string, string>} 可直接绑定到 el-input / input 的 attribute 对象
 */
export function inputA11y(label, required = false, errorId = '') {
  const attrs = {
    'aria-label': label,
  }
  if (required) {
    attrs['aria-required'] = 'true'
  }
  if (errorId) {
    attrs['aria-errormessage'] = errorId
    attrs['aria-invalid'] = 'false'
  }
  return attrs
}

/**
 * 生成按钮的 ARIA 属性。
 *
 * @param {string} label 按钮操作说明
 * @param {boolean} [loading=false] 是否处于加载状态
 * @returns {Record<string, string>} 可绑定到 el-button 的 attribute 对象
 */
export function buttonA11y(label, loading = false) {
  return {
    'aria-label': label,
    'aria-busy': loading ? 'true' : 'false',
  }
}

/**
 * 生成区域（landmark）容器的 ARIA 属性。
 *
 * @param {string} label 区域说明
 * @param {'main'|'navigation'|'search'|'complementary'|'contentinfo'} [role='main'] 区域角色
 * @returns {Record<string, string>} 可绑定到 div/section 的 attribute 对象
 */
export function landmarkA11y(label, role = 'main') {
  return {
    role,
    'aria-label': label,
  }
}

/**
 * 生成实时区域属性，用于动态提示/加载状态的无障碍朗读。
 *
 * @param {'polite'|'assertive'} [politeness='polite'] 朗读优先级
 * @returns {Record<string, string>}
 */
export function liveRegionA11y(politeness = 'polite') {
  return {
    'aria-live': politeness,
    'aria-atomic': 'true',
  }
}
