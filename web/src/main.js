import { createApp } from 'vue'
import App from './App.vue'
import { vReveal } from './directives/reveal'
import './styles/main.css'

createApp(App).directive('reveal', vReveal).mount('#app')
