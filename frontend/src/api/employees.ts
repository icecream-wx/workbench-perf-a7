import { get, request } from './http';
import type { PageResult } from './types';

export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';
export type EmployeeStatus = 'ACTIVE' | 'INACTIVE';

export interface Employee {
  id: number;
  employeeNo: string;
  name: string;
  gender: Gender;
  phone: string | null;
  email: string | null;
  department: string | null;
  position: string | null;
  hireDate: string | null;
  status: EmployeeStatus;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeePayload {
  employeeNo: string;
  name: string;
  gender: Gender;
  phone: string | null;
  email: string | null;
  department: string | null;
  position: string | null;
  hireDate: string | null;
  status: EmployeeStatus;
}

export interface EmployeeQuery {
  page: number;
  size: number;
  keyword?: string;
  department?: string;
  status?: EmployeeStatus;
}

export function listEmployees(query: EmployeeQuery): Promise<PageResult<Employee>> {
  return get<PageResult<Employee>>('/employees', { params: query });
}

export function getEmployee(id: number): Promise<Employee> {
  return get<Employee>(`/employees/${id}`);
}

export function createEmployee(payload: EmployeePayload): Promise<Employee> {
  return request<Employee>({ method: 'POST', url: '/employees', data: payload });
}

export function updateEmployee(id: number, payload: EmployeePayload): Promise<Employee> {
  return request<Employee>({ method: 'PUT', url: `/employees/${id}`, data: payload });
}
