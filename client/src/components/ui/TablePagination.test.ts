import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TablePagination from './TablePagination.vue'

describe('TablePagination', () => {
  it('renders nothing when every row fits on one page', () => {
    const wrapper = mount(TablePagination, {
      props: { page: 0, pageSize: 20, totalElements: 20 },
    })

    expect(wrapper.find('.table-foot').exists()).toBe(false)
  })

  it('renders the pager once the rows span more than one page', () => {
    const wrapper = mount(TablePagination, {
      props: { page: 0, pageSize: 20, totalElements: 21 },
    })

    expect(wrapper.find('.table-foot .pagination').exists()).toBe(true)
  })

  it('emits the zero-based page the pager moved to', async () => {
    const wrapper = mount(TablePagination, {
      props: { page: 1, pageSize: 10, totalElements: 35 },
    })

    await wrapper.get('.pagination-next').trigger('click')
    await wrapper.get('.pagination-previous').trigger('click')

    expect(wrapper.emitted('page-change')).toEqual([[2], [0]])
  })
})
