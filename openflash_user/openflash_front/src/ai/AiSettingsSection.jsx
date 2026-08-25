import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Card, List, ListItem, Preloader } from 'konsta/react'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import ListInput from '../components/konsta/AppListInput.jsx'
import { getErrorMessage } from '../lib/errorMessages.js'
import { withGenericClick } from '../lib/soundEngine'
import {
    activateAiProvider,
    activatePlatformOffering,
    createAiProvider,
    deleteAiProvider,
    discoverAiModels,
    getAiProviders,
    getPlatformModels,
    saveAiProvider,
    savePlatformPreference,
} from './api.js'

const PLATFORM_CLI_KIND = 'CLI'
const PLATFORM_AVAILABLE = 'AVAILABLE'
const PLATFORM_STATUS_KEYS = {
    AVAILABLE: 'settings.aiCodexStatusAvailable',
    NOT_INSTALLED: 'settings.aiCodexStatusNotInstalled',
    NOT_LOGGED_IN: 'settings.aiCodexStatusNotLoggedIn',
    DISABLED: 'settings.aiCodexStatusDisabled',
    ERROR: 'settings.aiCodexStatusError',
}
const PLATFORM_GUIDANCE_KEYS = {
    AVAILABLE: 'settings.aiCodexCliSharedLocalAccountDescription',
    NOT_INSTALLED: 'settings.aiCodexInstallGuidance',
    DISABLED: 'settings.aiCodexDisabledGuidance',
    ERROR: 'settings.aiCodexErrorGuidance',
}
const PLATFORM_EFFORT_KEYS = {
    low: 'settings.aiCodexEffortLow',
    medium: 'settings.aiCodexEffortMedium',
    high: 'settings.aiCodexEffortHigh',
    xhigh: 'settings.aiCodexEffortXhigh',
    max: 'settings.aiCodexEffortMax',
}
const ANTHROPIC_REASONING_EFFORTS = ['low', 'medium', 'high', 'xhigh', 'max']
function isPlatformCli(provider) {
    return provider?.source === 'PLATFORM' && provider?.kind === PLATFORM_CLI_KIND
}

function isPlatformProvider(provider) {
    return provider?.source === 'PLATFORM'
}

function providerRowId(provider) {
    if (provider?.id) return provider.id
    const sourceKey = isPlatformProvider(provider) ? provider?.offeringKey : provider?.providerKey
    return `${provider?.source || 'USER'}:${sourceKey || ''}`
}

function safePlatformStatus(status) {
    return PLATFORM_STATUS_KEYS[status] ? status : 'ERROR'
}

function canLoadPlatformCatalog(provider) {
    return isPlatformCli(provider)
        && provider?.runtimeStatus === 'AVAILABLE'
        && provider?.accessGranted === true
}

function canEditProvider(provider) {
    if (provider?.editable === false) return false
    return !isPlatformProvider(provider) || isPlatformCli(provider)
}

function canActivateProvider(provider) {
    if (!provider || provider.active) return false
    if (!isPlatformProvider(provider)) return true
    return provider.runtimeStatus === PLATFORM_AVAILABLE
        && provider.accessGranted === true
        && Boolean(provider.model)
}

function platformGuidanceKey(status, accessGranted) {
    if (status === 'NOT_LOGGED_IN') return 'settings.aiCodexAdminNotConfigured'
    if (status === PLATFORM_AVAILABLE && accessGranted !== true) {
        return 'settings.aiCodexAccessNotGranted'
    }
    return PLATFORM_GUIDANCE_KEYS[status]
}

