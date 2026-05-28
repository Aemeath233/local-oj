<template>
  <section class="page-stack" v-loading="loading">
    <div class="page-heading">
      <div>
        <h1>个人主页</h1>
        <p>账号资料和安全设置</p>
      </div>
    </div>

    <section class="profile-layout" v-if="form.username">
      <ProfileAvatarCard :form="form" @profile-updated="applyProfile" />

      <div class="profile-main panel">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="资料" name="profile">
            <ProfileDetailsTab :form="form" @profile-updated="applyProfile" />
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="password">
            <ProfilePasswordForm :email="form.email" />
          </el-tab-pane>

          <el-tab-pane label="偏好设置" name="preferences">
            <ProfilePreferencesCard />
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <!-- 学习激励大盘：难度统计与提交冷热图 -->
    <ProfileStatsDashboard v-if="!loading && form.username" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchProfile } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { User } from '../types'
import ProfileAvatarCard from '../components/profile/ProfileAvatarCard.vue'
import ProfileDetailsTab from '../components/profile/ProfileDetailsTab.vue'
import ProfilePasswordForm from '../components/profile/ProfilePasswordForm.vue'
import ProfilePreferencesCard from '../components/profile/ProfilePreferencesCard.vue'
import ProfileStatsDashboard from '../components/profile/ProfileStatsDashboard.vue'

const auth = useAuthStore()
const loading = ref(false)
const activeTab = ref('profile')

const form = reactive<User>({
  id: 0,
  username: '',
  email: '',
  displayName: '',
  avatarUrl: '',
  studentNo: '',
  major: '',
  role: 'STUDENT'
})

onMounted(() => {
  loadProfile()
})

async function loadProfile() {
  loading.value = true
  try {
    const profile = await fetchProfile()
    applyProfile(profile)
  } catch (error: any) {
    console.error('Failed to load profile', error)
  } finally {
    loading.value = false
  }
}

function applyProfile(profile: User) {
  form.id = profile.id
  form.username = profile.username
  form.email = profile.email || ''
  form.displayName = profile.displayName || profile.username
  form.avatarUrl = profile.avatarUrl || ''
  form.studentNo = profile.studentNo || ''
  form.major = profile.major || ''
  form.role = profile.role
  auth.setUser(profile)
}
</script>

<style scoped>
.profile-layout {
  display: flex;
  gap: 20px;
  margin-top: 18px;
  align-items: flex-start;
}

.profile-main {
  flex: 1;
}

@media (max-width: 768px) {
  .profile-layout {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
