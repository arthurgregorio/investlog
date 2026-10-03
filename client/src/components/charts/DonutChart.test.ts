import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick } from 'vue'
import { useAppearanceStore } from '@/stores/appearance'
import DonutChart from './DonutChart.vue'

interface DonutChartConfig {
  type: string
  data: {
    labels: string[]
    datasets: { data: number[]; backgroundColor: string[]; borderWidth: number }[]
  }
  options: {
    cutout: string
    plugins: {
      tooltip: {
        callbacks: {
          label: (context: {
            label: string
            parsed: number
            dataset: { data: number[] }
          }) => string
        }
      }
    }
  }
}

interface FakeChartInstance {
  canvas: HTMLCanvasElement
  config: DonutChartConfig
  data: DonutChartConfig['data']
  update: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
}

const fakeCharts = vi.hoisted(() => [] as FakeChartInstance[])

vi.mock('chart.js', () => {
  class FakeChart {
    static register = vi.fn()
    data: DonutChartConfig['data']
    update = vi.fn()
    destroy = vi.fn()
    constructor(
      public canvas: HTMLCanvasElement,
      public config: DonutChartConfig,
    ) {
      this.data = config.data
      fakeCharts.push(this)
    }
  }
  return {
    Chart: FakeChart,
    ArcElement: {},
    DoughnutController: {},
    Tooltip: {},
  }
})

const SEGMENTS = [
  { value: 60, color: '#2fb344', label: 'Ações' },
  { value: 30, color: '#206bc4', label: 'FIIs' },
  { value: 10, color: 'var(--segment-color)', label: 'Cripto' },
]

function mountChart(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(DonutChart, {
    props: { segments: SEGMENTS, ...props },
    slots,
    global: { plugins: [createTestingPinia({ stubActions: false })] },
  })
}

function latestChart() {
  return fakeCharts[fakeCharts.length - 1]
}

describe('DonutChart', () => {
  beforeEach(() => {
    fakeCharts.length = 0
    document.documentElement.style.setProperty('--segment-color', '#f59f00')
  })

  afterEach(() => {
    document.documentElement.removeAttribute('style')
  })

  describe('chart creation', () => {
    it('builds one doughnut chart on mount from the segments', () => {
      mountChart()

      expect(fakeCharts).toHaveLength(1)
      const { config, canvas } = latestChart()
      expect(canvas).toBeInstanceOf(HTMLCanvasElement)
      expect(config.type).toBe('doughnut')
      expect(config.data.labels).toEqual(['Ações', 'FIIs', 'Cripto'])
      expect(config.data.datasets).toHaveLength(1)
      expect(config.data.datasets[0].data).toEqual([60, 30, 10])
      expect(config.data.datasets[0].borderWidth).toBe(0)
    })

    it('resolves CSS variable segment colors and keeps literal ones', () => {
      mountChart()

      expect(latestChart().config.data.datasets[0].backgroundColor).toEqual([
        '#2fb344',
        '#206bc4',
        '#f59f00',
      ])
    })

    it('still constructs a chart with empty data for an empty segments array', () => {
      mountChart({ segments: [] })

      expect(fakeCharts).toHaveLength(1)
      expect(latestChart().config.data.labels).toEqual([])
      expect(latestChart().config.data.datasets[0].data).toEqual([])
    })
  })

  describe('size and thickness', () => {
    it('sizes the wrapper to the default 168 pixels', () => {
      const wrapper = mountChart()

      expect(wrapper.attributes('style')).toContain('width: 168px')
      expect(wrapper.attributes('style')).toContain('height: 168px')
    })

    it('sizes the wrapper to the size prop', () => {
      const wrapper = mountChart({ size: 200 })

      expect(wrapper.attributes('style')).toContain('width: 200px')
      expect(wrapper.attributes('style')).toContain('height: 200px')
    })

    it('derives the cutout from the default size and thickness', () => {
      mountChart()

      expect(latestChart().config.options.cutout).toBe(`${((168 / 2 - 22) / (168 / 2)) * 100}%`)
    })

    it('derives the cutout from the size and thickness props', () => {
      mountChart({ size: 100, thickness: 10 })

      expect(latestChart().config.options.cutout).toBe('80%')
    })

    it('renders the default slot over the chart', () => {
      const wrapper = mountChart({}, { default: '<strong>R$ 1.000</strong>' })

      expect(wrapper.find('strong').text()).toBe('R$ 1.000')
    })
  })

  describe('tooltip', () => {
    it('labels a segment with its share of the total', () => {
      mountChart()
      const { label } = latestChart().config.options.plugins.tooltip.callbacks

      const text = label({ label: 'FIIs', parsed: 30, dataset: { data: [60, 30, 10] } })

      expect(text).toBe('FIIs: 30.0%')
    })

    it('rounds the share to one decimal place', () => {
      mountChart()
      const { label } = latestChart().config.options.plugins.tooltip.callbacks

      const text = label({ label: 'A', parsed: 1, dataset: { data: [1, 2] } })

      expect(text).toBe('A: 33.3%')
    })

    it('shows zero percent when the segments total zero', () => {
      mountChart()
      const { label } = latestChart().config.options.plugins.tooltip.callbacks

      const text = label({ label: 'A', parsed: 0, dataset: { data: [0, 0] } })

      expect(text).toBe('A: 0%')
    })
  })

  describe('updates', () => {
    it('replaces the chart when the segments change', async () => {
      const wrapper = mountChart()
      const firstChart = latestChart()

      await wrapper.setProps({ segments: [{ value: 5, color: '#d63939', label: 'Fundos' }] })

      expect(firstChart.destroy).toHaveBeenCalledTimes(1)
      expect(fakeCharts).toHaveLength(2)
      expect(latestChart().config.data.labels).toEqual(['Fundos'])
      expect(latestChart().config.data.datasets[0].data).toEqual([5])
    })

    it('replaces the chart with a new cutout when size or thickness change', async () => {
      const wrapper = mountChart({ size: 100, thickness: 10 })

      await wrapper.setProps({ thickness: 20 })
      expect(latestChart().config.options.cutout).toBe('60%')

      await wrapper.setProps({ size: 200 })
      expect(latestChart().config.options.cutout).toBe('80%')
      expect(fakeCharts).toHaveLength(3)
    })

    it('re-resolves the segment colors and updates in place when the theme changes', async () => {
      mountChart()
      const chart = latestChart()
      expect(chart.data.datasets[0].backgroundColor[2]).toBe('#f59f00')

      document.documentElement.style.setProperty('--segment-color', '#ffd43b')
      useAppearanceStore().dark = true
      await nextTick()

      expect(chart.data.datasets[0].backgroundColor).toEqual(['#2fb344', '#206bc4', '#ffd43b'])
      expect(chart.update).toHaveBeenCalledTimes(1)
      expect(chart.destroy).not.toHaveBeenCalled()
      expect(fakeCharts).toHaveLength(1)
    })
  })

  describe('lifecycle', () => {
    it('destroys the chart on unmount', () => {
      const wrapper = mountChart()
      const chart = latestChart()

      wrapper.unmount()

      expect(chart.destroy).toHaveBeenCalledTimes(1)
    })
  })
})
