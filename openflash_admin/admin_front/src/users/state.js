const MUTABLE_FIELDS = ['role', 'cliAccess', 'offeringAccess', 'banned', 'deleted']

export function createInitialUsersState() {
  return {
    confirmedByUserId: {},
    latestListRequestId: 0,
    pendingByUserId: {},
    users: [],
  }
}

function pendingKey(userId) {
  return String(userId)
}

function mutationKey(field, itemKey) {
  return itemKey ? `${field}:${itemKey}` : field
}

function readValue(user, field, itemKey) {
  return itemKey ? user[field]?.[itemKey] : user[field]
}

function writeValue(user, field, itemKey, value) {
  if (!itemKey) return { ...user, [field]: value }
  return {
    ...user,
    [field]: { ...user[field], [itemKey]: value },
  }
}

function withoutKey(record, key) {
  const next = { ...record }
  delete next[key]
  return next
}

function confirmationsStartedAfter(confirmations, requestId) {
  const next = {}

  for (const [userId, fields] of Object.entries(confirmations)) {
    const survivingFields = {}
    for (const [key, confirmation] of Object.entries(fields)) {
      if (confirmation.confirmedAt > requestId) {
        survivingFields[key] = confirmation
      }
    }
    if (Object.keys(survivingFields).length > 0) {
      next[userId] = survivingFields
    }
  }

  return next
}

export function createUsersCoordinator(onStateChange = () => {}) {
  let state = createInitialUsersState()
  let nextOperationId = 0

  function updateState(nextState) {
    state = nextState
    onStateChange(state)
  }

  function succeedListRequest(requestId, users) {
    if (state.latestListRequestId !== requestId) return false

    const confirmedByUserId = confirmationsStartedAfter(
      state.confirmedByUserId,
      requestId,
    )
    let pendingByUserId = state.pendingByUserId
    const nextUsers = (Array.isArray(users) ? users : []).map(serverUser => {
      const key = pendingKey(serverUser.id)
      const pending = state.pendingByUserId[key]
      const confirmedFields = confirmedByUserId[key]
      const nextUser = { ...serverUser }

      if (pending && !confirmedFields?.[pending.key]) {
        if (pendingByUserId === state.pendingByUserId) {
          pendingByUserId = { ...pendingByUserId }
        }
        pendingByUserId[key] = {
          ...pending,
          rollbackValue: readValue(serverUser, pending.field, pending.itemKey),
        }
      }

      for (const confirmation of Object.values(confirmedFields ?? {})) {
        Object.assign(nextUser, writeValue(
          nextUser,
          confirmation.field,
          confirmation.itemKey,
          confirmation.value,
        ))
      }
      if (pending) {
        Object.assign(nextUser, writeValue(
          nextUser, pending.field, pending.itemKey, pending.value,
        ))
      }

      return nextUser
    })

    updateState({
      ...state,
      confirmedByUserId,
      pendingByUserId,
      users: nextUsers,
    })
    return true
  }

  function finishMutation(userId, mutationId, succeeded) {
    const key = pendingKey(userId)
    const pending = state.pendingByUserId[key]
    if (!pending || pending.id !== mutationId) return false

    const users = state.users.map(user => user.id === userId
      ? writeValue(
          user,
          pending.field,
          pending.itemKey,
          succeeded ? pending.value : pending.rollbackValue,
        )
      : user)
    const pendingByUserId = withoutKey(state.pendingByUserId, key)
    let confirmedByUserId = state.confirmedByUserId

    if (succeeded) {
      confirmedByUserId = {
        ...confirmedByUserId,
        [key]: {
          ...confirmedByUserId[key],
          [pending.key]: {
            confirmedAt: ++nextOperationId,
            field: pending.field,
            itemKey: pending.itemKey,
            value: pending.value,
          },
        },
      }
    }

    updateState({ ...state, confirmedByUserId, pendingByUserId, users })
    return true
  }

  return {
    getState() {
      return state
    },

    startListRequest() {
      const requestId = ++nextOperationId
      let settled = false
      updateState({ ...state, latestListRequestId: requestId })

      return {
        fail() {
          if (settled) return false
          settled = true
          return state.latestListRequestId === requestId
        },
        succeed(users) {
          if (settled) return false
          settled = true
          return succeedListRequest(requestId, users)
        },
      }
    },

    startMutation(userId, field, value, itemKey = null) {
      if (!MUTABLE_FIELDS.includes(field)) return null
      if (field === 'cliAccess' && !itemKey) return null

      const key = pendingKey(userId)
      const target = state.users.find(user => user.id === userId)
      if (!target || state.pendingByUserId[key]
          || readValue(target, field, itemKey) === value) return null

      const mutationId = ++nextOperationId
      const pending = {
        field,
        id: mutationId,
        itemKey,
        key: mutationKey(field, itemKey),
        rollbackValue: readValue(target, field, itemKey),
        value,
      }
      updateState({
        ...state,
        pendingByUserId: { ...state.pendingByUserId, [key]: pending },
        users: state.users.map(user => user.id === userId
          ? writeValue(user, field, itemKey, value)
          : user),
      })

      return {
        async run(request) {
          try {
            await request()
            finishMutation(userId, mutationId, true)
            return { status: 'succeeded' }
          } catch (error) {
            finishMutation(userId, mutationId, false)
            return { error, status: 'failed' }
          }
        },
      }
    },
  }
}
