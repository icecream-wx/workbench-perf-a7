<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import PageHeader from '../components/PageHeader.vue';
import {
  createMember,
  listMembers,
  updateMember,
  type Member,
  type MemberPayload,
  type Gender,
} from '../api/members';

interface MemberForm {
  memberNo: string;
  name: string;
  gender: Gender;
  phone: string;
  email: string;
  department: string;
  position: string;
  joinDate: string;
}

type DialogMode = 'create' | 'edit';

const FIELD_NAMES = [
  'memberNo',
  'name',
  'gender',
  'phone',
  'email',
  'department',
  'position',
  'joinDate',
] as const;

const router = useRouter();

const pageSizes = [10, 20, 50];
const genderOptions: Array<{ value: Gender; label: string }> = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未填' },
];

const keyword = ref('');
const appliedKeyword = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const items = ref<Member[]>([]);
const loading = ref(false);
const loadError = ref<string | null>(null);

const dialogVisible = ref(false);
const dialogMode = ref<DialogMode>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<MemberForm>(emptyForm());
const serverErrors = reactive<Record<string, string>>({});

const rules: FormRules<MemberForm> = {
  memberNo: [
    { required: true, whitespace: true, message: '成员编号不能为空', trigger: 'blur' },
    { max: 32, message: '成员编号长度不能超过32个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, whitespace: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 64, message: '姓名长度不能超过64个字符', trigger: 'blur' },
  ],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 128, message: '邮箱格式不正确', trigger: 'blur' },
  ],
  department: [{ max: 64, message: '部门长度不能超过64个字符', trigger: 'blur' }],
  position: [{ max: 64, message: '职位长度不能超过64个字符', trigger: 'blur' }],
};

function emptyForm(): MemberForm {
  return {
    memberNo: '',
    name: '',
    gender: 'UNKNOWN',
    phone: '',
    email: '',
    department: '',
    position: '',
    joinDate: '',
  };
}

function genderLabel(value: Gender | null | undefined): string {
  switch (value) {
    case 'MALE':
      return '男';
    case 'FEMALE':
      return '女';
    default:
      return '未填';
  }
}