/** 全局 AI 供应商设置区块, 展示供应商列表和详细表单. */
export default function AiSettingsSection() {
    const { t } = useTranslation()
    const [providers, setProviders] = useState([])
    const [selectedId, setSelectedId] = useState('')
    const [form, setForm] = useState({
        providerKey: '',
        displayName: '',
        website: '',
        note: '',
        baseUrl: '',
        apiKey: '',
        model: '',
        reasoningEffort: '',
    })
    const [platformForm, setPlatformForm] = useState({ model: '', reasoningEffort: 'low' })
    const [apiKeyConfigured, setApiKeyConfigured] = useState(false)
    const [aiSaveError, setAiSaveError] = useState('')
    const [aiSuccessKey, setAiSuccessKey] = useState('')
    const [aiSaved, setAiSaved] = useState(false)
    const [editing, setEditing] = useState(false)
    const [modelOptions, setModelOptions] = useState([])
    const [modelsLoading, setModelsLoading] = useState(false)
    const [modelsFetchedEmpty, setModelsFetchedEmpty] = useState(false)
    const [platformModels, setPlatformModels] = useState([])
    const [platformRuntimeStatus, setPlatformRuntimeStatus] = useState('')
    const [platformLoading, setPlatformLoading] = useState(false)
    const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
    const platformModelsRequestRef = useRef(0)
    const platformModelsMountedRef = useRef(true)
    const nextPlatformModelsRequestId = useCallback(
        () => ++platformModelsRequestRef.current,
        [],
    )
    const isCurrentPlatformRequest = useCallback(
        requestId => platformModelsMountedRef.current
            && requestId === platformModelsRequestRef.current,
        [],
    )
    const invalidatePendingRefresh = useCallback(() => {
        nextPlatformModelsRequestId()
        if (platformModelsMountedRef.current) setPlatformLoading(false)
    }, [nextPlatformModelsRequestId])

    useEffect(() => {
        platformModelsMountedRef.current = true
        return () => {
            platformModelsMountedRef.current = false
            nextPlatformModelsRequestId()
        }
    }, [nextPlatformModelsRequestId])

    /** 选中某个供应商：平台 CLI 保留 model/effort，API Key 输入栏永远清空。 */
    const selectProvider = useCallback(function selectProvider(provider) {
        setSelectedId(providerRowId(provider))
        if (isPlatformCli(provider)) {
            setPlatformForm({
                model: provider.model || '',
                reasoningEffort: provider.reasoningEffort || '',
            })
            return
        }
        setForm({
            providerKey: provider.providerKey,
            displayName: provider.displayName || '',
            website: provider.website || '',
            note: provider.note || '',
            baseUrl: provider.baseUrl || '',
            apiKey: '',
            model: provider.model || '',
            reasoningEffort: provider.reasoningEffort || '',
        })
        setApiKeyConfigured(provider.apiKeyConfigured === true)
        setModelOptions([])
        setModelsFetchedEmpty(false)
    }, [])

    /** 清空表单，进入新增 API Key 供应商的初始状态。 */
    const resetForm = useCallback(function resetForm() {
        setSelectedId('')
        setForm({
            providerKey: '',
            displayName: '',
            website: '',
            note: '',
            baseUrl: '',
            apiKey: '',
            model: '',
            reasoningEffort: '',
        })
        setApiKeyConfigured(false)
        setModelOptions([])
        setModelsFetchedEmpty(false)
    }, [])

    /** 拉取供应商列表；可保留指定行，否则默认选中 active 或第一行。 */
    const loadProviders = useCallback(async function loadProviders(preferredId = '', requestId) {
        const rows = await getAiProviders()
        const normalizedRows = rows || []
        if (!isCurrentPlatformRequest(requestId)) return normalizedRows
        setProviders(normalizedRows)
        const selected = normalizedRows.find(provider => providerRowId(provider) === preferredId)
            || normalizedRows.find(provider => provider.active)
            || normalizedRows[0]
        if (selected) {
            selectProvider(selected)
        } else {
            resetForm()
        }
        return normalizedRows
    }, [isCurrentPlatformRequest, resetForm, selectProvider])

    /** 按服务端顺序读取指定平台 CLI 目录；已保存值原样保留，新配置优先使用 low。 */
    const loadPlatformModels = useCallback(async function loadPlatformModels(provider, requestId) {
        if (!isCurrentPlatformRequest(requestId)) return { snapshot: null, error: null }
        try {
            const snapshot = await getPlatformModels(provider.offeringKey)
            const models = snapshot?.models || []
            if (!isCurrentPlatformRequest(requestId)) {
                return { snapshot, error: null }
            }
            setPlatformRuntimeStatus(safePlatformStatus(snapshot?.runtimeStatus))
            setPlatformModels(snapshot?.models || [])

            const hasSavedSelection = Boolean(provider?.model || provider?.reasoningEffort)
            if (hasSavedSelection) {
                setPlatformForm({
                    model: provider?.model || '',
                    reasoningEffort: provider?.reasoningEffort || '',
                })
                return { snapshot, error: null }
            }

            const defaultModel = models.find(model => model.defaultModel) || models[0]
            const efforts = defaultModel?.supportedReasoningEfforts || []
            const low = efforts.find(effort => effort.reasoningEffort === 'low')
            setPlatformForm({
                model: defaultModel?.model || '',
                reasoningEffort: low?.reasoningEffort
                    || defaultModel?.defaultReasoningEffort
                    || efforts[0]?.reasoningEffort
                    || '',
            })
            return { snapshot, error: null }
        } catch (error) {
            if (!isCurrentPlatformRequest(requestId)) {
                return { snapshot: null, error }
            }
            setPlatformRuntimeStatus('ERROR')
            setPlatformModels([])
            setAiSaveError(getErrorMessage(error?.code))
            return { snapshot: null, error }
        }
    }, [isCurrentPlatformRequest])

    /** 刷新供应商列表；每次调用先占用 generation，旧请求不能发布列表或选择。 */
    const refreshProviders = useCallback(async function refreshProviders(preferredId = '') {
        const requestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(requestId)) return { rows: [], error: null, requestId }
        setPlatformLoading(false)
        try {
            const rows = await loadProviders(preferredId, requestId)
            return { rows, error: null, requestId }
        } catch (error) {
            return { rows: [], error, requestId }
        }
    }, [isCurrentPlatformRequest, loadProviders, nextPlatformModelsRequestId])

    /** 用同一 generation 依次刷新供应商和当前平台 CLI 目录。 */
    const refreshPlatformData = useCallback(async function refreshPlatformData(
        preferredId = '',
        providerErrorMessage = '',
    ) {
        const requestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(requestId)) {
            return { rows: [], error: null, requestId }
        }
        setPlatformLoading(true)
        try {
            const rows = await loadProviders(preferredId, requestId)
            if (!isCurrentPlatformRequest(requestId)) {
                return { rows, error: null, requestId }
            }
            const platformCli = rows.find(row => providerRowId(row) === preferredId && isPlatformCli(row))
                || rows.find(row => row.active && isPlatformCli(row))
                || rows.find(isPlatformCli)
            if (!platformCli) {
                setPlatformRuntimeStatus('')
                setPlatformModels([])
                return { rows, error: null, requestId }
            }
            if (!canLoadPlatformCatalog(platformCli)) {
                setPlatformRuntimeStatus(safePlatformStatus(platformCli.runtimeStatus))
                setPlatformModels([])
                return { rows, error: null, requestId }
            }
            const catalog = await loadPlatformModels(platformCli, requestId)
            return { rows, error: catalog.error, requestId }
        } catch (error) {
            if (isCurrentPlatformRequest(requestId)) {
                setPlatformRuntimeStatus('ERROR')
                setPlatformModels([])
                setAiSaveError(providerErrorMessage || getErrorMessage(error?.code))
            }
            return { rows: [], error, requestId }
        } finally {
            if (isCurrentPlatformRequest(requestId)) setPlatformLoading(false)
        }
    }, [
        isCurrentPlatformRequest,
        loadPlatformModels,
        loadProviders,
        nextPlatformModelsRequestId,
    ])

    useEffect(() => {
        refreshPlatformData('', t('settings.aiLoadError'))
    }, [refreshPlatformData, t])

    /** 保存按钮可用：必填齐全 + 已配置 key 或新输入 key。 */
    const selectedPersonalModel = modelOptions.find(option => option.id === form.model)
    const discoveredPersonalEfforts = selectedPersonalModel?.supportedReasoningEfforts || []
    const personalEfforts = discoveredPersonalEfforts.length > 0
        ? discoveredPersonalEfforts
        : ANTHROPIC_REASONING_EFFORTS
    const canSaveAi = form.displayName.trim() !== ''
        && form.baseUrl.trim() !== ''
        && form.model.trim() !== ''
        && (apiKeyConfigured || form.apiKey.trim() !== '')
        && (discoveredPersonalEfforts.length === 0 || form.reasoningEffort !== '')

    const selected = providers.find(provider => providerRowId(provider) === selectedId)
    const selectedIsPlatformCli = isPlatformCli(selected)
    const selectedPlatformModel = platformModels.find(model => model.model === platformForm.model)
    const platformEfforts = selectedPlatformModel?.supportedReasoningEfforts || []
    const selectedPlatformEffort = platformEfforts.find(
        effort => effort.reasoningEffort === platformForm.reasoningEffort,
    )
    const selectedPlatformStatus = safePlatformStatus(platformRuntimeStatus || selected?.runtimeStatus)
    const platformRuntimeAvailable = selectedPlatformStatus === PLATFORM_AVAILABLE
    const platformAccessGranted = selected?.accessGranted === true
    const canConfigurePlatform = platformRuntimeAvailable && platformAccessGranted
    const canActivatePlatform = canConfigurePlatform && Boolean(platformForm.model)
    const platformSelectionValid = canConfigurePlatform
        && Boolean(selectedPlatformModel)
        && Boolean(selectedPlatformEffort)
    const platformFormDirty = selectedIsPlatformCli && (
        platformForm.model !== (selected?.model || '')
        || platformForm.reasoningEffort !== (selected?.reasoningEffort || '')
    )
    const selectedPlatformConfigValid = selectedIsPlatformCli && platformProviderSelectionValid(selected)
    const showActivate = canActivateProvider(selected)
    const showDelete = selected && !isPlatformProvider(selected) && selected.deletable !== false

    /** 单字段更新 API 表单。 */
    function updateForm(field, value) {
        setForm(previous => ({ ...previous, [field]: value }))
    }

    /** 选择平台 CLI 模型时按服务端顺序重建 effort，并选择 low 或默认值。 */
    function updatePlatformModel(modelValue) {
        invalidatePendingRefresh()
        const model = platformModels.find(option => option.model === modelValue)
        const efforts = model?.supportedReasoningEfforts || []
        const low = efforts.find(effort => effort.reasoningEffort === 'low')
        setPlatformForm({
            model: modelValue,
            reasoningEffort: low?.reasoningEffort
                || model?.defaultReasoningEffort
                || efforts[0]?.reasoningEffort
                || '',
        })
    }

    /** 保存当前 API Key 表单；新增时后端生成内部 key。 */
    async function handleAiSave() {
        const actionRequestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(actionRequestId)) return
        setPlatformLoading(false)
        setAiSaveError('')
        setAiSuccessKey('')
        try {
            const payload = {
                displayName: form.displayName.trim(),
                website: '',
                note: '',
                baseUrl: form.baseUrl.trim(),
                apiKey: form.apiKey.trim() ? form.apiKey : null,
                model: form.model.trim(),
                reasoningEffort: form.reasoningEffort || null,
            }
            if (form.providerKey.trim()) {
                await saveAiProvider(form.providerKey.trim(), payload)
            } else {
                await createAiProvider(payload)
            }
            if (!isCurrentPlatformRequest(actionRequestId)) return
            const refresh = await refreshProviders(selected ? providerRowId(selected) : '')
            if (!isCurrentPlatformRequest(refresh.requestId)) return
            if (refresh.error) {
                setAiSaveError(getErrorMessage(refresh.error?.code))
                return
            }
            setAiSaved(true)
            setForm(previous => ({ ...previous, apiKey: '' }))
            setApiKeyConfigured(true)
            setEditing(false)
            setTimeout(() => setAiSaved(false), 1500)
        } catch (error) {
            if (isCurrentPlatformRequest(actionRequestId)) {
                setAiSaveError(getErrorMessage(error?.code))
            }
        }
    }

    /** 保存平台 CLI model/effort；只提交这两个语言无关字段。 */
    async function handlePlatformSave() {
        if (!platformSelectionValid) {
            setAiSaveError(t('settings.aiCodexSelectionInvalid'))
            return
        }
        const actionRequestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(actionRequestId)) return
        setPlatformLoading(false)
        setAiSaveError('')
        setAiSuccessKey('')
        try {
            await savePlatformPreference(selected.offeringKey, {
                model: platformForm.model,
                reasoningEffort: platformForm.reasoningEffort,
            })
            if (!isCurrentPlatformRequest(actionRequestId)) return
            const refresh = await refreshPlatformData(selectedId)
            if (!isCurrentPlatformRequest(refresh.requestId) || refresh.error) return
            setAiSuccessKey('settings.aiCodexConfigSaved')
            setEditing(false)
        } catch (error) {
            if (isCurrentPlatformRequest(actionRequestId)) {
                setAiSaveError(getErrorMessage(error?.code))
            }
        }
    }

    /** 按 source 启用对应供应商；平台 CLI 在前端重验已保存选择。 */
    async function handleActivate(providerId, requireCleanPlatformForm = false) {
        const provider = providers.find(row => providerRowId(row) === providerId)
        if (!provider || !canActivateProvider(provider)
            || (requireCleanPlatformForm && platformFormDirty)) {
            setAiSaveError(t('settings.aiCodexSelectionInvalid'))
            return
        }
        const actionRequestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(actionRequestId)) return
        setPlatformLoading(false)
        setAiSaveError('')
        setAiSuccessKey('')
        try {
            if (isPlatformCli(provider)) {
                const catalog = await getPlatformModels(provider.offeringKey)
                if (!isCurrentPlatformRequest(actionRequestId)) return
                if (!platformProviderSelectionValid(
                    provider,
                    catalog?.models || [],
                    catalog?.runtimeStatus,
                )) {
                    setAiSaveError(t('settings.aiCodexSelectionInvalid'))
                    return
                }
            }
            if (isPlatformProvider(provider)) {
                await activatePlatformOffering(provider.offeringKey)
            } else {
                await activateAiProvider(provider.providerKey)
            }
            if (!isCurrentPlatformRequest(actionRequestId)) return
            const refresh = await refreshProviders(providerId)
            if (!isCurrentPlatformRequest(refresh.requestId)) return
            if (refresh.error) {
                setAiSaveError(getErrorMessage(refresh.error?.code))
                return
            }
            if (isPlatformProvider(provider)) setAiSuccessKey('settings.aiCodexActivated')
        } catch (error) {
            if (isCurrentPlatformRequest(actionRequestId)) {
                setAiSaveError(getErrorMessage(error?.code))
            }
        }
    }

    /** 按当前请求地址和 Key 获取 API 模型；失败时保留手填模型。 */
    async function handleDiscoverModels() {
        setAiSaveError('')
        setModelsLoading(true)
        try {
            const rows = await discoverAiModels(form.providerKey, form.baseUrl.trim(), form.apiKey.trim() || null)
            const models = rows || []
            setModelOptions(models)
            setModelsFetchedEmpty(models.length === 0)
            if (models.length > 0) {
                const model = models.find(option => option.id === form.model)
                    || (!form.model.trim() ? models[0] : null)
                const efforts = model?.supportedReasoningEfforts || []
                const savedEffort = efforts.includes(form.reasoningEffort)
                    ? form.reasoningEffort
                    : efforts.find(effort => effort === 'low') || efforts[0] || ''
                setForm(previous => ({
                    ...previous,
                    model: model && !previous.model.trim() ? model.id : previous.model,
                    reasoningEffort: model ? savedEffort : '',
                }))
            }
        } catch (error) {
            setModelOptions([])
            setModelsFetchedEmpty(false)
            setAiSaveError(getErrorMessage(error?.code))
        } finally {
            setModelsLoading(false)
        }
    }

    /** 新建 AI 配置始终展示空的 API Key 表单。 */
    function handleCreateProvider() {
        invalidatePendingRefresh()
        setAiSaveError('')
        setAiSuccessKey('')
        setAiSaved(false)
        resetForm()
        setEditing(true)
    }

    /** 编辑指定配置；平台 CLI 打开时重新读取自己的 runtime catalog。 */
    async function handleEditProvider(provider) {
        invalidatePendingRefresh()
        setAiSaveError('')
        setAiSuccessKey('')
        setAiSaved(false)
        selectProvider(provider)
        setEditing(true)
        if (isPlatformCli(provider)) await refreshPlatformData(providerRowId(provider))
    }

    /** 取消新建或编辑，回到当前 active 行。 */
    function handleCancelEdit() {
        invalidatePendingRefresh()
        setAiSaveError('')
        setAiSuccessKey('')
        setAiSaved(false)
        setEditing(false)
        const active = providers.find(provider => provider.active) || providers[0]
        if (active) {
            selectProvider(active)
        } else {
            resetForm()
        }
    }

    /** 删除当前 API Key 供应商。 */
    async function handleDelete() {
        const actionRequestId = nextPlatformModelsRequestId()
        if (!isCurrentPlatformRequest(actionRequestId)) return
        setPlatformLoading(false)
        setAiSaveError('')
        try {
            await deleteAiProvider(selected.providerKey)
            if (!isCurrentPlatformRequest(actionRequestId)) return
            const refresh = await refreshProviders()
            if (!isCurrentPlatformRequest(refresh.requestId)) return
            if (refresh.error) {
                setAiSaveError(getErrorMessage(refresh.error?.code))
                return
            }
            setDeleteConfirmOpen(false)
            setEditing(false)
        } catch (error) {
            if (isCurrentPlatformRequest(actionRequestId)) {
                setAiSaveError(getErrorMessage(error?.code))
            }
        }
    }

    function providerName(provider) {
        if (isPlatformCli(provider) && provider.model) {
            return `${provider.model} ${t('settings.platformProvidedSuffix')}`
        }
        return provider.displayNameKey
            ? t(provider.displayNameKey, { defaultValue: provider.displayName || provider.providerKey })
            : provider.displayName || provider.providerKey
    }

    function effortName(effort) {
        const effortKey = PLATFORM_EFFORT_KEYS[effort]
        return effortKey ? t(effortKey) : effort
    }

    function platformProviderSelectionValid(
        provider,
        catalogModels = platformModels,
        catalogRuntimeStatus = platformRuntimeStatus,
    ) {
        const model = catalogModels.find(option => option.model === provider.model)
        return safePlatformStatus(catalogRuntimeStatus || provider.runtimeStatus) === PLATFORM_AVAILABLE
            && provider?.accessGranted === true
            && Boolean(model?.supportedReasoningEfforts?.some(
                effort => effort.reasoningEffort === provider.reasoningEffort,
            ))
    }

    function providerSubtitle(provider) {
        if (!isPlatformProvider(provider)) {
            const effort = provider.reasoningEffort
                ? `${t('settings.aiCodexReasoningEffort')}: ${effortName(provider.reasoningEffort)}`
                : ''
            return [provider.model, effort, provider.baseUrl].filter(Boolean).join(' · ')
        }
        const displayModel = provider.model
            ? `${provider.model} ${t('settings.platformProvidedSuffix')}`
            : ''
        const status = safePlatformStatus(provider.runtimeStatus)
        const statusText = status === PLATFORM_AVAILABLE ? '' : t(PLATFORM_STATUS_KEYS[status])
        if (!isPlatformCli(provider)) {
            return [displayModel, statusText].filter(Boolean).join(' · ')
        }
        if (!provider.model || !provider.reasoningEffort) {
            return t('settings.aiCodexSelectionInvalid')
        }
        return [
            `${t('settings.aiCodexReasoningEffort')}: ${effortName(provider.reasoningEffort)}`,
            statusText,
        ].filter(Boolean).join(' · ')
    }

    /** 渲染当前选中供应商的编辑表单. */
    function renderEditor() {
        return selectedIsPlatformCli ? (
            <>
                <List inset strong className="!mx-0 !my-3">
                    {!canConfigurePlatform && (
                        <ListItem
                            title={t(platformGuidanceKey(selectedPlatformStatus, selected?.accessGranted))}
                        />
                    )}
                    <ListInput
                        outline
                        dropdown
                        type="select"
                        label={t('settings.aiCodexModel')}
                        value={platformForm.model}
                        onChange={event => updatePlatformModel(event.target.value)}
                        disabled={!canConfigurePlatform || platformLoading}
                    >
                        {!selectedPlatformModel && platformForm.model && (
                            <option value={platformForm.model}>
                                {platformForm.model} ({t('settings.aiCodexSelectionInvalid')})
                            </option>
                        )}
                        {platformModels.map(model => (
                            <option key={model.id || model.model} value={model.model}>
                                {model.displayName || model.model}
                                {model.defaultModel ? ` (${t('settings.aiCodexDefaultLabel')})` : ''}
                            </option>
                        ))}
                    </ListInput>
                    <ListInput
                        outline
                        dropdown
                        type="select"
                        label={t('settings.aiCodexReasoningEffort')}
                        value={platformForm.reasoningEffort}
                        onChange={event => {
                            invalidatePendingRefresh()
                            setPlatformForm(previous => ({
                                ...previous,
                                reasoningEffort: event.target.value,
                            }))
                        }}
                        disabled={!canConfigurePlatform || !selectedPlatformModel || platformLoading}
                    >
                        {!selectedPlatformEffort && platformForm.reasoningEffort && (
                            <option value={platformForm.reasoningEffort}>
                                {effortName(platformForm.reasoningEffort)} ({t('settings.aiCodexSelectionInvalid')})
                            </option>
                        )}
                        {platformEfforts.map(effort => (
                            <option key={effort.reasoningEffort} value={effort.reasoningEffort}>
                                {effortName(effort.reasoningEffort)}
                            </option>
                        ))}
                    </ListInput>
                    {selectedPlatformEffort && (
                        <ListItem
                            title={effortName(selectedPlatformEffort.reasoningEffort)}
                            text={selectedPlatformEffort.description || effortName(selectedPlatformEffort.reasoningEffort)}
                        />
                    )}
                </List>

                {canConfigurePlatform && !platformSelectionValid && (
                    <p className="mt-3 text-sm text-app-danger">
                        {t('settings.aiCodexSelectionInvalid')}
                    </p>
                )}

                <div className="mt-4 grid gap-2">
                    <Button
                        rounded
                        className="app-primary-fill"
                        onClick={withGenericClick(handlePlatformSave)}
                        disabled={!platformSelectionValid || platformLoading}
                    >
                        {platformLoading && <Preloader className="mr-2" />}
                        {t('settings.saveAiConfig')}
                    </Button>
                    <Button rounded tonal onClick={withGenericClick(handleCancelEdit)}>
                        {t('common.cancel')}
                    </Button>
                    {showActivate && canActivatePlatform && (
                        <Button
                            rounded
                            outline
                            onClick={withGenericClick(() => handleActivate(selectedId, true))}
                            disabled={!selectedPlatformConfigValid || platformFormDirty || platformLoading}
                        >
                            {t('settings.activateAiProvider')}
                        </Button>
                    )}
                </div>
            </>
        ) : (
            <>
                <List inset strong className="!mx-0 !my-3">
                    <ListInput
                        outline
                        label={t('settings.aiProviderName')}
                        value={form.displayName}
                        onChange={event => updateForm('displayName', event.target.value)}
                    />
                    <ListInput
                        outline
                        label={t('settings.aiProviderBaseUrl')}
                        placeholder={t('settings.aiProviderBaseUrlPlaceholder')}
                        inputClassName="placeholder:text-app-label-tertiary"
                        value={form.baseUrl}
                        onChange={event => updateForm('baseUrl', event.target.value)}
                    />
                    <ListInput
                        outline
                        type="password"
                        label={t('settings.aiProviderApiKey')}
                        placeholder={apiKeyConfigured ? t('settings.aiProviderApiKeyConfigured') : ''}
                        value={form.apiKey}
                        onChange={event => updateForm('apiKey', event.target.value)}
                    />
                    {modelOptions.length > 0 ? (
                        <ListInput
                            outline
                            dropdown
                            label={t('settings.aiProviderModel')}
                            type="select"
                            value={form.model}
                            onChange={event => {
                                const model = modelOptions.find(option => option.id === event.target.value)
                                const efforts = model?.supportedReasoningEfforts || []
                                updateForm('model', event.target.value)
                                updateForm('reasoningEffort',
                                    efforts.find(effort => effort === 'low') || efforts[0] || '')
                            }}
                        >
                            {!modelOptions.some(option => option.id === form.model) && form.model.trim() && (
                                <option value={form.model}>{form.model} ({t('settings.aiModelCustomTag')})</option>
                            )}
                            {modelOptions.map(option => (
                                <option key={option.id} value={option.id}>{option.name || option.id}</option>
                            ))}
                        </ListInput>
                    ) : (
                        <ListInput
                            outline
                            label={t('settings.aiProviderModel')}
                            value={form.model}
                            onChange={event => {
                                updateForm('model', event.target.value)
                                updateForm('reasoningEffort', '')
                            }}
                            info={modelsFetchedEmpty ? t('settings.aiModelsEmpty') : t('settings.aiModelManualHint')}
                        />
                    )}
                    <ListInput
                        outline
                        dropdown
                        label={t('settings.aiCodexReasoningEffort')}
                        type="select"
                        value={form.reasoningEffort}
                        onChange={event => updateForm('reasoningEffort', event.target.value)}
                    >
                        <option value="">{t('settings.aiSelectReasoningEffort')}</option>
                        {personalEfforts.map(effort => (
                            <option key={effort} value={effort}>{effortName(effort)}</option>
                        ))}
                    </ListInput>
                </List>

                <div className="flex flex-wrap gap-2">
                    <Button
                        inline
                        rounded
                        tonal
                        onClick={withGenericClick(handleDiscoverModels)}
                        disabled={modelsLoading || !form.baseUrl.trim() || (!apiKeyConfigured && !form.apiKey.trim())}
                    >
                        {modelsLoading && <Preloader className="mr-2" />}
                        {modelsLoading ? t('settings.fetchingAiModels') : t('settings.fetchAiModels')}
                    </Button>
                    {modelOptions.length > 0 && (
                        <Button inline rounded clear onClick={() => {
                            setModelOptions([])
                            setModelsFetchedEmpty(false)
                            updateForm('reasoningEffort', '')
                        }}>
                            {t('settings.aiModelManualSwitch')}
                        </Button>
                    )}
                </div>

                <div className="mt-4 grid gap-2">
                    <Button rounded className="app-primary-fill" onClick={withGenericClick(handleAiSave)} disabled={!canSaveAi}>
                        {aiSaved ? t('settings.aiConfigSaved') : t('settings.saveAiConfig')}
                    </Button>
                    <Button rounded tonal onClick={withGenericClick(handleCancelEdit)}>
                        {t('common.cancel')}
                    </Button>
                    {showActivate && (
                        <Button rounded outline onClick={withGenericClick(() => handleActivate(selectedId))}>
                            {t('settings.activateAiProvider')}
                        </Button>
                    )}
                    {showDelete && (
                        <Button className="k-color-brand-danger" rounded outline onClick={withGenericClick(() => setDeleteConfirmOpen(true))}>
                            {t('settings.deleteAiProvider')}
                        </Button>
                    )}
                </div>
            </>
        )
    }

    return (
        <>
            <Card className="mt-4 mx-4">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                    <p className="text-base font-semibold text-app-label-primary">{t('settings.aiConfig')}</p>
                    <div className="ml-auto flex flex-wrap items-center justify-end gap-2">
                        <Button inline small rounded clear onClick={withGenericClick(handleCreateProvider)}>
                            {t('settings.addAiProvider')}
                        </Button>
                    </div>
                </div>

                {editing && !selected && renderEditor()}

                {providers.length === 0 ? (
                    <p className="py-4 text-center text-sm text-app-label-tertiary">
                        {t('settings.noAiProviders')}
                    </p>
                ) : (
                    <List inset strong className="!mx-0 !my-3">
                        {providers.map(provider => {
                            const providerId = providerRowId(provider)
                            return (
                                <Fragment key={providerId}>
                                    <ListItem
                                        className={selectedId === providerId ? 'bg-app-selected' : ''}
                                        title={providerName(provider)}
                                        after={provider.active ? t('settings.aiProviderActive') : t('settings.aiProviderInactive')}
                                        text={providerSubtitle(provider)}
                                        footer={(
                                            <div className="mt-2 grid gap-2">
                                                <div className="flex flex-wrap gap-2">
                                                    {canEditProvider(provider) && (
                                                        <Button inline small rounded tonal onClick={withGenericClick(() => handleEditProvider(provider))}>
                                                            {t('settings.editAiProvider')}
                                                        </Button>
                                                    )}
                                                    {canActivateProvider(provider) && (
                                                        <Button
                                                            inline
                                                            small
                                                            rounded
                                                            outline
                                                            onClick={withGenericClick(() => handleActivate(providerId))}
                                                        >
                                                            {t('settings.activateAiProvider')}
                                                        </Button>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    />
                                    {editing && selectedId === providerId && (
                                        <li className="px-4 pb-4">
                                            {renderEditor()}
                                        </li>
                                    )}
                                </Fragment>
                            )
                        })}
                    </List>
                )}

                {aiSaveError && <p className="mt-3 text-sm text-app-danger">{aiSaveError}</p>}
                {aiSuccessKey && <p className="mt-3 text-sm text-app-success">{t(aiSuccessKey)}</p>}
            </Card>
            <ConfirmDialog
                open={deleteConfirmOpen}
                message={t('settings.deleteAiProviderConfirm')}
                onConfirm={handleDelete}
                onCancel={() => setDeleteConfirmOpen(false)}
            />
        </>
    )
}
