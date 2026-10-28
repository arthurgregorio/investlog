import type { ClassHolding, Strategy, StrategyClass } from '@/types'

export function strategyClass(overrides: Partial<StrategyClass> = {}): StrategyClass {
  return {
    id: 'class-brazil',
    name: 'Ações Brasil',
    targetPercentage: 60,
    position: 0,
    default: false,
    holdingCount: 3,
    typedCount: 1,
    typedPercentageSum: 30,
    blankSharePercentage: 35,
    ...overrides,
  }
}

export function strategy(overrides: Partial<Strategy> = {}): Strategy {
  return {
    id: 'strategy-long',
    name: 'Longo prazo',
    default: false,
    holdingCount: 4,
    classes: [
      strategyClass(),
      strategyClass({
        id: 'class-abroad',
        name: 'Exterior',
        targetPercentage: 40,
        position: 1,
        holdingCount: 1,
        typedCount: 0,
        typedPercentageSum: 0,
        blankSharePercentage: 100,
      }),
    ],
    ...overrides,
  }
}

export function defaultStrategy(overrides: Partial<Strategy> = {}): Strategy {
  return {
    id: 'strategy-default',
    name: 'Sem estratégia',
    default: true,
    holdingCount: 2,
    classes: [
      strategyClass({
        id: 'class-undefined',
        name: 'Não definida',
        targetPercentage: null,
        default: true,
        holdingCount: 2,
        typedCount: 0,
        typedPercentageSum: 0,
        blankSharePercentage: 50,
      }),
    ],
    ...overrides,
  }
}

export function classHolding(overrides: Partial<ClassHolding> = {}): ClassHolding {
  return {
    kind: 'STOCKS',
    id: 'holding-petr4',
    walletId: 'wallet-brazil',
    walletName: 'Ações Brasil',
    ticker: 'PETR4',
    name: 'Petrobras PN',
    typeLabel: 'Ações',
    segmentLabel: 'Energia',
    frozen: false,
    targetPercentage: 30,
    effectiveTargetPercentage: 30,
    ...overrides,
  }
}