function display(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : '-';
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function toPayload(): MemberPayload {
  return {
    memberNo: form.memberNo.trim(),
    name: form.name.trim(),
    gender: form.gender,
    phone: blankToNull(form.phone),
    email: blankToNull(form.email),
    department: blankToNull(form.department),
    position: blankToNull(form.position),
    joinDate: blankToNull(form.joinDate),
  };
}

function clearServerErrors(): void {
  for (const field of FIELD_NAMES) {
    delete serverErrors[field];
  }
}

function clearServerError(field: (typeof FIELD_NAMES)[number]): void {
  delete serverErrors[field];
}

function applyServerError(message: string): void {
  for (const field of FIELD_NAMES) {
    const prefix = `${field}: `;
    if (message.startsWith(prefix)) {
      serverErrors[field] = message.slice(prefix.length);
      return;
    }
  }
  if (message.includes('成员编号已存在')) {
    serverErrors.memberNo = message;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  loadError.value = null;
  try {
    const result = await listMembers({
      page: page.value,
      size: pageSize.value,
      keyword: appliedKeyword.value === '' ? undefined : appliedKeyword.value,
    });
    items.value = result.items;
    total.value = result.total;
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载成员列表失败';
    loadError.value = message;
    items.value = [];
    total.value = 0;
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}

function search(): void {
  appliedKeyword.value = keyword.value.trim();
  page.value = 1;
  void load();
}

function reset(): void {
  keyword.value = '';
  appliedKeyword.value = '';
  page.value = 1;
  void load();
}

function handleCurrentChange(current: number): void {
  page.value = current;
  void load();
}

function handleSizeChange(size: number): void {
  pageSize.value = size;
  page.value = 1;
  void load();
}

function openCreate(): void {
  dialogMode.value = 'create';
  editingId.value = null;
  Object.assign(form, emptyForm());
  clearServerErrors();
  dialogVisible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function openEditFromRow(row: unknown): void {
  openEdit(row as Member);
}

function openEdit(row: Member): void {
  dialogMode.value = 'edit';
  editingId.value = row.id;
  Object.assign(form, {
    memberNo: row.memberNo ?? '',
    name: row.name ?? '',
    gender: row.gender ?? 'UNKNOWN',
    phone: row.phone ?? '',
    email: row.email ?? '',
    department: row.department ?? '',
    position: row.position ?? '',
    joinDate: row.joinDate ?? '',
  });
  clearServerErrors();
  dialogVisible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function handleDialogClosed(): void {
  formRef.value?.clearValidate();
  clearServerErrors();
  submitting.value = false;
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return;
  }
  const instance = formRef.value;
  if (!instance) {
    return;
  }
  const valid = await instance.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  clearServerErrors();
  try {
    const payload = toPayload();
    if (dialogMode.value === 'create') {
      await createMember(payload);
      ElMessage.success('新增成功');
      dialogVisible.value = false;
      page.value = 1;
      await load();
      return;
    }
    if (editingId.value === null) {
      return;
    }
    await updateMember(editingId.value, payload);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await load();
  } catch (error) {
    const message = error instanceof Error ? error.message : '提交失败';
    applyServerError(message);
    ElMessage.error(message);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page">
    <PageHeader title="成员管理" subtitle="维护成员基础信息，支持按成员编号或姓名检索。">
      <template #actions>
        <ElButton @click="router.push({ name: 'home' })">返回首页</ElButton>
      </template>
    </PageHeader>

    <section class="card">
      <div class="toolbar">
        <ElInput
          v-model="keyword"
          class="toolbar-search"
          placeholder="成员编号／姓名"
          clearable
          @keyup.enter="search"
        />
        <ElButton type="primary" :loading="loading" @click="search">查询</ElButton>
        <ElButton :disabled="loading" @click="reset">重置</ElButton>
        <span class="toolbar-spacer" />
        <ElButton type="primary" :disabled="loading" @click="openCreate">新增成员</ElButton>
      </div>

      <div v-if="loadError" class="load-error">
        <p class="error">{{ loadError }}</p>
        <ElButton @click="load">重试</ElButton>
      </div>
      <template v-else>
        <ElTable
          v-loading="loading"
          :data="items"
          border
          stripe
          empty-text="暂无成员数据"
          class="member-table"
        >
          <ElTableColumn prop="memberNo" label="成员编号" min-width="110" />
          <ElTableColumn prop="name" label="姓名" min-width="100" />
          <ElTableColumn label="性别" width="80">
            <template #default="scope">{{ genderLabel(scope.row.gender) }}</template>
          </ElTableColumn>
          <ElTableColumn label="部门" min-width="110">
            <template #default="scope">{{ display(scope.row.department) }}</template>
          </ElTableColumn>
          <ElTableColumn label="职位" min-width="110">
            <template #default="scope">{{ display(scope.row.position) }}</template>
          </ElTableColumn>
          <ElTableColumn label="手机号" min-width="130">
            <template #default="scope">{{ display(scope.row.phone) }}</template>
          </ElTableColumn>
          <ElTableColumn label="邮箱" min-width="180">
            <template #default="scope">{{ display(scope.row.email) }}</template>
          </ElTableColumn>
          <ElTableColumn label="加入日期" min-width="120">
            <template #default="scope">{{ display(scope.row.joinDate) }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="90" fixed="right">
            <template #default="scope">
              <ElButton link type="primary" @click="openEditFromRow(scope.row)">编辑</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <div class="pager">
          <ElPagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="page"
            :page-size="pageSize"
            :page-sizes="pageSizes"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </section>

    <ElDialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增成员' : '编辑成员'"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="handleDialogClosed"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
        <ElFormItem label="成员编号" prop="memberNo" :error="serverErrors.memberNo">
          <ElInput
            v-model="form.memberNo"
            placeholder="请输入成员编号"
            @input="clearServerError('memberNo')"
          />
        </ElFormItem>
        <ElFormItem label="姓名" prop="name" :error="serverErrors.name">
          <ElInput
            v-model="form.name"
            placeholder="请输入姓名"
            @input="clearServerError('name')"
          />
        </ElFormItem>
        <ElFormItem label="性别" prop="gender" :error="serverErrors.gender">
          <ElSelect
            v-model="form.gender"
            placeholder="请选择性别"
            style="width: 100%"
            @change="clearServerError('gender')"
          >
            <ElOption
              v-for="option in genderOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="部门" prop="department" :error="serverErrors.department">
          <ElInput
            v-model="form.department"
            placeholder="请输入部门"
            @input="clearServerError('department')"
          />
        </ElFormItem>
        <ElFormItem label="职位" prop="position" :error="serverErrors.position">
          <ElInput
            v-model="form.position"
            placeholder="请输加入位"
            @input="clearServerError('position')"
          />
        </ElFormItem>
        <ElFormItem label="手机号" prop="phone" :error="serverErrors.phone">
          <ElInput
            v-model="form.phone"
            placeholder="请输入手机号"
            @input="clearServerError('phone')"
          />
        </ElFormItem>
        <ElFormItem label="邮箱" prop="email" :error="serverErrors.email">
          <ElInput
            v-model="form.email"
            placeholder="请输入邮箱"
            @input="clearServerError('email')"
          />
        </ElFormItem>
        <ElFormItem label="加入日期" prop="joinDate" :error="serverErrors.joinDate">
          <ElDatePicker
            v-model="form.joinDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="请选择加入日期"
            style="width: 100%"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton :disabled="submitting" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submit">保存</ElButton>
      </template>
    </ElDialog>
  </main>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.toolbar-search {
  width: 220px;
}

.toolbar-spacer {
  flex: 1 1 auto;
}

.member-table {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.load-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 32px 8px;
}

.load-error p {
  margin: 0;
}

@media (max-width: 640px) {
  .toolbar-search {
    width: 100%;
  }
}
</style>
