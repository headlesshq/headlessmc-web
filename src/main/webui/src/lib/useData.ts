import { onMounted, ref, watch, type Ref } from 'vue'
import { store } from './store'

/** Loads data and reloads it whenever a HeadlessMc command finished (it might have changed the data). */
export function useData<T>(loader: () => Promise<T>, initial: T): { data: Ref<T>; error: Ref<string>; loading: Ref<boolean>; reload: () => Promise<void> } {
  const data = ref(initial) as Ref<T>
  const error = ref('')
  const loading = ref(false)

  async function reload() {
    loading.value = true
    try {
      data.value = await loader()
      error.value = ''
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      loading.value = false
    }
  }

  onMounted(reload)
  watch(() => store.revision, reload)
  return { data, error, loading, reload }
}
