import { describe, expect, it } from 'vitest'
import { useStudentExamSheet } from '../useStudentExamSheet'

function sheetWith(types: string[]) {
  const sheet = useStudentExamSheet()
  sheet.questions.value = types.map((type, index) => ({ id: 900 + index, type }))
  return sheet
}

describe('作答判定：空串不算作答', () => {
  it('单选题：未答 / 空串 / 纯空白都不算，选了字母才算', () => {
    const sheet = sheetWith(['SINGLE_CHOICE'])
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.answers.value[0] = ''
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.answers.value[0] = '   '
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.answers.value[0] = 'A'
    expect(sheet.isQuestionAnswered(0)).toBe(true)
  })

  it('判断题：false 是有效作答，不能被当成未答', () => {
    const sheet = sheetWith(['TRUE_FALSE'])
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.answers.value[0] = false
    expect(sheet.isQuestionAnswered(0)).toBe(true)
  })

  it('多选题：空数组不算，至少一个选项才算', () => {
    const sheet = sheetWith(['MULTIPLE_CHOICE'])
    sheet.multiAnswers.value[0] = []
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.multiAnswers.value[0] = ['A']
    expect(sheet.isQuestionAnswered(0)).toBe(true)
  })

  it('简答题：空串与纯空白不算', () => {
    const sheet = sheetWith(['SHORT_ANSWER'])
    sheet.textAnswers.value[0] = ''
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.textAnswers.value[0] = '  '
    expect(sheet.isQuestionAnswered(0)).toBe(false)
    sheet.textAnswers.value[0] = '网络层负责路由'
    expect(sheet.isQuestionAnswered(0)).toBe(true)
  })

  it('自动保存回填的空串不会被算进「已作答」', () => {
    const sheet = sheetWith(['SINGLE_CHOICE', 'SINGLE_CHOICE'])
    sheet.restoreAnswers(sheet.parseSavedAnswers('{"900": "A", "901": ""}'))
    expect(sheet.isQuestionAnswered(0)).toBe(true)
    expect(sheet.isQuestionAnswered(1)).toBe(false)
  })
})
