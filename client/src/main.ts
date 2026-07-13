import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

import Buefy from 'buefy'

import App from './App.vue'
import { router } from './router'
import { useAuthStore } from '@/stores/auth'

import 'buefy/dist/css/buefy.css'
import '@mdi/font/css/materialdesignicons.min.css'
import '@/assets/styles.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(Buefy, { defaultIconPack: 'mdi' })

setActivePinia(pinia)
const auth = useAuthStore()

auth.restoreSession().finally(() => {
  app.use(router)
  app.mount('#app')
})
