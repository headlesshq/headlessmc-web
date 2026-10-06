import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { connect } from './lib/store'
import './style.css'

connect()
createApp(App).use(router).mount('#app')
