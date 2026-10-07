export default {
  extends: ['stylelint-config-standard'],
  ignoreFiles: ['dist/**', 'coverage/**', 'node_modules/**'],
  rules: {
    'declaration-no-important': true,
    'color-no-hex': true,
    'no-duplicate-selectors': true,
    'no-descending-specificity': true,
    'selector-max-id': 0,
    'selector-class-pattern': '^[a-z][a-z0-9]*(-[a-z0-9]+)*$',
    'value-keyword-case': [
      'lower',
      { camelCaseSvgKeywords: true, ignoreKeywords: ['BlinkMacSystemFont', 'Roboto'] },
    ],
    'selector-pseudo-class-no-unknown': [true, { ignorePseudoClasses: ['deep'] }],
  },
  overrides: [
    {
      files: ['**/*.vue'],
      customSyntax: 'postcss-html',
    },
    {
      files: ['src/assets/theme.css'],
      rules: {
        'color-no-hex': null,
      },
    },
  ],
}
