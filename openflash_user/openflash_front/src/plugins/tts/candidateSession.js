export function createCandidateSession({
  text,
  previewText,
  replaceCachedAudio,
  onStateChange = () => {},
}) {
  let requestVersion = 0
  const candidates = new Map()
  let state = {
    loadingEngine: null,
    selected: null,
    confirming: false,
    error: null,
  }

  function update(patch) {
    state = { ...state, ...patch }
    onStateChange(state)
  }

  return {
    getState() {
      return state
    },

    async preview(engine) {
      const version = ++requestVersion
      update({ loadingEngine: engine, error: null })
      try {
        const retainedCandidate = candidates.get(engine)
        const candidate = await previewText(text, engine, {
          candidateBlob: retainedCandidate?.blob,
        })
        if (version === requestVersion && candidate?.blob) {
          candidates.set(engine, candidate)
          update({ loadingEngine: null, selected: candidate })
        }
        return candidate
      } catch (error) {
        if (version === requestVersion) update({ loadingEngine: null, error })
        throw error
      }
    },

    async confirm() {
      if (!state.selected?.blob || state.confirming) return false
      const selected = state.selected
      update({ confirming: true, error: null })
      try {
        return await replaceCachedAudio(text, selected.blob)
      } catch (error) {
        update({ error })
        throw error
      } finally {
        update({ confirming: false })
      }
    },

    dispose() {
      requestVersion += 1
    },
  }
}
