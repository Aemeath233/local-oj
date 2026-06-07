<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>数据管理</h1>
        <p>用户批量导入、提交记录清理、测试数据存储空间分析以及数据库备份运维中心。</p>
      </div>
    </div>

    <section class="panel data-panel">
      <el-tabs v-model="activeTab">
        <!-- TAB 1: USER BULK IMPORT -->
        <el-tab-pane label="用户批量导入" name="users">
          <AdminUsersImport />
        </el-tab-pane>

        <!-- TAB 2: SUBMISSION CLEANUP -->
        <el-tab-pane label="提交记录清理" name="cleanup">
          <AdminSubmissionsCleanup />
        </el-tab-pane>

        <!-- TAB 3: TEST DATA STORAGE STATS -->
        <el-tab-pane label="测试数据空间统计" name="storage">
          <AdminStorageStats />
        </el-tab-pane>

        <!-- TAB 4: DATABASE BACKUP INFO -->
        <el-tab-pane label="数据库备份提示" name="backup">
          <AdminDatabaseBackup />
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AdminNav from '../components/AdminNav.vue'
import AdminUsersImport from '../components/admin/AdminUsersImport.vue'
import AdminSubmissionsCleanup from '../components/admin/AdminSubmissionsCleanup.vue'
import AdminStorageStats from '../components/admin/AdminStorageStats.vue'
import AdminDatabaseBackup from '../components/admin/AdminDatabaseBackup.vue'

const activeTab = ref('users')
</script>

<style scoped>
.data-panel {
  margin-top: 1rem;
  padding: 1.5rem;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
}
</style>
