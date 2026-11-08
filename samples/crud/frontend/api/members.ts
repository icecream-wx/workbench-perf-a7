import { get, request } from './http';
import type { PageResult } from './types';

export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export interface Member {
  id: number;
  memberNo: string;
  name: string;
  gender: Gender;
  phone: string | null;
  email: string | null;
  department: string | null;
  position: string | null;
  joinDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MemberPayload {
  memberNo: string;
  name: string;
  gender: Gender;
  phone: string | null;
  email: string | null;
  department: string | null;
  position: string | null;
  joinDate: string | null;
}

export interface MemberQuery {
  page: number;
  size: number;
  keyword?: string;
}

export function listMembers(query: MemberQuery): Promise<PageResult<Member>> {
  return get<PageResult<Member>>('/members', { params: query });
}

export function getMember(id: number): Promise<Member> {
  return get<Member>(`/members/${id}`);
}

export function createMember(payload: MemberPayload): Promise<Member> {
  return request<Member>({ method: 'POST', url: '/members', data: payload });
}

export function updateMember(id: number, payload: MemberPayload): Promise<Member> {
  return request<Member>({ method: 'PUT', url: `/members/${id}`, data: payload });
}
