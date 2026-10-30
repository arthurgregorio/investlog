import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import StrategyClassesEditor from './StrategyClassesEditor.vue'
import { useStrategiesStore } from '@/stores/strategies'
import { strategy } from '@/test/strategyFixtures'
import type { Strategy } from '@/types'

let activeWrapper: VueWrapper | undefined

function mountEditor(subject: Strategy = strategy(), expandedClassId: string | null = null) {
  const pinia = createTestingPinia()
  activeWrapper = mount(StrategyClassesEditor, {
    props: {
      strategy: subject,
      expandedClassId,
      'onUpdate:expandedClassId': (value: string | null) =>
        activeWrapper?.setProps({ expandedClassId: value }),
    },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

function saveButton(wrapper: VueWrapper) {
  return wrapper.find('[data-testid="save-classes"]')
}

function targetInputs(wrapper: VueWrapper) {
  return wrapper.findAll('input[data-testid="class-target"]')
}

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('StrategyClassesEditor', () => {
  it('shows each class with its target, holding count and how its holdings split', () => {
    const wrapper = mountEditor()

    const rows = wrapper.findAll('[data-testid="class-row"]')
    expect(rows).toHaveLength(2)
    expect((targetInputs(wrapper)[0].element as HTMLInputElement).value).toBe('60')
    expect(rows[0].text()).toContain('1 com meta · 2 dividem 70,00% (35,00% cada)')
    expect(rows[1].text()).toContain('Todos dividem igualmente · 100,00% cada')
    expect(wrapper.find('[data-testid="classes-sum"]').text()).toBe('Soma das metas: 100,00%')
    expect(wrapper.find('[data-testid="classes-sum-error"]').exists()).toBe(false)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('blocks saving while the targets miss 100% and shows the gap', async () => {
    const wrapper = mountEditor()

    await wrapper.find('[data-testid="add-class"]').trigger('click')
    await wrapper.findAll('input[data-testid="class-name"]')[2].setValue('Renda fixa')
    await targetInputs(wrapper)[0].setValue('50')

    expect(wrapper.find('[data-testid="classes-sum"]').text()).toBe(
      'Soma das metas: 90,00% · faltam 10,00 pp',
    )
    expect(wrapper.find('[data-testid="classes-sum-error"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="class-row"]')[2].text()).toContain(
      'Salve para adicionar investimentos',
    )
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

    await targetInputs(wrapper)[2].setValue('10')
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('saves the renamed strategy and the class list in order', async () => {
    const wrapper = mountEditor()
    const store = useStrategiesStore()

    await wrapper.find('input[data-testid="strategy-name"]').setValue('Longo prazo 2')
    await wrapper.findAll('[data-testid="remove-class"]')[1].trigger('click')
    await targetInputs(wrapper)[0].setValue('100')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.rename).toHaveBeenCalledWith('strategy-long', 'Longo prazo 2')
    expect(store.saveClasses).toHaveBeenCalledWith('strategy-long', [
      { id: 'class-brazil', name: 'Ações Brasil', targetPercentage: 100 },
    ])
  })

  it('discards the draft and leaves the name alone when only classes change', async () => {
    const wrapper = mountEditor()
    const store = useStrategiesStore()

    await targetInputs(wrapper)[0].setValue('70')
    await targetInputs(wrapper)[1].setValue('30')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(store.rename).not.toHaveBeenCalled()

    await targetInputs(wrapper)[0].setValue('10')
    await wrapper.find('[data-testid="discard-classes"]').trigger('click')
    expect((targetInputs(wrapper)[0].element as HTMLInputElement).value).toBe('60')
  })

  it('needs every class named before saving', async () => {
    const wrapper = mountEditor()

    await wrapper.findAll('input[data-testid="class-name"]')[0].setValue(' ')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('expands and collapses a saved class and asks the parent to delete the strategy', async () => {
    const wrapper = mountEditor()

    await wrapper.findAll('[data-testid="expand-class"]')[0].trigger('click')
    expect(wrapper.emitted('update:expandedClassId')).toEqual([['class-brazil']])
    expect(wrapper.findAll('[data-testid="class-row"]')[0].classes()).toContain('is-current')

    await wrapper.findAll('[data-testid="expand-class"]')[0].trigger('click')
    expect(wrapper.emitted('update:expandedClassId')?.[1]).toEqual([null])

    await wrapper.find('[data-testid="delete-strategy"]').trigger('click')
    expect(wrapper.emitted('delete')).toHaveLength(1)
  })

  it('reloads the draft when the strategy changes', async () => {
    const wrapper = mountEditor()

    await wrapper.setProps({
      strategy: strategy({ id: 'strategy-other', name: 'Outra', classes: [] }),
    })

    expect(wrapper.findAll('[data-testid="class-row"]')).toHaveLength(0)
    expect(
      (wrapper.find('input[data-testid="strategy-name"]').element as HTMLInputElement).value,
    ).toBe('Outra')
  })
})
