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
  ElTag,
} from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import PageHeader from '../components/PageHeader.vue';
import { ApiRequestError } from '../api/http';
import {
  createEmployee,
  listEmployees,
  updateEmployee,
  type Employee,
  type EmployeePayload,
  type EmployeeStatus,
  type Gender,
} from '../api/employees';

interface EmployeeForm {
  employeeNo: string;
  name: string;
  gender: Gender;
  phone: string;
  email: string;
  department: string;
  position: string;
  hireDate: string;
  status: EmployeeStatus;
}

type DialogMode = 'create' | 'edit';

const FIELD_NAMES = [
  'employeeNo',
  'name',
  'gender',
  'phone',
  'email',
  'department',
  'position',
  'hireDate',
  'status',
] as const;

const router = useRouter();

const pageSizes = [10, 20, 50];
const genderOptions: Array<{ value: Gender; label: string }> = [
  { value: 'MALE', label: '男性' },
  { value: 'FEMALE', label: '女性' },
  { value: 'UNKNOWN', label: '未知' },
];
const statusOptions: Array<{ value: EmployeeStatus; label: string }> = [
  { value: 'ACTIVE', label: '在职' },
  { value: 'INACTIVE', label: '离职' },
];

const keyword = ref('');
const appliedKeyword = ref('');
const department = ref('');
const appliedDepartment = ref('');
const statusFilter = ref<EmployeeStatus | ''>('');
const appliedStatus = ref<EmployeeStatus | ''>('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const items = ref<Employee[]>([]);
const loading = ref(false);
const loadError = ref<string | null>(null);

const dialogVisible = ref(false);
const dialogMode = ref<DialogMode>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<EmployeeForm>(emptyForm());
const serverErrors = reactive<Record<string, string>>({});

function todayString(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

const rules: FormRules<EmployeeForm> = {
  employeeNo: [
    { required: true, whitespace: true, message: '工号不能为空', trigger: 'blur' },
    { max: 32, message: '工号长度不能超过32个字符', trigger: 'blur' },
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
  hireDate: [
    {
      validator: (_rule, value, callback) => {
        const text = typeof value === 'string' ? value : '';
        if (text !== '' && text > todayString()) {
          callback(new Error('入职日期不能晚于今天'));
          return;
        }
        callback();
      },
      trigger: 'change',
    },
  ],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }],
};

function emptyForm(): EmployeeForm {
  return {
    employeeNo: '',
    name: '',
    gender: 'UNKNOWN',
    phone: '',
    email: '',
    department: '',
    position: '',
    hireDate: '',
    status: 'ACTIVE',
  };
}

function genderLabel(value: Gender | null | undefined): string {
  switch (value) {
    case 'MALE':
      return '男性';
    case 'FEMALE':
      return '女性';
    default:
      return '未知';
  }
}

function statusLabel(value: EmployeeStatus | null | undefined): string {
  return value === 'INACTIVE' ? '离职' : '在职';
}

function statusTagType(value: EmployeeStatus | null | undefined): 'success' | 'info' {
  return value === 'INACTIVE' ? 'info' : 'success';
}

function display(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : '-';
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function toPayload(): EmployeePayload {
  return {
    employeeNo: form.employeeNo.trim(),
    name: form.name.trim(),
    gender: form.gender,
    phone: blankToNull(form.phone),
    email: blankToNull(form.email),
    department: blankToNull(form.department),
    position: blankToNull(form.position),
    hireDate: blankToNull(form.hireDate),
    status: form.status,
  };
}

function clearServerErrors(): void {
  for (const field of FIELD_NAMES) {
    delete serverErrors[field];
  }
}

function isFieldName(value: string): value is (typeof FIELD_NAMES)[number] {
  return (FIELD_NAMES as readonly string[]).includes(value);
}

function clearServerError(field: (typeof FIELD_NAMES)[number]): void {
  delete serverErrors[field];
}

function collectServerErrors(error: unknown): string {
  const message = error instanceof Error ? error.message : '提交失败';
  if (error instanceof ApiRequestError) {
    for (const item of error.fieldErrors) {
      if (isFieldName(item.field)) {
        serverErrors[item.field] = item.message;
      }
    }
  }
  if (message.includes('工号已存在')) {
    serverErrors.employeeNo = message;
  }
  return message;
}

async function load(): Promise<void> {
  loading.value = true;
  loadError.value = null;
  try {
    const result = await listEmployees({
      page: page.value,
      size: pageSize.value,
      keyword: appliedKeyword.value === '' ? undefined : appliedKeyword.value,
      department: appliedDepartment.value === '' ? undefined : appliedDepartment.value,
      status: appliedStatus.value === '' ? undefined : appliedStatus.value,
    });
    items.value = result.items;
    total.value = result.total;
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载员工列表失败';
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
  appliedDepartment.value = department.value.trim();
  appliedStatus.value = statusFilter.value;
  page.value = 1;
  void load();
}

function reset(): void {
  keyword.value = '';
  appliedKeyword.value = '';
  department.value = '';
  appliedDepartment.value = '';
  statusFilter.value = '';
  appliedStatus.value = '';
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
  openEdit(row as Employee);
}

function openEdit(row: Employee): void {
  dialogMode.value = 'edit';
  editingId.value = row.id;
  Object.assign(form, {
    employeeNo: row.employeeNo ?? '',
    name: row.name ?? '',
    gender: row.gender ?? 'UNKNOWN',
    phone: row.phone ?? '',
    email: row.email ?? '',
    department: row.department ?? '',
    position: row.position ?? '',
    hireDate: row.hireDate ?? '',
    status: row.status ?? 'ACTIVE',
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

function isFutureDate(date: Date): boolean {
  return date.getTime() > Date.now();
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
      await createEmployee(payload);
      ElMessage.success('新增成功');
      dialogVisible.value = false;
      await load();
      return;
    }
    if (editingId.value === null) {
      return;
    }
    await updateEmployee(editingId.value, payload);
    ElMessage.success('更新成功');
    dialogVisible.value = false;
    await load();
  } catch (error) {
    const message = collectServerErrors(error);
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
    <PageHeader title="员工管理" subtitle="维护员工基础信息，支持按工号、姓名、部门与状态检索。">
      <template #actions>
        <ElButton @click="router.push({ name: 'home' })">返回首页</ElButton>
      </template>
    </PageHeader>

    <section class="card">
      <div class="toolbar">
        <ElInput
          v-model="keyword"
          class="toolbar-search"
          placeholder="工号／姓名"
          clearable
          @keyup.enter="search"
        />
        <ElInput
          v-model="department"
          class="toolbar-search"
          placeholder="部门"
          clearable
          @keyup.enter="search"
        />
        <ElSelect v-model="statusFilter" class="toolbar-select" placeholder="状态">
          <ElOption label="全部" value="" />
          <ElOption
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
        <ElButton type="primary" :loading="loading" @click="search">查询</ElButton>
        <ElButton :disabled="loading" @click="reset">重置</ElButton>
        <span class="toolbar-spacer" />
        <ElButton type="primary" :disabled="loading" @click="openCreate">新增员工</ElButton>
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
          row-key="id"
          empty-text="暂无数据"
          class="employee-table"
        >
          <ElTableColumn prop="employeeNo" label="工号" min-width="110" />
          <ElTableColumn prop="name" label="姓名" min-width="100" />
          <ElTableColumn label="性别" width="80">
            <template #default="scope">{{ genderLabel(scope.row.gender) }}</template>
          </ElTableColumn>
          <ElTableColumn label="手机号" min-width="130">
            <template #default="scope">{{ display(scope.row.phone) }}</template>
          </ElTableColumn>
          <ElTableColumn label="邮箱" min-width="180">
            <template #default="scope">{{ display(scope.row.email) }}</template>
          </ElTableColumn>
          <ElTableColumn label="部门" min-width="110">
            <template #default="scope">{{ display(scope.row.department) }}</template>
          </ElTableColumn>
          <ElTableColumn label="职位" min-width="110">
            <template #default="scope">{{ display(scope.row.position) }}</template>
          </ElTableColumn>
          <ElTableColumn label="入职日期" min-width="120">
            <template #default="scope">{{ display(scope.row.hireDate) }}</template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="90">
            <template #default="scope">
              <ElTag :type="statusTagType(scope.row.status)" effect="light">
                {{ statusLabel(scope.row.status) }}
              </ElTag>
            </template>
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
      :title="dialogMode === 'create' ? '新增员工' : '编辑员工'"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="handleDialogClosed"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
        <ElFormItem label="工号" prop="employeeNo" :error="serverErrors.employeeNo">
          <ElInput
            v-model="form.employeeNo"
            placeholder="请输入工号"
            @input="clearServerError('employeeNo')"
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
            placeholder="请输入职位"
            @input="clearServerError('position')"
          />
        </ElFormItem>
        <ElFormItem label="入职日期" prop="hireDate" :error="serverErrors.hireDate">
          <ElDatePicker
            v-model="form.hireDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="请选择入职日期"
            :disabled-date="isFutureDate"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status" :error="serverErrors.status">
          <ElSelect
            v-model="form.status"
            placeholder="请选择状态"
            style="width: 100%"
            @change="clearServerError('status')"
          >
            <ElOption
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
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
  width: 180px;
}

.toolbar-select {
  width: 120px;
}

.toolbar-spacer {
  flex: 1 1 auto;
}

.employee-table {
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
  .toolbar-search,
  .toolbar-select {
    width: 100%;
  }
}
</style>
