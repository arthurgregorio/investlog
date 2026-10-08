import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AssetTypeTable from './AssetTypeTable.vue'
import type { AssetType } from '@/types'

const types: AssetType[] = [
  { id: 'type-1', name: 'Ordinária', usageCount: 0 },
  { id: 'type-2', name: 'Preferencial', usageCount: 3 },
]

function mountTable(editable = true) {
  return mount(AssetTypeTable, { props: { types, noun: 'tipo', editable } })
}

describe('AssetTypeTable', () => {
  it('lists every type with its usage count', () => {
    const wrapper = mountTable()

    expect(wrapper.findAll('tbody td:first-child').map((cell) => cell.text())).toEqual([
      'Ordinária',
      'Preferencial',
    ])
    expect(wrapper.findAll('tbody .c-num').map((cell) => cell.text())).toEqual(['0', '3'])
  })

  it('has no actions column when it is not editable', () => {
    const wrapper = mountTable(false)

    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Nome',
      'Investimentos',
    ])
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('emits rename and remove with the row type', async () => {
    const wrapper = mountTable()
    const [renameButton, removeButton] = wrapper.findAll('tbody tr')[0].findAll('button')

    await renameButton.trigger('click')
    await removeButton.trigger('click')

    expect(wrapper.emitted('rename')).toEqual([[types[0]]])
    expect(wrapper.emitted('remove')).toEqual([[types[0]]])
  })

  it('disables removing a type in use', async () => {
    const wrapper = mountTable()
    const removeButton = wrapper.findAll('tbody tr')[1].findAll('button')[1]

    expect(removeButton.attributes('disabled')).toBeDefined()

    await removeButton.trigger('click')

    expect(wrapper.emitted('remove')).toBeUndefined()
  })
})
