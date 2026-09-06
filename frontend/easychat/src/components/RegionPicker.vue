<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import regions from '@/data/regions'

interface CityItem {
  '@CityID'?: string
  '@CityName'?: string
}
interface StationItem {
  '@StationID'?: string
  '@StationName'?: string
  City?: CityItem | CityItem[]
}
interface CountryItem {
  '@CountryID'?: string
  '@CountryName'?: string
  Station?: StationItem[]
}

export interface RegionValue {
  region: string
  regionCode: string
}

const props = defineProps<{
  modelValue?: RegionValue | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RegionValue | null]
}>()

const raw = regions as { Earth: { Country: CountryItem[] } }
// 中国置顶，其余按原顺序
const countries = computed(() => {
  const list = raw.Earth.Country.filter((c) => c['@CountryName'])
  const cn = list.find((c) => c['@CountryID'] === 'China')
  return cn ? [cn, ...list.filter((c) => c !== cn)] : list
})

const countryIdx = ref(-1)
const stationIdx = ref(-1)
const cityIdx = ref(-1)

const country = computed(() => (countryIdx.value >= 0 ? countries.value[countryIdx.value] : undefined))
const stations = computed(() => country.value?.Station ?? [])
const station = computed(() => (stationIdx.value >= 0 ? stations.value[stationIdx.value] : undefined))
const cities = computed(() => {
  const c = station.value?.City
  if (!c) return []
  return Array.isArray(c) ? c : [c]
})
const city = computed(() => (cityIdx.value >= 0 ? cities.value[cityIdx.value] : undefined))

function emitValue() {
  const names: string[] = []
  const ids: string[] = []
  if (country.value?.['@CountryName']) {
    names.push(country.value['@CountryName'])
    if (country.value['@CountryID']) ids.push(country.value['@CountryID'])
  }
  if (station.value?.['@StationName']) {
    names.push(station.value['@StationName'])
    if (station.value['@StationID']) ids.push(station.value['@StationID'])
  }
  if (city.value?.['@CityName']) {
    names.push(city.value['@CityName'])
    if (city.value['@CityID']) ids.push(city.value['@CityID'])
  }
  emit('update:modelValue', names.length ? { region: names.join(' '), regionCode: ids.join('.') } : null)
}

function onCountryChange() {
  stationIdx.value = -1
  cityIdx.value = -1
  emitValue()
}

function onStationChange() {
  cityIdx.value = -1
  emitValue()
}

// 初始回显：按 regionCode（国家.省.市）定位
watch(
  () => props.modelValue,
  (val) => {
    if (!val?.regionCode) return
    const ids = val.regionCode.split('.')
    const c = countries.value.find((x) => x['@CountryID'] === ids[0])
    if (!c) return
    countryIdx.value = countries.value.indexOf(c)
    const st = c.Station ?? []
    const s = st.find((x) => x['@StationID'] === ids[1])
    if (!s) return
    stationIdx.value = st.indexOf(s)
    const cs = s.City
    const list = cs ? (Array.isArray(cs) ? cs : [cs]) : []
    const city = list.find((x) => x['@CityID'] === ids[2])
    if (city) cityIdx.value = list.indexOf(city)
  },
  { immediate: true },
)
</script>

<template>
  <div class="flex flex-1 gap-2">
    <select
      v-model="countryIdx"
      class="min-w-0 flex-1 rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-2 py-2 text-sm text-[var(--ec-text)] outline-none transition-[border-color] duration-200 focus:border-[var(--ec-primary)]"
      @change="onCountryChange"
    >
      <option :value="-1" disabled>国家/地区</option>
      <option v-for="(c, i) in countries" :key="c['@CountryID'] ?? i" :value="i">
        {{ c['@CountryName'] }}
      </option>
    </select>

    <select
      v-if="stations.length"
      v-model="stationIdx"
      class="min-w-0 flex-1 rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-2 py-2 text-sm text-[var(--ec-text)] outline-none transition-[border-color] duration-200 focus:border-[var(--ec-primary)]"
      @change="onStationChange"
    >
      <option :value="-1" disabled>省份/州</option>
      <option v-for="(s, i) in stations" :key="s['@StationID'] ?? i" :value="i">
        {{ s['@StationName'] }}
      </option>
    </select>

    <select
      v-if="cities.length"
      v-model="cityIdx"
      class="min-w-0 flex-1 rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-2 py-2 text-sm text-[var(--ec-text)] outline-none transition-[border-color] duration-200 focus:border-[var(--ec-primary)]"
      @change="emitValue"
    >
      <option :value="-1" disabled>城市</option>
      <option v-for="(ct, i) in cities" :key="ct['@CityID'] ?? i" :value="i">
        {{ ct['@CityName'] }}
      </option>
    </select>
  </div>
</template>

<style scoped></style>
