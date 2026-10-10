/**
 * SQL 代码块解析回归断言
 *
 * 跑法：node --test tests/   （Node 内置测试器，无需新增依赖）
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { extractSqlBlocks, stripSqlBlocks } from '../src/utils/sqlBlock.js'

test('单个 SQL 块：提取与剥离覆盖范围一致', () => {
  const content = '说明文字\n```sql\nSELECT 1\n```\n结尾'
  assert.deepEqual(extractSqlBlocks(content), ['SELECT 1'])
  assert.equal(stripSqlBlocks(content), '说明文字\n\n结尾')
})

test('多个 SQL 块：必须全部提取，不能丢内容', () => {
  const content = 'A\n```sql\nSELECT 1\n```\nB\n```sql\nSELECT 2\n```\nC'
  assert.deepEqual(extractSqlBlocks(content), ['SELECT 1', 'SELECT 2'])
  assert.ok(!stripSqlBlocks(content).includes('SELECT'), '正文不应残留任何 SQL')
})

test('无 SQL 块时原样返回', () => {
  assert.deepEqual(extractSqlBlocks('纯文本'), [])
  assert.equal(stripSqlBlocks('纯文本'), '纯文本')
})

test('空输入不炸', () => {
  assert.deepEqual(extractSqlBlocks(''), [])
  assert.deepEqual(extractSqlBlocks(null), [])
  assert.equal(stripSqlBlocks(''), '')
  assert.equal(stripSqlBlocks(null), '')
})

test('大小写与多余空格容错', () => {
  assert.deepEqual(extractSqlBlocks('```SQL\nSELECT 1\n```'), ['SELECT 1'])
  assert.deepEqual(extractSqlBlocks('```sql  \nSELECT 2\n```'), ['SELECT 2'])
})

test('重复调用结果稳定（防正则 lastIndex 残留）', () => {
  const content = '```sql\nSELECT 1\n```\n```sql\nSELECT 2\n```'
  const first = extractSqlBlocks(content)
  assert.deepEqual(extractSqlBlocks(content), first)
  assert.deepEqual(extractSqlBlocks(content), first)
  assert.deepEqual(stripSqlBlocks(content), stripSqlBlocks(content))
})
