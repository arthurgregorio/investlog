import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SortTh from './SortTh.vue'

function mountHeader(props: Record<string, unknown> = {}) {
  return mount(SortTh, {
    props: { sortKey: 'ticker', activeKey: null, direction: 'asc', ...props },
    slots: { default: 'Ticker' },
    attachTo: document.createElement('tbody').appendChild(document.createElement('tr')),
  })
}

describe('SortTh', () => {
  it('renders its slot label', () => {
    expect(mountHeader().text()).toContain('Ticker')
  })

  it('emits toggle with its sort key when clicked', async () => {
    const wrapper = mountHeader({ sortKey: 'quantity' })

    await wrapper.trigger('click')

    expect(wrapper.emitted('toggle')).toEqual([['quantity']])
  })

  it('marks the icon active and points up when sorted ascending on this column', () => {
    const wrapper = mountHeader({ activeKey: 'ticker', direction: 'asc' })

    expect(wrapper.find('.sort-icon-active').exists()).toBe(true)
    expect(wrapper.find('.mdi-chevron-up').exists()).toBe(true)
  })

  it('marks the icon active and points down when sorted descending on this column', () => {
    const wrapper = mountHeader({ activeKey: 'ticker', direction: 'desc' })

    expect(wrapper.find('.sort-icon-active').exists()).toBe(true)
    expect(wrapper.find('.mdi-chevron-down').exists()).toBe(true)
  })

  it('shows an idle icon when another column is the active one', () => {
    const wrapper = mountHeader({ activeKey: 'quantity', direction: 'asc' })

    expect(wrapper.find('.sort-icon-idle').exists()).toBe(true)
    expect(wrapper.find('.sort-icon-active').exists()).toBe(false)
    expect(wrapper.find('.mdi-chevron-down').exists()).toBe(true)
  })

  it('right-aligns numerically by default and left-aligns on request', () => {
    expect(mountHeader().classes()).toContain('c-num')
    expect(mountHeader({ align: 'left' }).classes()).not.toContain('c-num')
  })
})
