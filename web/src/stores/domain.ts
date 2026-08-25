import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { domainApi } from '../services/domain-api'
import type { DataDomain } from '../types/domain'

const STORAGE_KEY = 'aibi.active-domain'

export const useDomainStore = defineStore('domain', () => {
  const domains = ref<DataDomain[]>([])
  const selectedCode = ref(localStorage.getItem(STORAGE_KEY) || '')
  const loading = ref(false)
  const current = computed(() => domains.value.find((item) => item.code === selectedCode.value) || null)

  async function load(): Promise<void> {
    loading.value = true
    try {
      domains.value = (await domainApi.list()).data
      if (!domains.value.some((item) => item.code === selectedCode.value)) select(domains.value[0]?.code || '')
    } finally {
      loading.value = false
    }
  }

  function select(code: string): void {
    selectedCode.value = code
    if (code) localStorage.setItem(STORAGE_KEY, code)
    else localStorage.removeItem(STORAGE_KEY)
  }

  return { domains, selectedCode, current, loading, load, select }
})
