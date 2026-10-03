import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick } from 'vue'
import { useAppearanceStore } from '@/stores/appearance'
import AreaChart from './AreaChart.vue'

interface FakeChartInstance {
  canvas: HTMLCanvasElement
  config: AreaChartConfig
  update: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
}

interface TickOptions {
  count: number
  color: string
  callback: (value: number | string) => string
}

interface AreaChartConfig {
  type: string
  data: { labels: string[]; datasets: DatasetConfig[] }
  options: {
    plugins: {
      legend: { display: boolean }
      tooltip: { callbacks: { label: (context: { parsed: { y: number } }) => string } }
    }
    scales: {
      y: { display: boolean; min: number; max: number; ticks: TickOptions }
      x: { ticks: { color: string } }
    }
  }
  plugins: GridPlugin[]
}

interface DatasetConfig {
  data: number[]
  borderColor: string
  backgroundColor: (context: ChartContext) => CanvasGradient | undefined
  pointRadius: (context: { dataIndex: number }) => number
  pointBackgroundColor: () => string
  pointBorderColor: () => string
}

interface ChartContext {
  chart: { ctx: FakeGradientContext; chartArea?: { top: number; bottom: number } }
}

interface FakeGradientContext {
  createLinearGradient: (x0: number, y0: number, x1: number, y1: number) => FakeGradient
}

interface FakeGradient {
  addColorStop: ReturnType<typeof vi.fn>
}

interface GridPlugin {
  id: string
  beforeDraw: (chartInstance: unknown) => void
}

const fakeCharts = vi.hoisted(() => [] as FakeChartInstance[])

vi.mock('chart.js', () => {
  class FakeChart {
    static register = vi.fn()
    update = vi.fn()
    destroy = vi.fn()
    constructor(
      public canvas: HTMLCanvasElement,
      public config: AreaChartConfig,
    ) {
      fakeCharts.push(this)
    }
  }
  return {
    Chart: FakeChart,
    CategoryScale: {},
    Filler: {},
    LinearScale: {},
    LineController: {},
    LineElement: {},
    PointElement: {},
    Tooltip: {},
  }
})

const LIGHT_THEME = {
  '--chart-grid': '#e6e7e9',
  '--text-muted': '#667382',
  '--surface': '#ffffff',
}

const DARK_THEME = {
  '--chart-grid': '#2c3a4d',
  '--text-muted': '#9aa5b4',
  '--surface': '#182433',
}

function applyTheme(name: 'light' | 'dark') {
  document.documentElement.dataset.theme = name
  const variables = name === 'light' ? LIGHT_THEME : DARK_THEME
  for (const [variable, value] of Object.entries(variables)) {
    document.documentElement.style.setProperty(variable, value)
  }
}

function mountChart(props: Record<string, unknown> = {}) {
  const wrapper = mount(AreaChart, {
    props: { data: [10, 20, 30], ...props },
    global: { plugins: [createTestingPinia({ stubActions: false })] },
  })
  return wrapper
}

function latestChart() {
  return fakeCharts[fakeCharts.length - 1]
}

function yRange() {
  const { min, max } = latestChart().config.options.scales.y
  return { lo: min, hi: max }
}

function fakeDrawingContext() {
  return {
    save: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    setLineDash: vi.fn(),
    strokeStyle: '',
    lineWidth: 0,
  }
}

function fakeChartInstance(ticks: number[], withYScale = true) {
  const context = fakeDrawingContext()
  const chartInstance = {
    ctx: context,
    chartArea: { left: 12, right: 480, top: 8, bottom: 220 },
    scales: withYScale
      ? {
          y: {
            ticks: ticks.map((value) => ({ value })),
            getPixelForValue: (value: number) => 200 - value,
          },
        }
      : {},
  }
  return { chartInstance, context }
}

