import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CashTableRow from './CashTableRow.vue'

function mountRow() {
  return mount(
    {
      components: { CashTableRow },
      template: '<table><tbody><CashTableRow v-bind="$attrs" /></tbody></table>',
    },
    { props: { cash: 1234.5, currency: 'BRL' } },
  )
}

describe('CashTableRow', () => {
  it('shows the cash as the value of a Caixa row', () => {
    const wrapper = mountRow()

    expect(wrapper.get('[data-testid="cash-row"]').text()).toContain('Disponível para compras')
    expect(wrapper.get('[data-testid="cash-row-value"]').text()).toBe('R$ 1.234,50')
  })

  it.each([
    ['cash-row-transfer', 'transfer'],
    ['cash-row-withdraw', 'withdraw-to-bank'],
  ])('emits from its %s button', async (testId, event) => {
    const wrapper = mountRow()

    await wrapper.get(`[data-testid="${testId}"]`).trigger('click')

    expect(wrapper.findComponent(CashTableRow).emitted(event)).toHaveLength(1)
  })
})
