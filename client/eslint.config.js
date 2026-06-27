import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/coverage/**', '**/node_modules/**', '**/components.d.ts'],
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  {
    name: 'app/rules-overrides',
    rules: {
      // Single-word names are intentional for small UI primitives (Avatar, Card, ...).
      'vue/multi-word-component-names': 'off',
    },
  },
  {
    name: 'app/forms-reactive-prop',
    files: ['src/components/forms/**/*.vue'],
    rules: {
      // Form components receive a `reactive()` form object owned by a composable and
      // mutate it directly by design (same object, not a one-way value prop).
      'vue/no-mutating-props': 'off',
    },
  },
  skipFormatting,
)