describe('AreaChart', () => {
  beforeEach(() => {
    fakeCharts.length = 0
    applyTheme('light')
  })

  afterEach(() => {
    document.documentElement.removeAttribute('style')
    document.documentElement.removeAttribute('data-theme')
  })

  describe('chart creation', () => {
    it('builds one line chart on mount with the data, labels and line color', () => {
      mountChart({ data: [5, 10, 15], xLabels: ['Jan', 'Fev', 'Mar'], color: '#2fb344' })

      expect(fakeCharts).toHaveLength(1)
      const { config, canvas } = latestChart()
      expect(canvas).toBeInstanceOf(HTMLCanvasElement)
      expect(config.type).toBe('line')
      expect(config.data.labels).toEqual(['Jan', 'Fev', 'Mar'])
      expect(config.data.datasets[0].data).toEqual([5, 10, 15])
      expect(config.data.datasets[0].borderColor).toBe('#2fb344')
    })

    it('falls back to empty labels when none are supplied', () => {
      mountChart({ data: [5, 10, 15] })

      expect(latestChart().config.data.labels).toEqual([])
    })

    it('sizes the wrapper to the height prop', () => {
      const wrapper = mountChart({ height: 320 })

      expect(wrapper.attributes('style')).toContain('height: 320px')
    })

    it('hides the legend and registers the dashed grid plugin', () => {
      mountChart()

      const { config } = latestChart()
      expect(config.options.plugins.legend.display).toBe(false)
      expect(config.plugins.map((plugin) => plugin.id)).toEqual(['dashedYGrid'])
    })

    it('resolves a CSS variable line color through the active theme', () => {
      document.documentElement.style.setProperty('--line-color', '#abcdef')

      mountChart({ color: 'var(--line-color)' })

      expect(latestChart().config.data.datasets[0].borderColor).toBe('#abcdef')
    })
  })

  describe('y range', () => {
    it('pads an ordinary series above and below its extremes', () => {
      mountChart({ data: [10, 20] })

      const { lo, hi } = yRange()
      expect(lo).toBeCloseTo(8.5)
      expect(hi).toBeCloseTo(21.5)
    })

    it('never pads a non-negative series below zero', () => {
      mountChart({ data: [1, 100] })

      const { lo, hi } = yRange()
      expect(lo).toBe(0)
      expect(hi).toBeGreaterThan(100)
    })

    it('opens a non-zero range around a flat series', () => {
      mountChart({ data: [100, 100, 100] })

      const { lo, hi } = yRange()
      expect(lo).toBeLessThan(100)
      expect(hi).toBeGreaterThan(100)
      expect(hi - lo).toBeGreaterThan(0)
    })

    it('opens a non-zero range around a single point', () => {
      mountChart({ data: [50] })

      const { lo, hi } = yRange()
      expect(lo).toBeLessThan(50)
      expect(hi).toBeGreaterThan(50)
    })

    it('extends below the minimum when the series spans zero', () => {
      mountChart({ data: [-10, 10] })

      const { lo, hi } = yRange()
      expect(lo).toBeCloseTo(-13)
      expect(hi).toBeCloseTo(13)
    })

    it('extends below zero for an all-negative series', () => {
      mountChart({ data: [-50, -20] })

      const { lo, hi } = yRange()
      expect(lo).toBeLessThan(-50)
      expect(hi).toBeGreaterThan(-20)
    })

    it('still builds a chart for an empty series', () => {
      mountChart({ data: [] })

      expect(fakeCharts).toHaveLength(1)
      expect(latestChart().config.data.datasets[0].data).toEqual([])
    })
  })

  describe('axis wiring', () => {
    it('shows the y axis only when the grid is enabled', () => {
      mountChart({ showGrid: false })

      expect(latestChart().config.options.scales.y.display).toBe(false)
    })

    it('formats y ticks and tooltip values through the fmtY prop', () => {
      const fmtY = vi.fn((value: number) => `R$ ${value}`)
      mountChart({ fmtY })

      const { options } = latestChart().config
      expect(options.scales.y.ticks.callback(1500)).toBe('R$ 1500')
      expect(options.scales.y.ticks.callback('0')).toBe('R$ 0')
      expect(options.scales.y.ticks.callback(-250)).toBe('R$ -250')
      expect(options.plugins.tooltip.callbacks.label({ parsed: { y: 42 } })).toBe('R$ 42')
      expect(fmtY).toHaveBeenCalledWith(1500)
      expect(fmtY).toHaveBeenCalledWith(0)
      expect(fmtY).toHaveBeenCalledWith(-250)
    })

    it('stringifies tick values when no formatter is given', () => {
      mountChart()

      expect(latestChart().config.options.scales.y.ticks.callback(1200)).toBe('1200')
    })

    it('asks for five y ticks', () => {
      mountChart()

      expect(latestChart().config.options.scales.y.ticks.count).toBe(5)
    })
  })

  describe('dataset callbacks', () => {
    it('marks only the last point with a visible dot', () => {
      mountChart({ data: [1, 2, 3] })

      const { pointRadius } = latestChart().config.data.datasets[0]
      expect(pointRadius({ dataIndex: 0 })).toBe(0)
      expect(pointRadius({ dataIndex: 1 })).toBe(0)
      expect(pointRadius({ dataIndex: 2 })).toBe(4.5)
    })

    it('outlines the dot with the surface color and fills it with the line color', () => {
      mountChart({ color: '#2fb344' })

      const dataset = latestChart().config.data.datasets[0]
      expect(dataset.pointBackgroundColor()).toBe('#2fb344')
      expect(dataset.pointBorderColor()).toBe(LIGHT_THEME['--surface'])
    })

    it('fades the area from the line color to transparent once the chart area exists', () => {
      mountChart({ color: '#2fb344' })

      const { backgroundColor } = latestChart().config.data.datasets[0]
      const gradient = { addColorStop: vi.fn() }
      const createLinearGradient = vi.fn(() => gradient)

      const result = backgroundColor({
        chart: { ctx: { createLinearGradient }, chartArea: { top: 8, bottom: 220 } },
      })

      expect(createLinearGradient).toHaveBeenCalledWith(0, 8, 0, 220)
      expect(gradient.addColorStop).toHaveBeenNthCalledWith(1, 0, 'rgba(47, 179, 68, 0.22)')
      expect(gradient.addColorStop).toHaveBeenNthCalledWith(2, 1, 'rgba(47, 179, 68, 0)')
      expect(result).toBe(gradient)
    })

    it('returns no fill before the chart area is laid out', () => {
      mountChart()

      const { backgroundColor } = latestChart().config.data.datasets[0]
      const createLinearGradient = vi.fn()

      const result = backgroundColor({ chart: { ctx: { createLinearGradient } } })

      expect(result).toBeUndefined()
      expect(createLinearGradient).not.toHaveBeenCalled()
    })
  })

  describe('dashed grid plugin', () => {
    it('strokes one dashed horizontal line per y tick across the chart area', () => {
      mountChart()
      const [plugin] = latestChart().config.plugins
      const { chartInstance, context } = fakeChartInstance([0, 50, 100])

      plugin.beforeDraw(chartInstance)

      expect(context.save).toHaveBeenCalledTimes(1)
      expect(context.setLineDash).toHaveBeenCalledWith([2, 4])
      expect(context.strokeStyle).toBe(LIGHT_THEME['--chart-grid'])
      expect(context.lineWidth).toBe(1)
      expect(context.moveTo.mock.calls).toEqual([
        [12, 200],
        [12, 150],
        [12, 100],
      ])
      expect(context.lineTo.mock.calls).toEqual([
        [480, 200],
        [480, 150],
        [480, 100],
      ])
      expect(context.stroke).toHaveBeenCalledTimes(3)
      expect(context.restore).toHaveBeenCalledTimes(1)
    })

    it('draws nothing when the grid is disabled', () => {
      mountChart({ showGrid: false })
      const [plugin] = latestChart().config.plugins
      const { chartInstance, context } = fakeChartInstance([0, 50, 100])

      plugin.beforeDraw(chartInstance)

      expect(context.save).not.toHaveBeenCalled()
      expect(context.stroke).not.toHaveBeenCalled()
    })

    it('draws nothing when the chart has no y scale yet', () => {
      mountChart()
      const [plugin] = latestChart().config.plugins
      const { chartInstance, context } = fakeChartInstance([], false)

      plugin.beforeDraw(chartInstance)

      expect(context.save).not.toHaveBeenCalled()
      expect(context.stroke).not.toHaveBeenCalled()
    })
  })

  describe('theming', () => {
    it('resolves different colors under the light and the dark theme', () => {
      mountChart()
      const lightChart = latestChart()
      const [lightPlugin] = lightChart.config.plugins
      const light = fakeChartInstance([0])
      lightPlugin.beforeDraw(light.chartInstance)

      applyTheme('dark')
      mountChart()
      const darkChart = latestChart()
      const [darkPlugin] = darkChart.config.plugins
      const dark = fakeChartInstance([0])
      darkPlugin.beforeDraw(dark.chartInstance)

      expect(lightChart.config.options.scales.y.ticks.color).toBe(LIGHT_THEME['--text-muted'])
      expect(darkChart.config.options.scales.y.ticks.color).toBe(DARK_THEME['--text-muted'])
      expect(light.context.strokeStyle).toBe(LIGHT_THEME['--chart-grid'])
      expect(dark.context.strokeStyle).toBe(DARK_THEME['--chart-grid'])
      expect(lightChart.config.data.datasets[0].pointBorderColor()).not.toBe(
        darkChart.config.data.datasets[0].pointBorderColor(),
      )
    })

    it('rebuilds the chart with the new theme colors when the appearance changes', async () => {
      mountChart()
      const firstChart = latestChart()
      expect(firstChart.config.options.scales.x.ticks.color).toBe(LIGHT_THEME['--text-muted'])

      applyTheme('dark')
      useAppearanceStore().dark = true
      await nextTick()

      expect(firstChart.destroy).toHaveBeenCalledTimes(1)
      expect(fakeCharts).toHaveLength(2)
      expect(latestChart().config.options.scales.x.ticks.color).toBe(DARK_THEME['--text-muted'])
      expect(latestChart().config.options.scales.y.ticks.color).toBe(DARK_THEME['--text-muted'])
    })

    it('rebuilds the chart when the accent changes', async () => {
      mountChart()

      useAppearanceStore().accent = 'indigo'
      await nextTick()

      expect(fakeCharts).toHaveLength(2)
    })
  })

  describe('lifecycle', () => {
    it('replaces the chart instead of stacking a second one when the data changes', async () => {
      const wrapper = mountChart({ data: [10, 20, 30] })
      const firstChart = latestChart()

      await wrapper.setProps({ data: [40, 50, 60] })

      expect(firstChart.destroy).toHaveBeenCalledTimes(1)
      expect(fakeCharts).toHaveLength(2)
      expect(latestChart().config.data.datasets[0].data).toEqual([40, 50, 60])
      expect(latestChart().destroy).not.toHaveBeenCalled()
    })

    it('picks up a new range and a new line color on prop changes', async () => {
      const wrapper = mountChart({ data: [10, 20], color: '#2fb344' })

      await wrapper.setProps({ data: [100, 200], color: '#d63939' })

      expect(yRange().hi).toBeGreaterThan(200)
      expect(latestChart().config.data.datasets[0].borderColor).toBe('#d63939')
    })

    it('rebuilds when the grid visibility or the labels change', async () => {
      const wrapper = mountChart({ xLabels: ['a', 'b', 'c'] })

      await wrapper.setProps({ showGrid: false })
      await wrapper.setProps({ xLabels: ['x', 'y', 'z'] })

      expect(fakeCharts).toHaveLength(3)
      expect(latestChart().config.options.scales.y.display).toBe(false)
      expect(latestChart().config.data.labels).toEqual(['x', 'y', 'z'])
    })

    it('destroys the chart on unmount', () => {
      const wrapper = mountChart()
      const chart = latestChart()

      wrapper.unmount()

      expect(chart.destroy).toHaveBeenCalledTimes(1)
      expect(fakeCharts).toHaveLength(1)
    })
  })
})
