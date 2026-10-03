import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TickerBadge from './TickerBadge.vue'

describe('TickerBadge', () => {
  it('renders a short ticker as given', () => {
    const wrapper = mount(TickerBadge, { props: { ticker: 'VALE' } })

    expect(wrapper.text()).toBe('VALE')
  })

  it('truncates a long ticker to its first four characters', () => {
    const wrapper = mount(TickerBadge, { props: { ticker: 'PETR4F' } })

    expect(wrapper.text()).toBe('PETR')
  })

  it('shrinks the font for tickers longer than three characters', () => {
    const short = mount(TickerBadge, { props: { ticker: 'BTC' } })
    const long = mount(TickerBadge, { props: { ticker: 'MXRF' } })

    expect(short.attributes('style')).toContain('font-size: 12px')
    expect(long.attributes('style')).toContain('font-size: 10px')
  })

  it('applies the given color and size', () => {
    const wrapper = mount(TickerBadge, {
      props: { ticker: 'ABC', color: 'rgb(1, 2, 3)', size: 48 },
    })

    const style = wrapper.attributes('style')
    expect(style).toContain('width: 48px')
    expect(style).toContain('height: 48px')
    expect(style).toContain('background: rgb(1, 2, 3)')
  })

  it('falls back to the default size and an empty label without a ticker', () => {
    const wrapper = mount(TickerBadge)

    expect(wrapper.text()).toBe('')
    expect(wrapper.attributes('style')).toContain('width: 36px')
  })
})
