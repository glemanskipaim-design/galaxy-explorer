// Exports TIPADOS das fixtures capturadas da API real (scripts/capture-fixtures.mjs).
// A anotacao de tipo em cada export e o "teste de contrato": se o backend mudar um campo e as
// fixtures forem recapturadas, `npm run typecheck` acusa a divergencia com src/api/types.ts.
import type {
  ApiErrorBody,
  CategoryListResponse,
  CategoryObjectsResponse,
  ImportJob,
  ObjectDetail,
  SearchResponse,
} from '../../api/types'

import categoriesJson from './categories.json'
import category400Json from './category-400.json'
import category404Json from './category-404.json'
import categoryGalaxyP1Json from './category-galaxy-p1.json'
import categoryGalaxyP2Json from './category-galaxy-p2.json'
import categoryGalaxyP3BeyondJson from './category-galaxy-p3-beyond.json'
import import400Json from './import-400.json'
import import404Json from './import-404.json'
import import429Json from './import-429.json'
import importBetelgeuseCreatedJson from './import-betelgeuse-created.json'
import importBetelgeuseRunningJson from './import-betelgeuse-running.json'
import importBetelgeuseSucceededJson from './import-betelgeuse-succeeded.json'
import importFailedCreatedJson from './import-failed-created.json'
import importFailedFailedJson from './import-failed-failed.json'
import importFailedRunningJson from './import-failed-running.json'
import importNotFoundCreatedJson from './import-not-found-created.json'
import importNotFoundNotFoundJson from './import-not-found-not-found.json'
import importNotFoundRunningJson from './import-not-found-running.json'
import importSgrACreatedJson from './import-sgr-a-created.json'
import importSgrARunningJson from './import-sgr-a-running.json'
import importSgrASucceededJson from './import-sgr-a-succeeded.json'
import importVegaCreatedJson from './import-vega-created.json'
import importVegaSucceededJson from './import-vega-succeeded.json'
import object404Json from './object-404.json'
import objectBetelgeuseJson from './object-betelgeuse.json'
import objectM100Json from './object-messier-100.json'
import objectM103Json from './object-messier-103.json'
import objectM31Json from './object-messier-31.json'
import objectSgrAJson from './object-sgr-a.json'
import objectVegaJson from './object-vega.json'
import search400Json from './search-400.json'
import searchEmptyJson from './search-empty-betelgeuse.json'
import searchFuzzyJson from './search-fuzzy.json'
import searchM31Json from './search-m31.json'

/** Envelope gravado pelo script de captura. `body` e exatamente o que a API devolveu. */
export interface Fixture<T> {
  request: { method: string; path: string; body: unknown }
  status: number
  headers: {
    'content-type': string | null
    location: string | null
    'retry-after': string | null
  }
  capturedAt: string
  body: T
}

// Busca
export const searchM31: Fixture<SearchResponse> = searchM31Json
export const searchFuzzy: Fixture<SearchResponse> = searchFuzzyJson
export const searchEmpty: Fixture<SearchResponse> = searchEmptyJson
export const search400: Fixture<ApiErrorBody> = search400Json

// Categorias
export const categories: Fixture<CategoryListResponse> = categoriesJson
export const categoryGalaxyP1: Fixture<CategoryObjectsResponse> = categoryGalaxyP1Json
export const categoryGalaxyP2: Fixture<CategoryObjectsResponse> = categoryGalaxyP2Json
export const categoryGalaxyP3Beyond: Fixture<CategoryObjectsResponse> = categoryGalaxyP3BeyondJson
export const category404: Fixture<ApiErrorBody> = category404Json
export const category400: Fixture<ApiErrorBody> = category400Json

// Objetos
export const objectM31: Fixture<ObjectDetail> = objectM31Json
export const objectBetelgeuse: Fixture<ObjectDetail> = objectBetelgeuseJson
export const objectVega: Fixture<ObjectDetail> = objectVegaJson
export const objectSgrA: Fixture<ObjectDetail> = objectSgrAJson
export const objectM100: Fixture<ObjectDetail> = objectM100Json
export const objectM103: Fixture<ObjectDetail> = objectM103Json
export const object404: Fixture<ApiErrorBody> = object404Json

// Importacao
export const importBetelgeuseCreated: Fixture<ImportJob> = importBetelgeuseCreatedJson
export const importBetelgeuseRunning: Fixture<ImportJob> = importBetelgeuseRunningJson
export const importBetelgeuseSucceeded: Fixture<ImportJob> = importBetelgeuseSucceededJson
export const importVegaCreated: Fixture<ImportJob> = importVegaCreatedJson
export const importVegaSucceeded: Fixture<ImportJob> = importVegaSucceededJson
export const importSgrACreated: Fixture<ImportJob> = importSgrACreatedJson
export const importSgrARunning: Fixture<ImportJob> = importSgrARunningJson
export const importSgrASucceeded: Fixture<ImportJob> = importSgrASucceededJson
export const importNotFoundCreated: Fixture<ImportJob> = importNotFoundCreatedJson
export const importNotFoundRunning: Fixture<ImportJob> = importNotFoundRunningJson
export const importNotFoundNotFound: Fixture<ImportJob> = importNotFoundNotFoundJson
export const importFailedCreated: Fixture<ImportJob> = importFailedCreatedJson
export const importFailedRunning: Fixture<ImportJob> = importFailedRunningJson
export const importFailedFailed: Fixture<ImportJob> = importFailedFailedJson
export const import400: Fixture<ApiErrorBody> = import400Json
export const import404: Fixture<ApiErrorBody> = import404Json
export const import429: Fixture<ApiErrorBody> = import429Json

/** Todos os objetos capturados, por slug (para o handler de /objects/:slug). */
export const objectsBySlug: ReadonlyMap<string, Fixture<ObjectDetail>> = new Map(
  [objectM31, objectBetelgeuse, objectVega, objectSgrA, objectM100, objectM103].map((f) => [
    f.body.slug,
    f,
  ]),
)
