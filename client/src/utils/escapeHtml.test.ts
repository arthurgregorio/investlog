import { describe, expect, it } from 'vitest'
import { escapeHtml } from './escapeHtml'

describe('escapeHtml', () => {
  it('escapes the five characters that can open or close markup', () => {
    expect(escapeHtml(`<a href="x" onclick='y'>&</a>`)).toBe(
      '&lt;a href=&quot;x&quot; onclick=&#39;y&#39;&gt;&amp;&lt;/a&gt;',
    )
  })

  it('leaves text without markup characters untouched', () => {
    expect(escapeHtml('Carteira Aposentadoria 2030')).toBe('Carteira Aposentadoria 2030')
  })

  it('escapes the ampersand first so entities are not double-decoded', () => {
    expect(escapeHtml('&lt;')).toBe('&amp;lt;')
  })
})
