import type { DataDomain, DomainDataSource, DomainMember } from '../types/domain'
import { http } from './http'

export const domainApi = {
  list: () => http.get<DataDomain[]>('/domains'),
  create: (body: { code: string; name: string; description: string }) => http.post<DataDomain>('/admin/domains', body),
  update: (code: string, body: { code: string; name: string; description: string }) => http.put<DataDomain>(`/admin/domains/${code}`, body),
  dataSource: (code: string) => http.get<DomainDataSource>(`/admin/domains/${code}/datasource`),
  updateDataSource: (code: string, body: Record<string, unknown>) => http.put<DomainDataSource>(`/admin/domains/${code}/datasource`, body),
  testDataSource: (code: string) => http.post<{ success: boolean; message: string; database: string }>(`/admin/domains/${code}/datasource/test`),
  members: (code: string) => http.get<DomainMember[]>(`/admin/domains/${code}/members`),
  saveMember: (code: string, body: Record<string, unknown>) => http.put(`/admin/domains/${code}/members`, body),
  deleteMember: (code: string, userId: number) => http.delete(`/admin/domains/${code}/members/${userId}`),
}
