import type {
  ClassAllocation,
  ClassHolding,
  ContributionPlan,
  HoldingAllocation,
  Strategy,
  StrategyAllocation,
  StrategyClass,
} from '@/types'

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

export function holdingAllocation(overrides: Partial<HoldingAllocation> = {}): HoldingAllocation {
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
    typed: true,
    actualPercentage: 60,
    driftPercentagePoints: 30,
    driftValue: 150,
    value: 300,
    ...overrides,
  }
}

export function classAllocation(overrides: Partial<ClassAllocation> = {}): ClassAllocation {
  return {
    id: 'class-brazil',
    name: 'Ações Brasil',
    targetPercentage: 60,
    actualPercentage: 50,
    driftPercentagePoints: -10,
    driftValue: -100,
    value: 500,
    holdings: [
      holdingAllocation(),
      holdingAllocation({
        id: 'holding-vale3',
        ticker: 'VALE3',
        name: 'Vale ON',
        segmentLabel: null,
        targetPercentage: 35,
        typed: false,
        actualPercentage: 20,
        driftPercentagePoints: -15,
        driftValue: -75,
        value: 100,
      }),
      holdingAllocation({
        id: 'holding-itub4',
        ticker: 'ITUB4',
        name: 'Itaú Unibanco PN',
        segmentLabel: 'Financeiro',
        frozen: true,
        targetPercentage: 35,
        typed: false,
        actualPercentage: 20,
        driftPercentagePoints: -15,
        driftValue: -75,
        value: 100,
      }),
    ],
    segments: [
      { label: 'Energia', value: 300, classPercentage: 60, strategyPercentage: 30 },
      { label: 'Financeiro', value: 100, classPercentage: 20, strategyPercentage: 10 },
      { label: 'Sem segmento', value: 100, classPercentage: 20, strategyPercentage: 10 },
    ],
    ...overrides,
  }
}

export function strategyAllocation(
  overrides: Partial<StrategyAllocation> = {},
): StrategyAllocation {
  return {
    strategyId: 'strategy-long',
    strategyName: 'Longo prazo',
    displayCurrency: 'BRL',
    totalValue: 1000,
    classes: [
      classAllocation(),
      classAllocation({
        id: 'class-abroad',
        name: 'Exterior',
        targetPercentage: 40,
        actualPercentage: 50,
        driftPercentagePoints: 10,
        driftValue: 100,
        value: 500,
        holdings: [
          holdingAllocation({
            id: 'holding-aapl',
            kind: 'STOCKS',
            ticker: 'AAPL',
            name: 'Apple',
            walletName: 'Ações EUA',
            targetPercentage: 100,
            typed: false,
            actualPercentage: 100,
            driftPercentagePoints: 0,
            driftValue: 0,
            value: 500,
          }),
        ],
        segments: [
          { label: 'Tecnologia', value: 500, classPercentage: 100, strategyPercentage: 50 },
        ],
      }),
    ],
    unassignedHoldingCount: 2,
    unassignedValue: 250,
    ...overrides,
  }
}

export function contributionPlan(overrides: Partial<ContributionPlan> = {}): ContributionPlan {
  return {
    strategyId: 'strategy-long',
    displayCurrency: 'BRL',
    amount: 300,
    totalValueBefore: 1000,
    totalValueAfter: 1300,
    classes: [
      {
        id: 'class-brazil',
        name: 'Ações Brasil',
        targetPercentage: 60,
        percentageBefore: 50,
        percentageAfter: 61.54,
        amount: 300,
        holdings: [
          {
            kind: 'STOCKS',
            id: 'holding-vale3',
            walletId: 'wallet-brazil',
            walletName: 'Ações Brasil',
            ticker: 'VALE3',
            name: 'Vale ON',
            amount: 300,
          },
        ],
      },
    ],
    leftOut: [
      {
        kind: 'STOCKS',
        id: 'holding-petr4',
        walletId: 'wallet-brazil',
        walletName: 'Ações Brasil',
        ticker: 'PETR4',
        name: 'Petrobras PN',
        className: 'Ações Brasil',
        reason: 'ABOVE_TARGET_IN_CLASS',
      },
      {
        kind: 'STOCKS',
        id: 'holding-itub4',
        walletId: 'wallet-brazil',
        walletName: 'Ações Brasil',
        ticker: 'ITUB4',
        name: 'Itaú Unibanco PN',
        className: 'Ações Brasil',
        reason: 'FROZEN',
      },
      {
        kind: 'STOCKS',
        id: 'holding-aapl',
        walletId: 'wallet-usa',
        walletName: 'Ações EUA',
        ticker: 'AAPL',
        name: 'Apple',
        className: 'Exterior',
        reason: 'CLASS_ABOVE_TARGET',
      },
    ],
    ...overrides,
  }
}
