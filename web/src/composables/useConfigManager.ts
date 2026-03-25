import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { ConfigType, Config, ConfigField, ModelOption, LLMModel, LLMFactory, SttFactory, SttModel, TtsFactory, TtsModel } from '@/types/config'
import { queryConfigs, addConfig, updateConfig, fetchModels } from '@/services/config'
import { configTypeMap } from '@/config/providerConfig'
import llmFactoriesData from '@/config/llm_factories.json'
import sttFactoriesData from '@/config/stt_factories.json'
import ttsFactoriesData from '@/config/tts_factories.json'
import { useTable } from './useTable'
import { useLoadingStore } from '@/store/loading'

export function useConfigManager(configType: ConfigType) {
  const { t } = useI18n()
  const loadingStore = useLoadingStore()
  
  // 使用统一的表格管理
  const {
    loading,
    data: configItems,
    pagination,
    loadData,
  } = useTable<Config>()

  // 状态
  const currentType = ref('')
  const editingConfigId = ref<number>()
  const activeTabKey = ref('1')
  const modelOptions = ref<ModelOption[]>([])
  
  // LLM 工厂数据
  interface LLMFactoryModelInfo {
    chat?: LLMModel[]
    vision?: LLMModel[]
    intent?: LLMModel[]
    embedding?: LLMModel[]
  }
  const llmFactoryData = ref<Record<string, LLMFactoryModelInfo>>({})
  // STT/TTS 工厂数据：按 provider 分组存储模型列表
  const sttFactoryData = ref<Record<string, SttModel[]>>({})
  const ttsFactoryData = ref<Record<string, TtsModel[]>>({})
  const availableProviders = ref<Array<{ value: string; label: string }>>([])

  // 查询表单
  const queryForm = ref({
    provider: '',
    configName: '',
    modelType: '',
  })

  // 配置类型信息
  const configTypeInfo = computed(() => {
    return configTypeMap[configType] || { label: '' }
  })

  // 类型选项
  const typeOptions = computed(() => {
    if (configType === 'llm') {
      return availableProviders.value
    }
    return configTypeInfo.value.typeOptions || []
  })

  // 当前类型字段
  const currentTypeFields = computed((): ConfigField[] => {
    const typeFieldsMap = configTypeInfo.value.typeFields || {}
    
    if (configType === 'llm' && currentType.value && !typeFieldsMap[currentType.value]) {
      return typeFieldsMap['default'] || []
    }
    
    return typeFieldsMap[currentType.value] || []
  })

  /**
   * 初始化 LLM 工厂数据
   */
  function initLlmFactoriesData() {
    if (!llmFactoriesData || !llmFactoriesData.factory_llm_infos) {
      console.warn('llm_factories.json 数据格式不正确')
      return
    }

    const factoryData: Record<string, LLMFactoryModelInfo> = {}
    const providers: Array<{ value: string; label: string }> = []

    llmFactoriesData.factory_llm_infos.forEach((factory: LLMFactory) => {
      const providerName = factory.name
      providers.push({
        value: providerName,
        label: providerName,
      })

      // 按模型类型分组存储模型
      const modelsByType: LLMFactoryModelInfo = {
        chat: [],
        embedding: [],
        vision: [],
        intent: []
      }

      if (factory.llm && Array.isArray(factory.llm)) {
        factory.llm.forEach((llm: LLMModel) => {
          let mappedModelType = llm.model_type

          // 映射模型类型
          if (mappedModelType === 'speech2text' || mappedModelType === 'image2text') {
            mappedModelType = 'vision'
          }

          // 只保留需要的模型类型
          if (['chat', 'embedding', 'vision'].includes(mappedModelType as keyof LLMFactoryModelInfo)) {
            (modelsByType[mappedModelType as keyof LLMFactoryModelInfo] as LLMModel[]).push({
              llm_name: llm.llm_name,
              model_type: mappedModelType,
              max_tokens: llm.max_tokens,
              is_tools: llm.is_tools || false,
              tags: llm.tags || '',
            })
          }
        })
      }

      factoryData[providerName] = modelsByType
    })

    llmFactoryData.value = factoryData

    // 按照 providerConfig 中 typeFields 的顺序排序
    const typeFieldsKeys = Object.keys(configTypeMap.llm?.typeFields || {})
    const sortedProviders = providers.sort((a, b) => {
      const indexA = typeFieldsKeys.indexOf(a.value)
      const indexB = typeFieldsKeys.indexOf(b.value)

      // 如果都在 typeFields 中，按照 typeFields 的顺序
      if (indexA !== -1 && indexB !== -1) {
        return indexA - indexB
      }
      // 如果只有 a 在 typeFields 中，a 排前面
      if (indexA !== -1) {
        return -1
      }
      // 如果只有 b 在 typeFields 中，b 排前面
      if (indexB !== -1) {
        return 1
      }
      // 如果都不在 typeFields 中，保持原顺序
      return 0
    })

    availableProviders.value = sortedProviders
  }

  /**
   * 初始化 STT 工厂数据
   */
  function initSttFactoriesData() {
    if (!sttFactoriesData || !sttFactoriesData.factory_stt_infos) {
      console.warn('stt_factories.json 数据格式不正确')
      return
    }

    const factoryData: Record<string, SttModel[]> = {}
    sttFactoriesData.factory_stt_infos.forEach((factory: SttFactory) => {
      factoryData[factory.name] = factory.models || []
    })
    sttFactoryData.value = factoryData
  }

  /**
   * 初始化 TTS 工厂数据
   */
  function initTtsFactoriesData() {
    if (!ttsFactoriesData || !ttsFactoriesData.factory_tts_infos) {
      console.warn('tts_factories.json 数据格式不正确')
      return
    }

    const factoryData: Record<string, TtsModel[]> = {}
    ttsFactoriesData.factory_tts_infos.forEach((factory: TtsFactory) => {
      factoryData[factory.name] = factory.models || []
    })
    ttsFactoryData.value = factoryData
  }

  /**
   * 根据 provider 和 modelType 获取模型列表
   */
  function getModelsByProviderAndType(provider: string, modelType: string): LLMModel[] {
    if (!llmFactoryData.value[provider]) {
      return []
    }
    const providerData = llmFactoryData.value[provider]
    return (providerData[modelType as keyof LLMFactoryModelInfo] || []) as LLMModel[]
  }

  /**
   * 获取 STT/TTS 提供商的模型列表
   */
  function getModelsByProvider(provider: string): Array<{ model_name: string; desc: string }> {
    if (configType === 'stt') {
      return sttFactoryData.value[provider] || []
    }
    if (configType === 'tts') {
      return ttsFactoryData.value[provider] || []
    }
    return []
  }

  /**
   * 更新模型选项列表
   */
  function updateModelOptions(provider: string, modelType?: string) {
    if (configType === 'llm') {
      const models = getModelsByProviderAndType(provider, modelType || 'chat')
      modelOptions.value = models.map((model: LLMModel) => ({
        value: model.llm_name,
        label: model.llm_name,
      }))
    } else if (configType === 'stt' || configType === 'tts') {
      const models = getModelsByProvider(provider)
      modelOptions.value = models.map((model) => ({
        value: model.model_name,
        label: model.desc ? `${model.model_name}（${model.desc}）` : model.model_name,
      }))
    }
  }

  /**
   * 动态获取模型列表（从后端 API）
   */
  async function fetchDynamicModels(provider: string, apiKey: string, type: ConfigType) {
    try {
      const res = await fetchModels({ configType: type, provider, apiKey })
      if (res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
        // 动态结果覆盖静态列表
        modelOptions.value = res.data.map((m: any) => ({
          value: m.model_name,
          label: m.desc ? `${m.model_name}（${m.desc}）` : m.model_name,
        }))
      }
      // 如果返回空列表或失败，保持静态列表不变
    } catch (error) {
      // 动态获取失败时静默回退到静态列表
      console.warn('动态获取模型列表失败，使用静态列表', error)
    }
  }

  /**
   * 获取配置列表
   */
  async function fetchData() {
    await loadData(async ({ start, limit }) => {
      return queryConfigs({
        start,
        limit,
        configType,
        ...queryForm.value,
      })
    })
  }

  /**
   * 删除配置（快速操作，只用 table loading）
   */
  async function deleteConfig(configId: string) {
    loading.value = true
    try {
      const res = await updateConfig({
        configId: parseInt(configId),
        configType,
        state: '0',
      })

      if (res.code === 200) {
        message.success(t('common.delete'))
        await fetchData()
      } else {
        message.error(res.message)
      }
    } catch (error) {
      console.error('删除配置失败:', error)
      message.error(t('common.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 设置为默认配置（快速操作，只用 table loading）
   */
  async function setAsDefault(record: Config) {
    if (configType === 'tts') return

    loading.value = true
    try {
      const res = await updateConfig({
        configId: record.configId,
        configType,
        modelType: configType === 'llm' ? record.modelType : undefined,
        isDefault: '1',
      })

      if (res.code === 200) {
        message.success(t('common.setDefaultSuccess', { name: record.configName }))
        await fetchData()
      } else {
        message.error(res.message || t('common.setDefaultFailed'))
      }
    } catch (error) {
      console.error('设置默认配置失败:', error)
      message.error(t('common.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  // 初始化
  if (configType === 'llm') {
    initLlmFactoriesData()
  } else if (configType === 'stt') {
    initSttFactoriesData()
  } else if (configType === 'tts') {
    initTtsFactoriesData()
  }

  return {
    // 状态
    loading,
    configItems,
    currentType,
    editingConfigId,
    activeTabKey,
    modelOptions,
    pagination,
    queryForm,

    // 计算属性
    configTypeInfo,
    typeOptions,
    currentTypeFields,

    // 方法
    fetchData,
    deleteConfig,
    setAsDefault,
    updateModelOptions,
    getModelsByProviderAndType,
    fetchDynamicModels,
  }
}

