/**
 * SQL 代码块解析
 *
 * 从 AI 回复正文里提取/剥离 ```sql ... ``` 代码块。
 *
 * ⚠️ 两条红线：
 * 1. 提取与剥离必须覆盖**同一批**代码块。原实现提取只取第一块（正则无 g）、
 *    剥离却删全部块（正则带 g）——模型一旦输出两个 SQL 块，第二个既不高亮、
 *    又被从正文里删掉，用户永远看不到。
 * 2. 每次调用都用**新建的 RegExp 实例**。带 g 标志的正则对象会保留 lastIndex，
 *    复用同一个实例会漏匹配（同类坑见 PITFALLS 坑 31）。
 */

/** 代码块匹配源（不含标志）。提取与剥离共用同一份，从根上保证覆盖范围一致 */
const SQL_BLOCK_SOURCE = '```\\s*sql\\s*([\\s\\S]*?)```'

/** 提取全部 SQL 代码块，返回去除首尾空白的 SQL 原文数组 */
export const extractSqlBlocks = (content) => {
  if (!content) return []
  return [...content.matchAll(new RegExp(SQL_BLOCK_SOURCE, 'gi'))].map((m) => m[1].trim())
}

/** 去掉全部 SQL 代码块，返回正文 */
export const stripSqlBlocks = (content) => {
  return (content || '').replace(new RegExp(SQL_BLOCK_SOURCE, 'gi'), '').trim()
}
