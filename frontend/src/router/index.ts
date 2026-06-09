import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'

const ProblemListView = () => import('../views/ProblemListView.vue')
const ProblemDetailView = () => import('../views/ProblemDetailView.vue')
const SubmissionListView = () => import('../views/SubmissionListView.vue')
const LeaderboardView = () => import('../views/LeaderboardView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const UserPublicProfileView = () => import('../views/UserPublicProfileView.vue')
const AdminDashboardView = () => import('../views/AdminDashboardView.vue')
const AdminProblemListView = () => import('../views/AdminProblemListView.vue')
const AdminProblemView = () => import('../views/AdminProblemView.vue')
const AdminSubmissionView = () => import('../views/AdminSubmissionView.vue')
const AdminSettingsView = () => import('../views/AdminSettingsView.vue')
const AdminUserListView = () => import('../views/AdminUserListView.vue')
const ContestListView = () => import('../views/ContestListView.vue')
const ContestDetailView = () => import('../views/ContestDetailView.vue')
const ContestProblemDetailView = () => import('../views/ContestProblemDetailView.vue')
const AdminContestListView = () => import('../views/AdminContestListView.vue')
const AdminContestView = () => import('../views/AdminContestView.vue')
const AdminContestPlagiarismView = () => import('../views/AdminContestPlagiarismView.vue')
const AdminLogView = () => import('../views/AdminLogView.vue')
const AdminDataView = () => import('../views/AdminDataView.vue')
const TrainingListView = () => import('../views/TrainingListView.vue')
const TrainingDetailView = () => import('../views/TrainingDetailView.vue')
const AdminTrainingListView = () => import('../views/AdminTrainingListView.vue')
const AdminTrainingView = () => import('../views/AdminTrainingView.vue')

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
    { path: '/user/:id', component: UserPublicProfileView, meta: { requiresAuth: true } },
    { path: '/admin', component: AdminDashboardView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems', component: AdminProblemListView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems/new', component: AdminProblemView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/problems/:id', component: AdminProblemView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/submissions', component: AdminSubmissionView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/logs', component: AdminLogView, meta: { requiresAuth: true, requiresSuperAdmin: true } },
    { path: '/admin/contests', component: AdminContestListView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/contests/new', component: AdminContestView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/contests/:id', component: AdminContestView, props: true, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/contests/:id/plagiarism', component: AdminContestPlagiarismView, props: true, meta: { requiresAuth: true, requiresAdmin: true } },
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
