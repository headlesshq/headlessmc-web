import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'

export const routes = [
  { path: '/', name: 'dashboard', component: DashboardView, meta: { title: 'Dashboard' } },
  { path: '/accounts', name: 'accounts', component: () => import('./views/AccountsView.vue'), meta: { title: 'Accounts' } },
  { path: '/versions', name: 'versions', component: () => import('./views/VersionsView.vue'), meta: { title: 'Versions' } },
  { path: '/profiles', name: 'profiles', component: () => import('./views/ProfilesView.vue'), meta: { title: 'Profiles' } },
  { path: '/servers', name: 'servers', component: () => import('./views/ServersView.vue'), meta: { title: 'Servers' } },
  { path: '/mods', name: 'mods', component: () => import('./views/ModsView.vue'), meta: { title: 'Mods' } },
  { path: '/java', name: 'java', component: () => import('./views/JavaView.vue'), meta: { title: 'Java' } },
  { path: '/config', name: 'config', component: () => import('./views/ConfigView.vue'), meta: { title: 'Config' } },
  { path: '/processes/:id?', name: 'processes', component: () => import('./views/ProcessesView.vue'), meta: { title: 'Processes' } },
  { path: '/commands/:path(.*)*', name: 'commands', component: () => import('./views/CommandsView.vue'), meta: { title: 'All commands' } },
  { path: '/console', name: 'console', component: () => import('./views/ConsoleView.vue'), meta: { title: 'Console' } },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = `${(to.meta.title as string | undefined) ?? 'HeadlessMc'} · HeadlessMc`
})
