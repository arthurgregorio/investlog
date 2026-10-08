import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SettingsSection from './SettingsSection.vue'

describe('SettingsSection', () => {
  it('frames its content under the title and description', () => {
    const wrapper = mount(SettingsSection, {
      props: { title: 'Sincronização automática', description: 'Ative ou desative.' },
      slots: {
        default: '<p class="body-text">Conteúdo</p>',
        aside: '<span class="aside">Base</span>',
      },
    })

    expect(wrapper.get('h2').text()).toBe('Sincronização automática')
    expect(wrapper.get('.set-desc').text()).toBe('Ative ou desative.')
    expect(wrapper.get('h2 + .aside').text()).toBe('Base')
    expect(wrapper.get('.card-body .body-text').text()).toBe('Conteúdo')
  })

  it('shows its loading overlay only while loading', async () => {
    const wrapper = mount(SettingsSection, { props: { title: 'Taxas', description: 'Moedas.' } })

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    await wrapper.setProps({ loading: true })

    expect(wrapper.find('.loading-overlay').exists()).toBe(true)
  })
})
