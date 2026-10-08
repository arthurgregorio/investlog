import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CurrencyRateRow from './CurrencyRateRow.vue'
import type { CurrencyRate } from '@/types'

const dollar: CurrencyRate = { currencyCode: 'USD', rate: 5.2, isBase: false }

function mountRow(rate: CurrencyRate = dollar) {
  return mount(CurrencyRateRow, { props: { rate, baseCurrency: 'BRL' } })
}

describe('CurrencyRateRow', () => {
  it('marks the base currency instead of offering an input', () => {
    const wrapper = mountRow({ currencyCode: 'BRL', rate: 1, isBase: true })

    expect(wrapper.get('.cur-chip').text()).toBe('BRL')
    expect(wrapper.get('.rate-base').text()).toContain('Moeda base')
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('shows the stored rate against the base currency symbol', () => {
    const wrapper = mountRow()

    expect(wrapper.get('.rate-input').text()).toContain('1 USD =')
    expect(wrapper.get('.button.is-static').text()).toBe('R$')
    expect(wrapper.get('input').element.value).toBe('5.2')
  })

  it('commits an edited rate on blur and then shows the stored rate again', async () => {
    const wrapper = mountRow()
    const input = wrapper.get('input')

    await input.setValue('5.5')
    await input.trigger('blur')

    expect(wrapper.emitted('commit')).toEqual([[5.5]])
    expect(input.element.value).toBe('5.2')
  })

  it.each([['0'], ['-3'], ['']])('discards the draft "%s"', async (draft) => {
    const wrapper = mountRow()

    await wrapper.get('input').setValue(draft)
    await wrapper.get('input').trigger('blur')

    expect(wrapper.emitted('commit')).toBeUndefined()
  })

  it('commits nothing when blurred without an edit', async () => {
    const wrapper = mountRow()

    await wrapper.get('input').trigger('blur')

    expect(wrapper.emitted('commit')).toBeUndefined()
  })
})
