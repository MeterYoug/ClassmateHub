import defaultSettings from '@/settings'
import useSettingsStore from '@/store/modules/settings'

/**
 * 动态修改标题
 */
export function useDynamicTitle() {
  const settingsStore = useSettingsStore()
  const brand = defaultSettings.enTitle
    ? defaultSettings.title + ' · ' + defaultSettings.enTitle
    : defaultSettings.title
  if (settingsStore.dynamicTitle) {
    document.title = settingsStore.title + ' - ' + brand
  } else {
    document.title = brand
  }
}