export interface DataDomain {
  code: string
  name: string
  description?: string
  status: string
  created_by?: string
  can_query?: boolean | number
  can_report?: boolean | number
  can_train?: boolean | number
}

export interface DomainDataSource {
  domainCode: string
  jdbcUrl: string
  username: string
  driverClass: string
  validationQuery: string
  passwordConfigured: boolean
  platformManaged: boolean
}

export interface DomainMember {
  id: number
  username: string
  display_name: string
  can_query: boolean | number
  can_report: boolean | number
  can_train: boolean | number
}
