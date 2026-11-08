import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/employees', name: 'employees', component: () => import('../views/EmployeeListView.vue') },

    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
  ],
});

export default router;
