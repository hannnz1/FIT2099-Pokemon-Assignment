const key='ECHO_PREFERENCES';
function clean(value={}) {
  return {volume:Number.isFinite(value?.volume)?Math.max(0,Math.min(1,value.volume)):0.5,textSpeed:[15,30,50].includes(value?.textSpeed)?value.textSpeed:30};
}
export function loadPreferences(storage=globalThis.localStorage) { try { return clean(JSON.parse(storage.getItem(key) || '{}')); } catch { return clean(); } }
export function savePreferences(value,storage=globalThis.localStorage) { storage.setItem(key,JSON.stringify(clean(value))); }
