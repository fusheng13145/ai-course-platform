import request from './request'

/**
 * 全站聚合搜索（知识点/题目/课程/笔记）
 * @param {string} keyword 搜索关键字
 * @param {string[]} [types] 检索类型子集，默认四类全搜
 * @param {number} [limit] 返回条数上限（1-20）
 * @returns {Promise<{type,id,title,snippet,score}[]>}
 */
export const searchAll = (
  keyword,
  types = ['knowledge', 'question', 'course', 'note'],
  limit = 8,
) =>
  request
    .get('/search', {
      params: {
        keyword,
        types: types.join(','),
        limit,
      },
    })
    .then((res) => (Array.isArray(res?.data) ? res.data : []))

export default { searchAll }
