/**
 * 题型契约常量（Question.type 的唯一展示出口，跨宿主共享）。
 *
 * ⚠️ 契约更正（2026-10-26，QA 终验批 2 实查裁定，替代设计 F3 原前提）：
 * 题库 Question.type 实际值是 ***_CHOICE 风格**，与 AutoComposeDTO 注释一致、
 * 与设计 F3「注释是误导」的判断相反。证据链（QA 实查 courseId=4007 全部 68 题 + 后端源码）：
 * - 实库分布：SINGLE_CHOICE(38)/MULTIPLE_CHOICE(10)/TRUE_FALSE(8)/FILL_BLANK(7)/ESSAY(5)；
 * - V2__align_column_comments.sql 列注释与 V5__rich_seed_data.sql 映射同口径（'essay'→'ESSAY'）；
 * - QuestionVO/QuestionGenerateDTO 的 allowableValues 均无 CODE（CODE 仅存在于
 *   SignIn/Plagiarism/Task 域），实库亦无 CODE 题，故本契约不含 CODE；
 * - 后端 ExamPaperServiceImpl 的 typeRules 与 difficultyRatio 均**精确 key 匹配**
 *   （groupingBy 原值），错 key 直接 400「没有匹配到符合条件的题目」。
 *
 * 另：difficulty 实库为**小写** easy/medium/hard（见 CesAutoComposeModal），与本文件无关。
 * 所有题型展示/筛选/typeRules 一律走本模块，禁止组件里手写魔法字符串。
 */

export interface QuestionTypeOption {
  /** 题库 Question.type 实际值（同时是 AutoComposeDTO.typeRules 的 key） */
  value: string
  /** 中文标签 */
  label: string
  /** el-tag type 色板 */
  tagType: 'primary' | 'success' | 'warning' | 'info' | 'danger' | ''
}

/** 题型选项（顺序即筛选/组卷表单的展示顺序；key 与实库 Question.type 严格一致） */
export const QUESTION_TYPE_OPTIONS: readonly QuestionTypeOption[] = [
  { value: 'SINGLE_CHOICE', label: '单选题', tagType: 'primary' },
  { value: 'MULTIPLE_CHOICE', label: '多选题', tagType: 'success' },
  { value: 'TRUE_FALSE', label: '判断题', tagType: 'warning' },
  { value: 'FILL_BLANK', label: '填空题', tagType: 'info' },
  { value: 'ESSAY', label: '简答题', tagType: '' },
]

/**
 * 读取题型中文标签；未知/空值降级为「未知」，不抛异常。
 * @param type 题库 Question.type 实际值
 */
export function questionTypeLabel(type?: string | null): string {
  if (!type) return '未知'
  const key = String(type).toUpperCase()
  return QUESTION_TYPE_OPTIONS.find((o) => o.value === key)?.label || String(type)
}

/**
 * 读取题型标签色；未知/空值降级为 info。
 * @param type 题库 Question.type 实际值
 */
export function questionTypeTag(type?: string | null): string {
  if (!type) return 'info'
  const key = String(type).toUpperCase()
  return QUESTION_TYPE_OPTIONS.find((o) => o.value === key)?.tagType ?? 'info'
}

/* ------------------------------------------------------------------ *
 * 难度契约（Question.difficulty，实库为小写 easy/medium/hard——QA 实查 +
 * 后端按原值精确分组，同 typeRules 一条规矩）
 * ------------------------------------------------------------------ */

export interface QuestionDifficultyOption {
  /** 题库 Question.difficulty 实际值（小写） */
  value: string
  /** 中文标签 */
  label: string
  /** el-tag type 色板 */
  tagType: 'success' | 'warning' | 'danger'
}

/** 难度选项（key 与实库 Question.difficulty 严格一致） */
export const QUESTION_DIFFICULTY_OPTIONS: readonly QuestionDifficultyOption[] = [
  { value: 'easy', label: '简单', tagType: 'success' },
  { value: 'medium', label: '中等', tagType: 'warning' },
  { value: 'hard', label: '困难', tagType: 'danger' },
]

/**
 * 读取难度中文标签；未知/空值降级为「未知」，不抛异常。
 * 查找大小写不敏感（历史数据若存过大写 EASY 也能显示），但**写侧必须用小写原值**。
 * @param difficulty 题库 Question.difficulty 实际值
 */
export function questionDifficultyLabel(difficulty?: string | null): string {
  if (!difficulty) return '未知'
  const key = String(difficulty).toLowerCase()
  return QUESTION_DIFFICULTY_OPTIONS.find((o) => o.value === key)?.label || String(difficulty)
}

/**
 * 读取难度标签色；未知/空值降级为 info。
 * @param difficulty 题库 Question.difficulty 实际值
 */
export function questionDifficultyTag(difficulty?: string | null): string {
  if (!difficulty) return 'info'
  const key = String(difficulty).toLowerCase()
  return QUESTION_DIFFICULTY_OPTIONS.find((o) => o.value === key)?.tagType ?? 'info'
}
