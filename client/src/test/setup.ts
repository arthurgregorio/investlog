import { afterEach } from 'vitest'
import { config } from '@vue/test-utils'
import Buefy from 'buefy'

config.global.plugins = [Buefy]

afterEach(() => {
  // b-dropdown's append-to-body teleports outside the wrapper, so each mount leaves
  // nodes behind unless we clear the body between tests.
  document.body.innerHTML = ''
})
