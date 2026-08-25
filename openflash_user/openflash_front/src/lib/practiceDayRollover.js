export function hasPracticeDayChanged(sessionDate, today) {
  return Boolean(sessionDate && today && sessionDate !== today)
}

export function claimPracticeDayRollover(lockRef, sessionDate, today) {
  if (lockRef.current || !hasPracticeDayChanged(sessionDate, today)) return false
  lockRef.current = true
  return true
}

export function millisecondsUntilNextLocalDay(now = new Date()) {
  const nextDay = new Date(now)
  nextDay.setHours(24, 0, 0, 0)
  return Math.max(nextDay.getTime() - now.getTime(), 0)
}
