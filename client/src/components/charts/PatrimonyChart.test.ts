import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick } from 'vue'
import { useAppearanceStore } from '@/stores/appearance'
import PatrimonyChart from './PatrimonyChart.vue'

interface DatasetConfig {
  data: number[]
  borderColor: string
  fill: string | { target: number; above: string; below: string }
  backgroundColor?: (context: {
    chart: { ctx: { createLinearGradient: () => { addColorStop: () => void } }; chartArea?: object }
  }) => unknown
  pointRadius: number | ((context: { dataIndex: number }) => number)
}

interface ChartConfig {
  data: { labels: string[]; datasets: DatasetConfig[] }
  options: {
    plugins: { tooltip: { callbacks: { label: (context: { parsed: { y: number } }) => string } } }
    scales: {
      y: { min: number; max: number; ticks: { callback: (value: number) => string } }
    }
  }
  plugins: {
    id: string
    beforeDraw: (chartInstance: unknown) => void
    afterDatasetsDraw: (chartInstance: unknown) => void
  }[]
}

interface FakeChartInstance {
  config: ChartConfig
  destroy: ReturnType<typeof vi.fn>
}

const fakeCharts = vi.hoisted(() => [] as FakeChartInstance[])

vi.mock('chart.js', () => {
  class FakeChart {
    static register = vi.fn()
    destroy = vi.fn()
    constructor(
      public canvas: HTMLCanvasElement,
      public config: ChartConfig,
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

const THEME = {
  '--primary': '#206bc4',
  '--up': '#15915b',
  '--down': '#e0384f',
  '--chart-grid': '#e6e7e9',
  '--text-muted': '#667382',
  '--surface': '#ffffff',
}

function mountChart(props: Record<string, unknown> = {}) {
  return mount(PatrimonyChart, {
    props: {
      ownMoney: [100, 200, 300],
      result: [10, 30, 60],
      labels: ['set/26', 'out/26', 'nov/26'],
      ...props,
    },
    global: { plugins: [createTestingPinia({ stubActions: false })] },
  })
}

function latestChart() {
  return fakeCharts[fakeCharts.length - 1]!
}

function fakeContext() {
  return {
    save: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    setLineDash: vi.fn(),
    fillText: vi.fn(),
    strokeStyle: '',
    fillStyle: '',
    lineWidth: 0,
    font: '',
    textAlign: '',
    textBaseline: '',
  }
}

function fakeChartInstance(withScales = true) {
  const context = fakeContext()
  return {
    context,
    chartInstance: {
      ctx: context,
      chartArea: { left: 10, right: 400, top: 5, bottom: 200 },
      scales: withScales
        ? {
            y: {
              ticks: [{ value: 0 }, { value: 100 }],
              getPixelForValue: (value: number) => 200 - value,
            },
            x: { getPixelForValue: (index: number) => 10 + index * 100 },
          }
        : {},
    },
  }
}

describe('PatrimonyChart', () => {
  beforeEach(() => {
    fakeCharts.length = 0
    for (const [variable, value] of Object.entries(THEME)) {
      document.documentElement.style.setProperty(variable, value)
    }
  })

  afterEach(() => {
    document.documentElement.removeAttribute('style')
  })

  it('draws own money as an area and the total as a line filled against it', () => {
    mountChart()

    const [ownMoney, total] = latestChart().config.data.datasets
    expect(latestChart().config.data.labels).toEqual(['set/26', 'out/26', 'nov/26'])
    expect(ownMoney!.data).toEqual([100, 200, 300])
    expect(ownMoney!.fill).toBe('origin')
    expect(ownMoney!.borderColor).toBe('#206bc4')
    expect(total!.data).toEqual([110, 230, 360])
    expect(total!.fill).toEqual({
      target: 0,
      above: 'rgba(21, 145, 91, 0.2)',
      below: 'rgba(224, 56, 79, 0.2)',
    })
    expect(total!.borderColor).toBe('#15915b')
  })

  it('colours the total line red when the latest result is a loss', () => {
    mountChart({ result: [10, -20, -40] })

    expect(latestChart().config.data.datasets[1]!.borderColor).toBe('#e0384f')
  })

  it('dots only the last point of the total line', () => {
    mountChart()

    const pointRadius = latestChart().config.data.datasets[1]!.pointRadius as (context: {
      dataIndex: number
    }) => number
    expect(pointRadius({ dataIndex: 2 })).toBe(4.5)
    expect(pointRadius({ dataIndex: 0 })).toBe(0)
  })

  it('fills own money with a gradient once the chart area exists', () => {
    mountChart()

    const background = latestChart().config.data.datasets[0]!.backgroundColor!
    const addColorStop = vi.fn()
    const gradient = { addColorStop }
    const chart = { ctx: { createLinearGradient: () => gradient } }

    expect(background({ chart })).toBeUndefined()
    expect(background({ chart: { ...chart, chartArea: { top: 0, bottom: 100 } } })).toBe(gradient)
    expect(addColorStop).toHaveBeenCalledWith(0, 'rgba(32, 107, 196, 0.22)')
  })

  it('keeps the axis at zero for positive values and pads below zero otherwise', () => {
    mountChart()
    expect(latestChart().config.options.scales.y.min).toBe(0)

    mountChart({ ownMoney: [100, 100], result: [-300, -200], labels: ['a', 'b'] })
    expect(latestChart().config.options.scales.y.min).toBeLessThan(-200)
  })

  it('formats ticks and tooltips with the given formatter', () => {
    mountChart({ fmtY: (value: number) => `R$ ${value}` })

    const { options } = latestChart().config
    expect(options.scales.y.ticks.callback(500)).toBe('R$ 500')
    expect(options.plugins.tooltip.callbacks.label({ parsed: { y: 42 } })).toBe('R$ 42')
  })

  it('draws the dashed grid and the labelled marker at the marker index', () => {
    mountChart({ markerIndex: 1, markerLabel: 'início dos registros diários' })

    const [plugin] = latestChart().config.plugins
    const { chartInstance, context } = fakeChartInstance()
    plugin!.beforeDraw(chartInstance)
    plugin!.afterDatasetsDraw(chartInstance)

    expect(context.lineTo).toHaveBeenCalledWith(400, 200)
    expect(context.moveTo).toHaveBeenCalledWith(110, 5)
    expect(context.fillText).toHaveBeenCalledWith('início dos registros diários', 104, 5)
  })

  it('skips the marker without an index and the decorations without scales', () => {
    mountChart()

    const [plugin] = latestChart().config.plugins
    const withScales = fakeChartInstance()
    plugin!.afterDatasetsDraw(withScales.chartInstance)
    expect(withScales.context.fillText).not.toHaveBeenCalled()

    const withoutScales = fakeChartInstance(false)
    plugin!.beforeDraw(withoutScales.chartInstance)
    plugin!.afterDatasetsDraw(withoutScales.chartInstance)
    expect(withoutScales.context.stroke).not.toHaveBeenCalled()
  })

  it('rebuilds on new data and on a theme change, and destroys the chart on unmount', async () => {
    const wrapper = mountChart()
    const first = latestChart()

    await wrapper.setProps({ result: [0, 0, 0] })
    expect(first.destroy).toHaveBeenCalled()
    expect(fakeCharts).toHaveLength(2)

    useAppearanceStore().dark = !useAppearanceStore().dark
    await nextTick()
    await nextTick()
    expect(fakeCharts).toHaveLength(3)

    wrapper.unmount()
    expect(latestChart().destroy).toHaveBeenCalled()
  })
})
