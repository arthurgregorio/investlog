import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Buefy from 'buefy'

import App from './App.vue'
import { router } from './router'
import 'buefy/dist/css/buefy.css'
import '@mdi/font/css/materialdesignicons.min.css'
import './assets/styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Buefy, { defaultIconPack: 'mdi' })
app.mount('#app')
