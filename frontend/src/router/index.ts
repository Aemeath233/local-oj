import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ProblemListView from '../views/ProblemListView.vue'
import ProblemDetailView from '../views/ProblemDetailView.vue'
import SubmissionListView from '../views/SubmissionListView.vue'
import LeaderboardView from '../views/LeaderboardView.vue'
import ProfileView from '../views/ProfileView.vue'
import AdminDashboardView from '../views/AdminDashboardView.vue'
import AdminProblemListView from '../views/AdminProblemListView.vue'
import AdminProblemView from '../views/AdminProblemView.vue'
import AdminSubmissionView from '../views/AdminSubmissionView.vue'
import AdminSettingsView from '../views/AdminSettingsView.vue'
import AdminUserListView from '../views/AdminUserListView.vue'
import ContestListView from '../views/ContestListView.vue'
import ContestDetailView from '../views/ContestDetailView.vue'
import ContestProblemDetailView from '../views/ContestProblemDetailView.vue'
import AdminContestListView from '../views/AdminContestListView.vue'
import AdminContestView from '../views/AdminContestView.vue'
import AdminLogView from '../views/AdminLogView.vue'
import AdminDataView from '../views/AdminDataView.vue'
import TrainingListView from '../views/TrainingListView.vue'
import TrainingDetailView from '../views/TrainingDetailView.vue'
import AdminTrainingListView from '../views/AdminTrainingListView.vue'
import AdminTrainingView from '../views/AdminTrainingView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView, meta: { requiresAuth: true } },
    { path: '/login', component: LoginView },
    { path: '/problems', component: ProblemListView },
    { path: '/leaderboard', component: LeaderboardView },
    { path: '/contests', component: ContestListView },
    { path: '/contests/:id', component: ContestDetailView, props: true, meta: { requiresAuth: true } },
    { path: '/contests/:contestId/problems/:id', component: ContestProblemDetailView, props: true, meta: { requiresAuth: true } },
    { path: '/problems/:id', component: ProblemDetailView, props: true, meta: { requiresAuth: true } },
    { path: '/submissions', component: SubmissionListView, meta: { requiresAuth: true } },
    { path: '/profile', component: ProfileView, meta: { requiresAuth: true } },
    { path: '/admin', component: AdminDashboardView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems', component: AdminProblemListView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems/new', component: AdminProblemView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems/:id', component: AdminProblemView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/submissions', component: AdminSubmissionView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/logs', component: AdminLogView, meta: { requiresAuth: true, requiresSuperAdmin: true } },
    { path: '/admin/contests', component: AdminContestListView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/contests/new', component: AdminContestView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/contests/:id', component: AdminContestView, props: true, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/data', component: AdminDataView, meta: { requiresAuth: true, requiresSuperAdmin: true } },
    { path: '/admin/settings', component: AdminSettingsView, meta: { requiresAuth: true, requiresSuperAdmin: true } },
    { path: '/admin/users', component: AdminUserListView, meta: { requiresAuth: true, requiresSuperAdmin: true } },
    { path: '/training', component: TrainingListView, meta: { requiresAuth: true } },
    { path: '/training/:id', component: TrainingDetailView, props: true, meta: { requiresAuth: true } },
    { path: '/admin/training', component: AdminTrainingListView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/training/:id', component: AdminTrainingView, props: true, meta: { requiresAuth: true, requiresAdmin: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.requiresSuperAdmin && !auth.isSuperAdmin) {
    return '/admin'
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return '/problems'
  }
  return true
})

export default router
