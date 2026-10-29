/**
 * 全局页 special → Body 组件的命名约定：`users` → `./UsersBody.vue`。
 *
 * 后续页面任务只需新增 `<Special>Body.vue`，不必再改 V2GlobalPage；文件是否已落地
 * 由 specialBodyLoader 用 import.meta.glob 判定，未落地时返回 null，页面回落示例块。
 */
const BODY_MODULES = import.meta.glob('./*Body.vue')

/** 只接受小写开头的驼峰标识符，避免把空值、路径穿越或任意字符串拼成路径。 */
const SPECIAL_NAME = /^[a-z][A-Za-z0-9]*$/

/** 按约定拼出 Body 路径；special 非法或为空时返回 null。 */
export function specialBodyFile(special?: string): string | null {
  if (!special || !SPECIAL_NAME.test(special)) return null
  return `./${special.charAt(0).toUpperCase()}${special.slice(1)}Body.vue`
}

/** 只有约定路径命中真实文件才返回懒加载器，否则 null（调用方回落示例块）。 */
export function specialBodyLoader(special?: string): (() => Promise<unknown>) | null {
  const file = specialBodyFile(special)
  if (!file) return null
  return BODY_MODULES[file] ?? null
}
