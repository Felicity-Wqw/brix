<template>
  <div class="users">
    <div class="users__bar">
      <div class="users__filters">
        <el-input
          v-model="keyword"
          placeholder="用户名 / 昵称 / 手机号"
          clearable
          :prefix-icon="Search"
          class="users__search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="role" placeholder="角色" clearable class="users__select" @change="reload">
          <el-option label="普通用户" value="USER" />
          <el-option label="审核员" value="REVIEWER" />
          <el-option label="运营" value="OPERATOR" />
        </el-select>
        <el-select v-model="status" placeholder="状态" clearable class="users__select" @change="reload">
          <el-option label="正常" value="NORMAL" />
          <el-option label="已封禁" value="DISABLED" />
        </el-select>
      </div>
      <span class="users__tip">后台账号不受用户管理约束，避免运营把审核员封掉</span>
    </div>

    <div class="users__body" v-loading="loading">
      <el-table :data="rows" size="default" empty-text="没有匹配的用户">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="cell-user">
              <el-avatar :size="34" :src="resolveImage(row.avatar)" class="cell-user__avatar">
                {{ (row.nickname || row.username || '?').slice(0, 1) }}
              </el-avatar>
              <div>
                <p class="cell-user__name">{{ row.nickname }}</p>
                <p class="cell-user__id">@{{ row.username }} · ID {{ row.id }}</p>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="学校 / 校区" min-width="180">
          <template #default="{ row }">
            <span class="cell-school">{{ row.school || '未填写' }}</span>
            <span v-if="row.campus" class="cell-campus">{{ row.campus }}</span>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <span class="role" :class="`role--${row.role.toLowerCase()}`">{{ row.roleDesc }}</span>
          </template>
        </el-table-column>

        <el-table-column label="信用分" width="90" align="right">
          <template #default="{ row }">
            <span class="cell-credit" :class="{ 'cell-credit--low': row.creditScore < 80 }">
              {{ row.creditScore }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="state" :class="row.status === 'DISABLED' ? 'state--bad' : 'state--ok'">
              {{ row.statusDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="130">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTime(row.createdAt, 'YYYY-MM-DD') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.role === 'USER'"
              link
              size="small"
              :class="row.status === 'DISABLED' ? 'op-ok' : 'op-bad'"
              @click="toggle(row)"
            >
              {{ row.status === 'DISABLED' ? '解封' : '封禁' }}
            </el-button>
            <span v-else class="cell-locked">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="users__pager">
        <el-pagination
          v-model:current-page="page.currentPage"
          :page-size="page.pageSize"
          :total="page.total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { fetchAdminUsers, updateUserStatus } from '@/api/admin'
import { formatTime, resolveImage } from '@/utils/format'

const keyword = ref('')
const role = ref('')
const status = ref('')
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminUsers({
      keyword: keyword.value.trim(),
      role: role.value,
      status: status.value,
      currentPage: page.currentPage,
      pageSize: page.pageSize
    })
    rows.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.currentPage = 1
  load()
}

/** 封禁 / 解封：封禁时必须填原因，写入 t_user.disabled_by / disabled_at */
async function toggle(row) {
  const disabling = row.status !== 'DISABLED'
  let reason = ''
  if (disabling) {
    try {
      const input = await ElMessageBox.prompt(
        `封禁后该用户无法登录、无法发布与下单。请填写封禁原因（仅内部留存）：`,
        `封禁「${row.nickname}」`,
        {
          confirmButtonText: '确认封禁',
          cancelButtonText: '取消',
          inputPlaceholder: '例如：多次发布违规商品，屡教不改',
          inputValidator: (value) => (value && value.trim() ? true : '请填写封禁原因')
        }
      )
      reason = input.value.trim()
    } catch (error) {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(`确认解封「${row.nickname}」？解封后该用户可正常登录。`, '解封用户', {
        confirmButtonText: '确认解封',
        cancelButtonText: '取消'
      })
    } catch (error) {
      return
    }
  }

  try {
    await updateUserStatus(row.id, {
      status: disabling ? 'DISABLED' : 'NORMAL',
      reason
    })
    ElMessage.success(disabling ? '已封禁' : '已解封')
    load()
  } catch (error) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.users {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.users__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.users__filters {
  display: flex;
  gap: 10px;
}

.users__search {
  width: 240px;
}

.users__select {
  width: 130px;
}

.users__tip {
  font-size: 12px;
  color: #9aa5b3;
}

.users__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.users__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.cell-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-user__avatar {
  flex: none;
  background: #e7f3ec;
  color: #0e7a50;
  font-size: 13px;
  font-weight: 600;
}

.cell-user__name {
  margin: 0;
  font-size: 13.5px;
  color: #1c2a24;
}

.cell-user__id {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: #9aa5b3;
}

.cell-school {
  font-size: 13px;
  color: #3c4250;
}

.cell-campus {
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: 999px;
  background: #f4f6f8;
  font-size: 11.5px;
  color: #8d99a8;
}

.role {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.role--user {
  background: #f0f3f5;
  color: #5c6b63;
}

.role--reviewer {
  background: #fdece8;
  color: #c9411f;
}

.role--operator {
  background: #e7f3ec;
  color: #0e7a50;
}

.cell-credit {
  font-weight: 600;
  color: #1c2a24;
  font-variant-numeric: tabular-nums;
}

.cell-credit--low {
  color: #c9411f;
}

.state {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.state--ok {
  background: #e7f3ec;
  color: #0e7a50;
}

.state--bad {
  background: #fdece8;
  color: #c9411f;
}

.cell-time {
  font-size: 12.5px;
  color: #8d99a8;
}

.op-bad {
  color: #c9411f;
}

.op-ok {
  color: #0e7a50;
}

.cell-locked {
  color: #c0c8d2;
}
</style>
