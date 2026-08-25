import { getErrorMessage } from './errorMessages.js'

export function getCardCreateFailureMessage(error) {
  return getErrorMessage(error?.code)
}
