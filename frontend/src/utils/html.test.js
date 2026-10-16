import { test } from 'node:test'
import assert from 'node:assert/strict'
import { escapeHtml } from './html.js'

test('escapes stored markup and quoted attribute breakout', () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;')
  assert.equal(escapeHtml('" onerror="alert(1)'), '&quot; onerror=&quot;alert(1)')
  assert.equal(escapeHtml("张三 & '同学'"), '张三 &amp; &#39;同学&#39;')
  assert.equal(escapeHtml(null), '')
})
